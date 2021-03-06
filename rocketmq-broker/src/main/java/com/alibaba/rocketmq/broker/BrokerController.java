/**
 * Copyright (C) 2010-2013 Alibaba Group Holding Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.rocketmq.broker;

import com.alibaba.rocketmq.broker.client.*;
import com.alibaba.rocketmq.broker.client.net.Broker2Client;
import com.alibaba.rocketmq.broker.client.rebalance.RebalanceLockManager;
import com.alibaba.rocketmq.broker.filtersrv.FilterServerManager;
import com.alibaba.rocketmq.broker.longpolling.PullRequestHoldService;
import com.alibaba.rocketmq.broker.mqtrace.ConsumeMessageHook;
import com.alibaba.rocketmq.broker.mqtrace.SendMessageHook;
import com.alibaba.rocketmq.broker.offset.ConsumerOffsetManager;
import com.alibaba.rocketmq.broker.out.BrokerOuterAPI;
import com.alibaba.rocketmq.broker.processor.*;
import com.alibaba.rocketmq.broker.slave.SlaveSynchronize;
import com.alibaba.rocketmq.broker.stats.BrokerStats;
import com.alibaba.rocketmq.broker.stats.BrokerStatsManager;
import com.alibaba.rocketmq.broker.subscription.SubscriptionGroupManager;
import com.alibaba.rocketmq.broker.topic.TopicConfigManager;
import com.alibaba.rocketmq.common.*;
import com.alibaba.rocketmq.common.constant.LoggerName;
import com.alibaba.rocketmq.common.constant.PermName;
import com.alibaba.rocketmq.common.namesrv.RegisterBrokerResult;
import com.alibaba.rocketmq.common.protocol.RequestCode;
import com.alibaba.rocketmq.common.protocol.body.TopicConfigSerializeWrapper;
import com.alibaba.rocketmq.remoting.RPCHook;
import com.alibaba.rocketmq.remoting.RemotingServer;
import com.alibaba.rocketmq.remoting.netty.NettyClientConfig;
import com.alibaba.rocketmq.remoting.netty.NettyRemotingServer;
import com.alibaba.rocketmq.remoting.netty.NettyRequestProcessor;
import com.alibaba.rocketmq.remoting.netty.NettyServerConfig;
import com.alibaba.rocketmq.store.DefaultMessageStore;
import com.alibaba.rocketmq.store.MessageStore;
import com.alibaba.rocketmq.store.config.BrokerRole;
import com.alibaba.rocketmq.store.config.MessageStoreConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.*;


/**
 * Broker?????????????????????
 * 
 * @author shijia.wxr<vintage.wang@gmail.com>
 * @since 2013-7-26
 */
public class BrokerController {
    private static final Logger log = LoggerFactory.getLogger(LoggerName.BrokerLoggerName);
    // ???????????????
    private final BrokerConfig brokerConfig;
    // ???????????????
    private final NettyServerConfig nettyServerConfig;
    private final NettyClientConfig nettyClientConfig;
    // ???????????????
    private final MessageStoreConfig messageStoreConfig;
    // ?????????????????????
    private final DataVersion configDataVersion = new DataVersion();
    // ??????????????????
    private final ConsumerOffsetManager consumerOffsetManager;
    // Consumer???????????????????????????
    private final ConsumerManager consumerManager;
    // Producer????????????
    private final ProducerManager producerManager;
    // ???????????????????????????
    private final ClientHousekeepingService clientHousekeepingService;
    private final PullMessageProcessor pullMessageProcessor;
    private final PullRequestHoldService pullRequestHoldService;
    // Broker????????????Client
    private final Broker2Client broker2Client;
    // ?????????????????????
    private final SubscriptionGroupManager subscriptionGroupManager;
    // ?????????????????????????????????????????????????????????
    private final ConsumerIdsChangeListener consumerIdsChangeListener;
    // ????????????????????????
    private final RebalanceLockManager rebalanceLockManager = new RebalanceLockManager();
    // Broker?????????????????????
    private final BrokerOuterAPI brokerOuterAPI;
    private final ScheduledExecutorService scheduledExecutorService = Executors
        .newSingleThreadScheduledExecutor(new ThreadFactoryImpl("BrokerControllerScheduledThread"));
    // Slave?????????Master????????????
    private final SlaveSynchronize slaveSynchronize;
    // ???????????????
    private MessageStore messageStore;
    // ???????????????
    private RemotingServer remotingServer;
    // Topic??????
    private TopicConfigManager topicConfigManager;
    // ???????????????????????????
    private ExecutorService sendMessageExecutor;
    // ???????????????????????????
    private ExecutorService pullMessageExecutor;
    // ????????????Broker?????????
    private ExecutorService adminBrokerExecutor;
    // ????????????????????????HA Master??????
    private boolean updateMasterHAServerAddrPeriodically = false;

