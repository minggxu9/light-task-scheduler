package com.lts.jobtracker.channel;

import com.lts.core.cluster.NodeType;
import com.lts.core.logger.Logger;
import com.lts.core.logger.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Robert HG (254963746@qq.com) on 7/24/14.
 *         管理channel
 */
public class ChannelManager {

    private final Logger LOGGER = LoggerFactory.getLogger(ChannelManager.class);
    // 客户端列表 (要保证同一个group的node要是无状态的)
    private final ConcurrentHashMap<String/*clientGroup*/, List<ChannelWrapper>> clientChannelMap = new ConcurrentHashMap<String, List<ChannelWrapper>>();
    // 任务节点列表
    private final ConcurrentHashMap<String/*taskTrackerNodeGroup*/, List<ChannelWrapper>> taskTrackerChannelMap = new ConcurrentHashMap<String, List<ChannelWrapper>>();
    // 用来定时检查已经关闭的channel
    private final ScheduledExecutorService channelCheckExecutorService = Executors.newScheduledThreadPool(1);

    private ScheduledFuture scheduledFuture;
    private AtomicBoolean start = new AtomicBoolean(false);

    public void start() {
        try {
            if (start.compareAndSet(false, true)) {
                scheduledFuture = channelCheckExecutorService.scheduleWithFixedDelay(new Runnable() {
                    @Override
                    public void run() {
                        checkCloseChannel(clientChannelMap);
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("JobClient Channel Pool " + clientChannelMap);
                        }
                        checkCloseChannel(taskTrackerChannelMap);
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("TaskTracker Channel Pool " + taskTrackerChannelMap);
                        }

                    }
                }, 10, 10, TimeUnit.SECONDS);
            }
            LOGGER.info("Start channel manager success!");
        } catch (Throwable t) {
            LOGGER.error("Start channel manager failed!", t);
        }
    }

    public void stop() {
        try {
            if (start.compareAndSet(true, false)) {
                scheduledFuture.cancel(true);
                channelCheckExecutorService.shutdown();
            }
            LOGGER.info("Stop channel manager success!");
        } catch (Throwable t) {
            LOGGER.error("Stop channel manager failed!", t);
        }
    }

    /**
     * 检查 关闭的channel
     *
     * @param channelMap
     */
    private void checkCloseChannel(ConcurrentHashMap<String, List<ChannelWrapper>> channelMap) {
        for (Map.Entry<String, List<ChannelWrapper>> entry : channelMap.entrySet()) {
            List<ChannelWrapper> channels = entry.getValue();
            List<ChannelWrapper> removeList = new ArrayList<ChannelWrapper>();
            for (ChannelWrapper channel : channels) {
                if (channel.isClosed()) {
                    removeList.add(channel);
                    LOGGER.info("close channel={}", channel);
                }
            }
            channels.removeAll(removeList);
        }
    }

    public List<ChannelWrapper> getChannels(String nodeGroup, NodeType nodeType) {
        if (nodeType == NodeType.JOB_CLIENT) {
            return clientChannelMap.get(nodeGroup);
        } else if (nodeType == NodeType.TASK_TRACKER) {
            return taskTrackerChannelMap.get(nodeGroup);
        }
        return null;
    }

    /**
     * 根据 节点唯一编号得到 channel
     *
     * @param nodeGroup
     * @param nodeType
     * @param identity
     * @return
     */
    public ChannelWrapper getChannel(String nodeGroup, NodeType nodeType, String identity) {
        List<ChannelWrapper> channelWrappers = getChannels(nodeGroup, nodeType);
        if (channelWrappers != null && channelWrappers.size() != 0) {
            for (ChannelWrapper channelWrapper : channelWrappers) {
                if (channelWrapper.getIdentity().equals(identity)) {
                    return channelWrapper;
                }
            }
        }
        return null;
    }

    /**
     * 添加channel
     *
     * @param channel
     */
    public void offerChannel(ChannelWrapper channel) {
        String nodeGroup = channel.getNodeGroup();
        NodeType nodeType = channel.getNodeType();
        List<ChannelWrapper> channels = getChannels(nodeGroup, nodeType);
        if (channels == null) {
            channels = new ArrayList<ChannelWrapper>();
            if (nodeType == NodeType.JOB_CLIENT) {
                clientChannelMap.put(nodeGroup, channels);
            } else if (nodeType == NodeType.TASK_TRACKER) {
                taskTrackerChannelMap.put(nodeGroup, channels);
            }
            channels.add(channel);
            LOGGER.info("new connected channel={}", channel);
        } else {
            if (!channels.contains(channel)) {
                channels.add(channel);
                LOGGER.info("new connected channel={}", channel);
            }
        }
    }

    public void removeChannel(ChannelWrapper channel) {
        String nodeGroup = channel.getNodeGroup();
        NodeType nodeType = channel.getNodeType();
        List<ChannelWrapper> channels = getChannels(nodeGroup, nodeType);
        if (channels != null) {
            channels.remove(channel);
            LOGGER.info("remove channel={}", channel);
        }
    }
}
