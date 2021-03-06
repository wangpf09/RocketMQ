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
package com.alibaba.rocketmq.tools.admin;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.alibaba.rocketmq.client.MQAdmin;
import com.alibaba.rocketmq.client.exception.MQBrokerException;
import com.alibaba.rocketmq.client.exception.MQClientException;
import com.alibaba.rocketmq.common.TopicConfig;
import com.alibaba.rocketmq.common.admin.ConsumeStats;
import com.alibaba.rocketmq.common.admin.RollbackStats;
import com.alibaba.rocketmq.common.admin.TopicStatsTable;
import com.alibaba.rocketmq.common.message.MessageExt;
import com.alibaba.rocketmq.common.message.MessageQueue;
import com.alibaba.rocketmq.common.protocol.body.ClusterInfo;
import com.alibaba.rocketmq.common.protocol.body.ConsumeMessageDirectlyResult;
import com.alibaba.rocketmq.common.protocol.body.ConsumerConnection;
import com.alibaba.rocketmq.common.protocol.body.ConsumerRunningInfo;
import com.alibaba.rocketmq.common.protocol.body.GroupList;
import com.alibaba.rocketmq.common.protocol.body.KVTable;
import com.alibaba.rocketmq.common.protocol.body.ProducerConnection;
import com.alibaba.rocketmq.common.protocol.body.QueueTimeSpan;
import com.alibaba.rocketmq.common.protocol.body.TopicList;
import com.alibaba.rocketmq.common.protocol.route.TopicRouteData;
import com.alibaba.rocketmq.common.subscription.SubscriptionGroupConfig;
import com.alibaba.rocketmq.remoting.exception.RemotingCommandException;
import com.alibaba.rocketmq.remoting.exception.RemotingConnectException;
import com.alibaba.rocketmq.remoting.exception.RemotingException;
import com.alibaba.rocketmq.remoting.exception.RemotingSendRequestException;
import com.alibaba.rocketmq.remoting.exception.RemotingTimeoutException;
import com.alibaba.rocketmq.tools.admin.api.MessageTrack;


/**
 * MQ?????????????????????????????????MQ???????????????????????????<br>
 * ??????Topic??????????????????????????????????????????
 * 
 * @since 2013-7-14
 */
public interface MQAdminExt extends MQAdmin {
    public void start() throws MQClientException;


    public void shutdown();


    /**
     * ??????Broker??????
     * 
     * @param brokerAddr
     * @param properties
     * @throws MQBrokerException
     * @throws InterruptedException
     * @throws UnsupportedEncodingException
     * @throws RemotingTimeoutException
     * @throws RemotingSendRequestException
     * @throws RemotingConnectException
     */
    public void updateBrokerConfig(final String brokerAddr, final Properties properties)
            throws RemotingConnectException, RemotingSendRequestException, RemotingTimeoutException,
            UnsupportedEncodingException, InterruptedException, MQBrokerException;


    /**
     * ?????????Broker??????????????????Topic??????
     * 
     * @param addr
     * @param config
     * @throws MQClientException
     * @throws InterruptedException
     * @throws MQBrokerException
     * @throws RemotingException
     */
    public void createAndUpdateTopicConfig(final String addr, final TopicConfig config)
            throws RemotingException, MQBrokerException, InterruptedException, MQClientException;


    /**
     * ?????????Broker?????????????????????????????????
     * 
     * @param addr
     * @param config
     * @throws MQClientException
     * @throws InterruptedException
     * @throws MQBrokerException
     * @throws RemotingException
     */
    public void createAndUpdateSubscriptionGroupConfig(final String addr, final SubscriptionGroupConfig config)
            throws RemotingException, MQBrokerException, InterruptedException, MQClientException;


    /**
     * ????????????Broker??????????????????
     * 
     * @param addr
     * @param group
     * @return
     */
    public SubscriptionGroupConfig examineSubscriptionGroupConfig(final String addr, final String group);


    /**
     * ????????????Broker???Topic??????
     * 
     * @param addr
     * @param topic
     * @return
     */
    public TopicConfig examineTopicConfig(final String addr, final String topic);