    private BrokerStats brokerStats;
    // ???????????????????????????
    private final BlockingQueue<Runnable> sendThreadPoolQueue;

    // ???????????????????????????
    private final BlockingQueue<Runnable> pullThreadPoolQueue;

    // FilterServer??????
    private final FilterServerManager filterServerManager;

    private final BrokerStatsManager brokerStatsManager;


    public BrokerController(//
            final BrokerConfig brokerConfig, //
            final NettyServerConfig nettyServerConfig, //
            final NettyClientConfig nettyClientConfig, //
            final MessageStoreConfig messageStoreConfig //
    ) {
        this.brokerConfig = brokerConfig;
        this.nettyServerConfig = nettyServerConfig;
        this.nettyClientConfig = nettyClientConfig;
        this.messageStoreConfig = messageStoreConfig;
        this.consumerOffsetManager = new ConsumerOffsetManager(this);
        this.topicConfigManager = new TopicConfigManager(this);
        this.pullMessageProcessor = new PullMessageProcessor(this);
        this.pullRequestHoldService = new PullRequestHoldService(this);
        this.consumerIdsChangeListener = new DefaultConsumerIdsChangeListener(this);
        this.consumerManager = new ConsumerManager(this.consumerIdsChangeListener);
        this.producerManager = new ProducerManager();
        this.clientHousekeepingService = new ClientHousekeepingService(this);
        this.broker2Client = new Broker2Client(this);
        this.subscriptionGroupManager = new SubscriptionGroupManager(this);
        this.brokerOuterAPI = new BrokerOuterAPI(nettyClientConfig);
        this.filterServerManager = new FilterServerManager(this);

        if (this.brokerConfig.getNamesrvAddr() != null) {
            this.brokerOuterAPI.updateNameServerAddressList(this.brokerConfig.getNamesrvAddr());
            log.info("user specfied name server address: {}", this.brokerConfig.getNamesrvAddr());
        }

        this.slaveSynchronize = new SlaveSynchronize(this);

        this.sendThreadPoolQueue =
                new LinkedBlockingQueue<Runnable>(this.brokerConfig.getSendThreadPoolQueueCapacity());

        this.pullThreadPoolQueue =
                new LinkedBlockingQueue<Runnable>(this.brokerConfig.getPullThreadPoolQueueCapacity());

        this.brokerStatsManager = new BrokerStatsManager(this);
    }