    /**
     * ??????Topic Offset??????
     * 
     * @param topic
     * @return
     */
    public TopicStatsTable examineTopicStats(final String topic) throws RemotingException, MQClientException,
            InterruptedException, MQBrokerException;


    /**
     * ???Name Server????????????Topic??????
     * 
     * @return
     * @throws InterruptedException
     * @throws MQClientException
     * @throws RemotingException
     */
    public TopicList fetchAllTopicList() throws RemotingException, MQClientException, InterruptedException;


    /**
     * ??????Broker???????????????
     * 
     * @return
     * @throws MQBrokerException
     * @throws InterruptedException
     * @throws RemotingTimeoutException
     * @throws RemotingSendRequestException
     * @throws RemotingConnectException
     */
    public KVTable fetchBrokerRuntimeStats(final String brokerAddr) throws RemotingConnectException,
            RemotingSendRequestException, RemotingTimeoutException, InterruptedException, MQBrokerException;


    /**
     * ??????????????????
     * 
     * @param consumerGroup
     * @return
     * @throws InterruptedException
     * @throws MQClientException
     * @throws RemotingException
     * @throws MQBrokerException
     */
    public ConsumeStats examineConsumeStats(final String consumerGroup) throws RemotingException,
            MQClientException, InterruptedException, MQBrokerException;


    /**
     * ??????????????????
     * 
     * @return
     */
    public ClusterInfo examineBrokerClusterInfo() throws InterruptedException, MQBrokerException,
            RemotingTimeoutException, RemotingSendRequestException, RemotingConnectException;


    /**
     * ??????Topic????????????
     * 
     * @param topic
     * @return
     */
    public TopicRouteData examineTopicRouteInfo(final String topic) throws RemotingException,
            MQClientException, InterruptedException;


    /**
     * ??????Consumer???????????????????????????
     * 
     * @param consumerGroup
     * @return
     * @throws MQBrokerException
     * @throws InterruptedException
     * @throws RemotingTimeoutException
     * @throws RemotingSendRequestException
     * @throws RemotingConnectException
     * @throws MQClientException
     * @throws RemotingException
     */
    public ConsumerConnection examineConsumerConnectionInfo(final String consumerGroup)
            throws RemotingConnectException, RemotingSendRequestException, RemotingTimeoutException,
            InterruptedException, MQBrokerException, RemotingException, MQClientException;


    /**
     * ??????Producer????????????
     * 
     * @param producerGroup
     * @param topic
     * @return
     * @throws InterruptedException
     * @throws MQClientException
     * @throws RemotingException
     * @throws MQBrokerException
     */
    public ProducerConnection examineProducerConnectionInfo(final String producerGroup, final String topic)
            throws RemotingException, MQClientException, InterruptedException, MQBrokerException;


    /**
     * ??????Name Server????????????
     * 
     * @return
     */
    public List<String> getNameServerAddressList();


    /**
     * ????????????Broker???????????????????????????Name Server
     * 
     * @param brokerName
     * @return ????????????????????????topic
     * @throws MQClientException
     * @throws InterruptedException
     * @throws RemotingTimeoutException
     * @throws RemotingSendRequestException
     * @throws RemotingConnectException
     * @throws RemotingCommandException
     */
    public int wipeWritePermOfBroker(final String namesrvAddr, String brokerName)
            throws RemotingCommandException, RemotingConnectException, RemotingSendRequestException,
            RemotingTimeoutException, InterruptedException, MQClientException;


    /**
     * ???Name Server?????????????????????
     * 
     * @param namespace
     * @param key
     * @param value
     */
    public void putKVConfig(final String namespace, final String key, final String value);


    /**
     * ???Name Server?????????????????????
     * 
     * @param namespace
     * @param key
     * @return
     */
    public String getKVConfig(final String namespace, final String key) throws RemotingException,
            MQClientException, InterruptedException;


    /**
     * ????????????Namespace????????????kv
     * 
     * @param namespace
     * @return
     * @throws InterruptedException
     * @throws MQClientException
     * @throws RemotingException
     */
    public KVTable getKVListByNamespace(final String namespace) throws RemotingException, MQClientException,
            InterruptedException;


    /**
     * ?????? broker ?????? topic ??????
     * 
     * @param addrs
     * @param topic
     * @throws RemotingException
     * @throws MQBrokerException
     * @throws InterruptedException
     * @throws MQClientException
     */
    public void deleteTopicInBroker(final Set<String> addrs, final String topic) throws RemotingException,
            MQBrokerException, InterruptedException, MQClientException;


    /**
     * ?????? broker ?????? topic ??????
     * 
     * @param addrs
     * @param topic
     * @throws RemotingException
     * @throws MQBrokerException
     * @throws InterruptedException
     * @throws MQClientException
     */
    public void deleteTopicInNameServer(final Set<String> addrs, final String topic)
            throws RemotingException, MQBrokerException, InterruptedException, MQClientException;


    /**
     * ?????? broker ?????? subscription group ??????
     * 
     * @param addr
     * @param groupName
     * @throws RemotingException
     * @throws MQBrokerException
     * @throws InterruptedException
     * @throws MQClientException
     */
    public void deleteSubscriptionGroup(final String addr, String groupName) throws RemotingException,
            MQBrokerException, InterruptedException, MQClientException;


    /**
     * ??? namespace ????????????????????? KV ??????
     * 
     * @param namespace
     * @param key
     * @param value
     * @throws RemotingException
     * @throws MQBrokerException
     * @throws InterruptedException
     * @throws MQClientException
     */
    public void createAndUpdateKvConfig(String namespace, String key, String value) throws RemotingException,
            MQBrokerException, InterruptedException, MQClientException;


    /**
     * ?????? namespace ?????? KV ??????
     * 
     * @param namespace
     * @param key
     * @throws RemotingException
     * @throws MQBrokerException
     * @throws InterruptedException
     * @throws MQClientException
     */
    public void deleteKvConfig(String namespace, String key) throws RemotingException, MQBrokerException,
            InterruptedException, MQClientException;


    /**
     * ?????? server ip ?????? project ??????
     * 
     * @param ip
     * @throws RemotingException
     * @throws MQBrokerException
     * @throws InterruptedException
     * @throws MQClientException
     * @return
     */
    public String getProjectGroupByIp(String ip) throws RemotingException, MQBrokerException,
            InterruptedException, MQClientException;


    /**
     * ?????? project ??????????????? server ip ??????
     * 
     * @param projectGroup
     * @throws RemotingException
     * @throws MQBrokerException
     * @throws InterruptedException
     * @throws MQClientException
     * @return
     */
    public String getIpsByProjectGroup(String projectGroup) throws RemotingException, MQBrokerException,
            InterruptedException, MQClientException;


    /**
     * ?????? project group ??????????????? server ip
     * 
     * @param key
     * @throws RemotingException
     * @throws MQBrokerException
     * @throws InterruptedException
     * @throws MQClientException
     */
    public void deleteIpsByProjectGroup(String key) throws RemotingException, MQBrokerException,
            InterruptedException, MQClientException;


    /**
     * ??????????????????????????????(?????????????????????)
     * 
     * @param consumerGroup
     * @param topic
     * @param timestamp
     * @param force
     * @throws RemotingException
     * @throws MQBrokerException
     * @throws InterruptedException
     * @throws MQClientException
     * @return
     */
    public List<RollbackStats> resetOffsetByTimestampOld(String consumerGroup, String topic, long timestamp,
            boolean force) throws RemotingException, MQBrokerException, InterruptedException,
            MQClientException;


    /**
     * ??????????????????????????????(????????????????????????)
     * 
     * @param topic
     * @param group
     * @param timestamp
     * @param isForce
     * @throws RemotingException
     * @throws MQBrokerException
     * @throws InterruptedException
     * @throws MQClientException
     * @return
     */
    public Map<MessageQueue, Long> resetOffsetByTimestamp(String topic, String group, long timestamp,
            boolean isForce) throws RemotingException, MQBrokerException, InterruptedException,
            MQClientException;


    /**
     * ???????????????????????????Consumer???????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
     * 
     * @param consumerGroup
     * @param topic
     * @param timestamp
     * @throws InterruptedException
     * @throws MQBrokerException
     * @throws RemotingException
     * @throws MQClientException
     */
    public void resetOffsetNew(String consumerGroup, String topic, long timestamp) throws RemotingException,
            MQBrokerException, InterruptedException, MQClientException;