    public boolean initialize() {
        boolean result = true;

        // ??????Topic??????
        result = result && this.topicConfigManager.load();

        // ??????Consumer Offset
        result = result && this.consumerOffsetManager.load();
        // ??????Consumer subscription
        result = result && this.subscriptionGroupManager.load();

        // ??????????????????
        if (result) {
            try {
                this.messageStore = new DefaultMessageStore(this.messageStoreConfig);
            }
            catch (IOException e) {
                result = false;
                e.printStackTrace();
            }
        }

        // ????????????????????????
        result = result && this.messageStore.load();

        if (result) {
            // ??????????????????
            this.remotingServer =
                    new NettyRemotingServer(this.nettyServerConfig, this.clientHousekeepingService);

            // ??????????????????
            this.sendMessageExecutor = new ThreadPoolExecutor(//
                this.brokerConfig.getSendMessageThreadPoolNums(),//
                this.brokerConfig.getSendMessageThreadPoolNums(),//
                1000 * 60,//
                TimeUnit.MILLISECONDS,//
                this.sendThreadPoolQueue,//
                new ThreadFactoryImpl("SendMessageThread_"));

            this.pullMessageExecutor = new ThreadPoolExecutor(//
                this.brokerConfig.getPullMessageThreadPoolNums(),//
                this.brokerConfig.getPullMessageThreadPoolNums(),//
                1000 * 60,//
                TimeUnit.MILLISECONDS,//
                this.pullThreadPoolQueue,//
                new ThreadFactoryImpl("PullMessageThread_"));

            this.adminBrokerExecutor =
                    Executors.newFixedThreadPool(this.brokerConfig.getAdminBrokerThreadPoolNums(),
                        new ThreadFactoryImpl("AdminBrokerThread_"));

            this.registerProcessor();

            this.brokerStats = new BrokerStats((DefaultMessageStore) this.messageStore);

            // ????????????00:00:00???????????????
            final long initialDelay = UtilAll.computNextMorningTimeMillis() - System.currentTimeMillis();
            final long period = 1000 * 60 * 60 * 24;
            this.scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    try {
                        BrokerController.this.getBrokerStats().record();
                    }
                    catch (Exception e) {
                        log.error("schedule record error.", e);
                    }
                }
            }, initialDelay, period, TimeUnit.MILLISECONDS);

            // ?????????????????????
            this.scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    try {
                        BrokerController.this.consumerOffsetManager.persist();
                    }
                    catch (Exception e) {
                        log.error("schedule persist consumerOffset error.", e);
                    }
                }
            }, 1000 * 10, this.brokerConfig.getFlushConsumerOffsetInterval(), TimeUnit.MILLISECONDS);

            // ??????????????????????????????????????????10??????????????????
            this.scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    try {
                        BrokerController.this.consumerOffsetManager.scanUnsubscribedTopic();
                    }
                    catch (Exception e) {
                        log.error("schedule scanUnsubscribedTopic error.", e);
                    }
                }
            }, 10, 60, TimeUnit.MINUTES);

            // ?????????Name Server??????
            if (this.brokerConfig.getNamesrvAddr() != null) {
                this.brokerOuterAPI.updateNameServerAddressList(this.brokerConfig.getNamesrvAddr());
            }
            // ????????????Name Server??????
            else if (this.brokerConfig.isFetchNamesrvAddrByAddressServer()) {
                this.scheduledExecutorService.scheduleAtFixedRate(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            BrokerController.this.brokerOuterAPI.fetchNameServerAddr();
                        }
                        catch (Exception e) {
                            log.error("ScheduledTask fetchNameServerAddr exception", e);
                        }
                    }
                }, 1000 * 10, 1000 * 60 * 2, TimeUnit.MILLISECONDS);
            }

            // ?????????slave
            if (BrokerRole.SLAVE == this.messageStoreConfig.getBrokerRole()) {
                if (this.messageStoreConfig.getHaMasterAddress() != null
                        && this.messageStoreConfig.getHaMasterAddress().length() >= 6) {
                    this.messageStore.updateHaMasterAddress(this.messageStoreConfig.getHaMasterAddress());
                    this.updateMasterHAServerAddrPeriodically = false;
                }
                else {
                    this.updateMasterHAServerAddrPeriodically = true;
                }

                // Slave?????????Master??????????????????
                this.scheduledExecutorService.scheduleAtFixedRate(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            BrokerController.this.slaveSynchronize.syncAll();
                        }
                        catch (Exception e) {
                            log.error("ScheduledTask syncAll slave exception", e);
                        }
                    }
                }, 1000 * 10, 1000 * 60, TimeUnit.MILLISECONDS);
            }
            // ?????????Master?????????????????????
            else {
                this.scheduledExecutorService.scheduleAtFixedRate(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            BrokerController.this.printMasterAndSlaveDiff();
                        }
                        catch (Exception e) {
                            log.error("schedule printMasterAndSlaveDiff error.", e);
                        }
                    }
                }, 1000 * 10, 1000 * 60, TimeUnit.MILLISECONDS);
            }
        }

        return result;
    }


    public void registerProcessor() {
        /**
         * SendMessageProcessor
         */
        SendMessageProcessor sendProcessor = new SendMessageProcessor(this);
        sendProcessor.registerSendMessageHook(sendMessageHookList);
        this.remotingServer.registerProcessor(RequestCode.SEND_MESSAGE, sendProcessor,
            this.sendMessageExecutor);
        this.remotingServer.registerProcessor(RequestCode.SEND_MESSAGE_V2, sendProcessor,
            this.sendMessageExecutor);
        this.remotingServer.registerProcessor(RequestCode.CONSUMER_SEND_MSG_BACK, sendProcessor,
            this.sendMessageExecutor);

        /**
         * PullMessageProcessor
         */
        this.remotingServer.registerProcessor(RequestCode.PULL_MESSAGE, this.pullMessageProcessor,
            this.pullMessageExecutor);
        this.pullMessageProcessor.registerConsumeMessageHook(consumeMessageHookList);

        /**
         * QueryMessageProcessor
         */
        NettyRequestProcessor queryProcessor = new QueryMessageProcessor(this);
        this.remotingServer.registerProcessor(RequestCode.QUERY_MESSAGE, queryProcessor,
            this.pullMessageExecutor);
        this.remotingServer.registerProcessor(RequestCode.VIEW_MESSAGE_BY_ID, queryProcessor,
            this.pullMessageExecutor);

        /**
         * ClientManageProcessor
         */
        NettyRequestProcessor clientProcessor = new ClientManageProcessor(this);
        this.remotingServer.registerProcessor(RequestCode.HEART_BEAT, clientProcessor,
            this.adminBrokerExecutor);
        this.remotingServer.registerProcessor(RequestCode.UNREGISTER_CLIENT, clientProcessor,
            this.adminBrokerExecutor);
        this.remotingServer.registerProcessor(RequestCode.GET_CONSUMER_LIST_BY_GROUP, clientProcessor,
            this.adminBrokerExecutor);

        /**
         * EndTransactionProcessor
         */
        this.remotingServer.registerProcessor(RequestCode.END_TRANSACTION, new EndTransactionProcessor(this),
            this.sendMessageExecutor);

        /**
         * Default
         */
        AdminBrokerProcessor adminProcessor = new AdminBrokerProcessor(this);
        adminProcessor.registerConsumeMessageHook(this.consumeMessageHookList);
        this.remotingServer.registerDefaultProcessor(adminProcessor, this.adminBrokerExecutor);
    }


    public Broker2Client getBroker2Client() {
        return broker2Client;
    }


    public BrokerConfig getBrokerConfig() {
        return brokerConfig;
    }


    public String getConfigDataVersion() {
        return this.configDataVersion.toJson();
    }


    public ConsumerManager getConsumerManager() {
        return consumerManager;
    }


    public ConsumerOffsetManager getConsumerOffsetManager() {
        return consumerOffsetManager;
    }


    public MessageStore getMessageStore() {
        return messageStore;
    }


    public void setMessageStore(MessageStore messageStore) {
        this.messageStore = messageStore;
    }


    public MessageStoreConfig getMessageStoreConfig() {
        return messageStoreConfig;
    }


    public NettyServerConfig getNettyServerConfig() {
        return nettyServerConfig;
    }


    public ProducerManager getProducerManager() {
        return producerManager;
    }


    public PullMessageProcessor getPullMessageProcessor() {
        return pullMessageProcessor;
    }


    public PullRequestHoldService getPullRequestHoldService() {
        return pullRequestHoldService;
    }


    public RemotingServer getRemotingServer() {
        return remotingServer;
    }


    public void setRemotingServer(RemotingServer remotingServer) {
        this.remotingServer = remotingServer;
    }


    public SubscriptionGroupManager getSubscriptionGroupManager() {
        return subscriptionGroupManager;
    }


    public void shutdown() {
        if (this.brokerStatsManager != null) {
            this.brokerStatsManager.shutdown();
        }

        if (this.clientHousekeepingService != null) {
            this.clientHousekeepingService.shutdown();
        }

        if (this.pullRequestHoldService != null) {
            this.pullRequestHoldService.shutdown();
        }

        if (this.remotingServer != null) {
            this.remotingServer.shutdown();
        }

        if (this.messageStore != null) {
            this.messageStore.shutdown();
        }

        this.scheduledExecutorService.shutdown();
        try {
            this.scheduledExecutorService.awaitTermination(5000, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e) {
        }

        this.unregisterBrokerAll();

        if (this.sendMessageExecutor != null) {
            this.sendMessageExecutor.shutdown();
        }

        if (this.pullMessageExecutor != null) {
            this.pullMessageExecutor.shutdown();
        }

        if (this.adminBrokerExecutor != null) {
            this.adminBrokerExecutor.shutdown();
        }

        if (this.brokerOuterAPI != null) {
            this.brokerOuterAPI.shutdown();
        }

        this.consumerOffsetManager.persist();

        if (this.filterServerManager != null) {
            this.filterServerManager.shutdown();
        }
    }


    private void unregisterBrokerAll() {
        this.brokerOuterAPI.unregisterBrokerAll(//
            this.brokerConfig.getBrokerClusterName(), //
            this.getBrokerAddr(), //
            this.brokerConfig.getBrokerName(), //
            this.brokerConfig.getBrokerId());
    }


    public String getBrokerAddr() {
        String addr = this.brokerConfig.getBrokerIP1() + ":" + this.nettyServerConfig.getListenPort();
        return addr;
    }


    public void start() throws Exception {
        if (this.messageStore != null) {
            this.messageStore.start();
        }

        if (this.remotingServer != null) {
            this.remotingServer.start();
        }

        if (this.brokerOuterAPI != null) {
            this.brokerOuterAPI.start();
        }

        if (this.pullRequestHoldService != null) {
            this.pullRequestHoldService.start();
        }

        if (this.clientHousekeepingService != null) {
            this.clientHousekeepingService.start();
        }

        if (this.filterServerManager != null) {
            this.filterServerManager.start();
        }

        // ????????????????????????
        this.registerBrokerAll();

        // ????????????Broker???Name Server
        this.scheduledExecutorService.scheduleAtFixedRate(new Runnable() {

            @Override
            public void run() {
                try {
                    BrokerController.this.registerBrokerAll();
                }
                catch (Exception e) {
                    log.error("registerBrokerAll Exception", e);
                }
            }
        }, 1000 * 10, 1000 * 30, TimeUnit.MILLISECONDS);

        if (this.brokerStatsManager != null) {
            this.brokerStatsManager.start();
        }

        // ???????????????Topic
        this.addDeleteTopicTask();
    }


    public synchronized void registerBrokerAll() {
        TopicConfigSerializeWrapper topicConfigWrapper =
                this.getTopicConfigManager().buildTopicConfigSerializeWrapper();

        // ?????? Broker ????????????
        if (!PermName.isWriteable(this.getBrokerConfig().getBrokerPermission())
                || !PermName.isReadable(this.getBrokerConfig().getBrokerPermission())) {
            ConcurrentHashMap<String, TopicConfig> topicConfigTable =
                    new ConcurrentHashMap<String, TopicConfig>(topicConfigWrapper.getTopicConfigTable());
            for (TopicConfig topicConfig : topicConfigTable.values()) {
                topicConfig.setPerm(this.getBrokerConfig().getBrokerPermission());
            }
            topicConfigWrapper.setTopicConfigTable(topicConfigTable);
        }

        RegisterBrokerResult registerBrokerResult = this.brokerOuterAPI.registerBrokerAll(//
            this.brokerConfig.getBrokerClusterName(), //
            this.getBrokerAddr(), //
            this.brokerConfig.getBrokerName(), //
            this.brokerConfig.getBrokerId(), //
            this.getHAServerAddr(), //
            topicConfigWrapper,//
            this.filterServerManager.buildNewFilterServerList()//
            );

        if (registerBrokerResult != null) {
            if (this.updateMasterHAServerAddrPeriodically && registerBrokerResult.getHaServerAddr() != null) {
                this.messageStore.updateHaMasterAddress(registerBrokerResult.getHaServerAddr());
            }

            this.slaveSynchronize.setMasterAddr(registerBrokerResult.getMasterAddr());
        }
    }


    public TopicConfigManager getTopicConfigManager() {
        return topicConfigManager;
    }


    public void setTopicConfigManager(TopicConfigManager topicConfigManager) {
        this.topicConfigManager = topicConfigManager;
    }


    public String getHAServerAddr() {
        String addr = this.brokerConfig.getBrokerIP2() + ":" + this.messageStoreConfig.getHaListenPort();
        return addr;
    }


    public void updateAllConfig(Properties properties) {
        MixAll.properties2Object(properties, brokerConfig);
        MixAll.properties2Object(properties, nettyServerConfig);
        MixAll.properties2Object(properties, nettyClientConfig);
        MixAll.properties2Object(properties, messageStoreConfig);
        this.configDataVersion.nextVersion();
        this.flushAllConfig();
    }


    private void flushAllConfig() {
        String allConfig = this.encodeAllConfig();
        try {
            MixAll.string2File(allConfig, BrokerPathConfigHelper.getBrokerConfigPath());
            log.info("flush broker config, {} OK", BrokerPathConfigHelper.getBrokerConfigPath());
        }
        catch (IOException e) {
            log.info("flush broker config Exception, " + BrokerPathConfigHelper.getBrokerConfigPath(), e);
        }
    }


    public String encodeAllConfig() {
        StringBuilder sb = new StringBuilder();
        {
            Properties properties = MixAll.object2Properties(this.brokerConfig);
            if (properties != null) {
                sb.append(MixAll.properties2String(properties));
            }
            else {
                log.error("encodeAllConfig object2Properties error");
            }
        }

        {
            Properties properties = MixAll.object2Properties(this.messageStoreConfig);
            if (properties != null) {
                sb.append(MixAll.properties2String(properties));
            }
            else {
                log.error("encodeAllConfig object2Properties error");
            }
        }

        {
            Properties properties = MixAll.object2Properties(this.nettyServerConfig);
            if (properties != null) {
                sb.append(MixAll.properties2String(properties));
            }
            else {
                log.error("encodeAllConfig object2Properties error");
            }
        }

        {
            Properties properties = MixAll.object2Properties(this.nettyClientConfig);
            if (properties != null) {
                sb.append(MixAll.properties2String(properties));
            }
            else {
                log.error("encodeAllConfig object2Properties error");
            }
        }
        return sb.toString();
    }


    public RebalanceLockManager getRebalanceLockManager() {
        return rebalanceLockManager;
    }


    public SlaveSynchronize getSlaveSynchronize() {
        return slaveSynchronize;
    }


    public BrokerOuterAPI getBrokerOuterAPI() {
        return brokerOuterAPI;
    }


    public ExecutorService getPullMessageExecutor() {
        return pullMessageExecutor;
    }


    public void setPullMessageExecutor(ExecutorService pullMessageExecutor) {
        this.pullMessageExecutor = pullMessageExecutor;
    }


    public BrokerStats getBrokerStats() {
        return brokerStats;
    }


    public void setBrokerStats(BrokerStats brokerStats) {
        this.brokerStats = brokerStats;
    }


    public BlockingQueue<Runnable> getSendThreadPoolQueue() {
        return sendThreadPoolQueue;
    }


    public FilterServerManager getFilterServerManager() {
        return filterServerManager;
    }


    public BrokerStatsManager getBrokerStatsManager() {
        return brokerStatsManager;
    }


    private void printMasterAndSlaveDiff() {
        long diff = this.messageStore.slaveFallBehindMuch();

        // XXX: warn and notify me
        log.info("slave fall behind master, how much, {} bytes", diff);
    }


    public void addDeleteTopicTask() {
        // 5????????????????????????topic
        this.scheduledExecutorService.schedule(new Runnable() {
            @Override
            public void run() {
                int removedTopicCnt =
                        BrokerController.this.messageStore.cleanUnusedTopic(BrokerController.this
                            .getTopicConfigManager().getTopicConfigTable().keySet());
                log.info("addDeleteTopicTask removed topic count {}", removedTopicCnt);
            }
        }, 5, TimeUnit.MINUTES);
    }

    // ???????????????????????? hook
    private final List<SendMessageHook> sendMessageHookList = new ArrayList<SendMessageHook>();


    public void registerSendMessageHook(final SendMessageHook hook) {
        this.sendMessageHookList.add(hook);
        log.info("register SendMessageHook Hook, {}", hook.hookName());
    }

    // ???????????????????????? hook
    private final List<ConsumeMessageHook> consumeMessageHookList = new ArrayList<ConsumeMessageHook>();


    public void registerConsumeMessageHook(final ConsumeMessageHook hook) {
        this.consumeMessageHookList.add(hook);
        log.info("register ConsumeMessageHook Hook, {}", hook.hookName());
    }


    public void registerServerRPCHook(RPCHook rpcHook) {
        getRemotingServer().registerRPCHook(rpcHook);
    }


    public void registerClientRPCHook(RPCHook rpcHook) {
        this.getBrokerOuterAPI().registerRPCHook(rpcHook);
    }
}