    /**
     * ?????????????????????????????????????????????
     * 
     * @param topic
     * @param group
     * @param clientAddr
     * @return
     * @throws RemotingException
     * @throws MQBrokerException
     * @throws InterruptedException
     * @throws MQClientException
     */
    public Map<String, Map<MessageQueue, Long>> getConsumeStatus(String topic, String group, String clientAddr)
            throws RemotingException, MQBrokerException, InterruptedException, MQClientException;


    /**
     * ??????????????????????????????????????????
     * 
     * @param key
     * @param value
     * @param isCluster
     * @throws RemotingException
     * @throws MQBrokerException
     * @throws InterruptedException
     * @throws MQClientException
     */
    public void createOrUpdateOrderConf(String key, String value, boolean isCluster)
            throws RemotingException, MQBrokerException, InterruptedException, MQClientException;


    /**
     * ??????Topic??????????????????????????????
     * 
     * @param topic
     * @return
     * @throws MQBrokerException
     * @throws InterruptedException
     * @throws RemotingTimeoutException
     * @throws RemotingSendRequestException
     * @throws RemotingConnectException
     * @throws MQClientException
     * @throws RemotingException
     */
    public GroupList queryTopicConsumeByWho(final String topic) throws RemotingConnectException,
            RemotingSendRequestException, RemotingTimeoutException, InterruptedException, MQBrokerException,
            RemotingException, MQClientException;


    /**
     * ?????? topic ??? group ???????????????????????????
     * 
     * @param topic
     * @param group
     * @return
     * @throws RemotingConnectException
     * @throws RemotingSendRequestException
     * @throws RemotingTimeoutException
     * @throws InterruptedException
     * @throws MQBrokerException
     * @throws RemotingException
     * @throws MQClientException
     */
    public Set<QueueTimeSpan> queryConsumeTimeSpan(final String topic, final String group)
            throws InterruptedException, MQBrokerException, RemotingException, MQClientException;


    /**
     * ?????????????????????????????????
     * 
     * @param cluster
     *            null?????????????????????
     * @return ??????????????????
     * @throws RemotingConnectException
     * @throws RemotingSendRequestException
     * @throws RemotingTimeoutException
     * @throws MQClientException
     * @throws InterruptedException
     */
    public boolean cleanExpiredConsumerQueue(String cluster) throws RemotingConnectException,
            RemotingSendRequestException, RemotingTimeoutException, MQClientException, InterruptedException;


    /**
     * ???????????????broker???????????????????????????
     * 
     * @param addr
     * @return ??????????????????
     * @throws RemotingConnectException
     * @throws RemotingSendRequestException
     * @throws RemotingTimeoutException
     * @throws MQClientException
     * @throws InterruptedException
     */
    public boolean cleanExpiredConsumerQueueByAddr(String addr) throws RemotingConnectException,
            RemotingSendRequestException, RemotingTimeoutException, MQClientException, InterruptedException;


    /**
     * ??????Consumer??????????????????
     * 
     * @param consumerGroup
     * @param clientId
     * @return
     * @throws InterruptedException
     * @throws MQClientException
     * @throws RemotingException
     */
    public ConsumerRunningInfo getConsumerRunningInfo(final String consumerGroup, final String clientId,
            final boolean jstack) throws RemotingException, MQClientException, InterruptedException;


    /**
     * ?????????Consumer??????????????????
     * 
     * @param consumerGroup
     * @param clientId
     * @param msgId
     * @return
     * @throws InterruptedException
     * @throws MQClientException
     * @throws RemotingException
     * @throws MQBrokerException
     */
    public ConsumeMessageDirectlyResult consumeMessageDirectly(String consumerGroup, //
            String clientId, //
            String msgId) throws RemotingException, MQClientException, InterruptedException,
            MQBrokerException;


    /**
     * ???????????????????????????
     * 
     * @param msg
     * @return
     * @throws RemotingException
     * @throws MQClientException
     * @throws InterruptedException
     * @throws MQBrokerException
     */
    public List<MessageTrack> messageTrackDetail(MessageExt msg) throws RemotingException, MQClientException,
            InterruptedException, MQBrokerException;
}
