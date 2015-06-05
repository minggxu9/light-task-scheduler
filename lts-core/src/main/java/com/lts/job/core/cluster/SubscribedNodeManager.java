package com.lts.job.core.cluster;


import com.lts.job.core.Application;
import com.lts.job.core.listener.NodeChangeListener;
import com.lts.job.core.logger.Logger;
import com.lts.job.core.logger.LoggerFactory;
import com.lts.job.core.util.CollectionUtils;
import com.lts.job.core.util.ListUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Robert HG (254963746@qq.com) on 6/22/14.
 *         节点管理 (主要用于管理自己关注的节点)
 */
public class SubscribedNodeManager implements NodeChangeListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubscribedNodeManager.class);
    private final ConcurrentHashMap<NodeType, List<Node>> NODES = new ConcurrentHashMap<NodeType, List<Node>>();

    private Application application;

    public SubscribedNodeManager(Application application) {
        this.application = application;
    }

    /**
     * 添加监听的节点
     *
     * @param node
     */
    private void addNode(Node node) {
        // 当前节点是管理节点，直接添加
        if (NodeType.JOB_ADMIN.equals(application.getConfig().getNodeType())) {
            _addNode(node);
        } else if ((NodeType.JOB_TRACKER.equals(node.getNodeType()))) {
            // 如果增加的JobTracker节点，那么直接添加，因为三种节点都需要监听
            _addNode(node);
        } else if (NodeType.JOB_TRACKER.equals(application.getConfig().getNodeType())) {
            // 如果当天节点是JobTracker节点，那么直接添加，因为JobTracker节点要监听三种节点
            _addNode(node);
        } else if (application.getConfig().getNodeType().equals(node.getNodeType())
                && application.getConfig().getNodeGroup().equals(node.getGroup())) {
            // 剩下这种情况是JobClient和TaskTracker都只监听和自己同一个group的节点
            _addNode(node);
        }
    }

    private void _addNode(Node node) {
        List<Node> nodeList = NODES.get(node.getNodeType());
        if (CollectionUtils.isEmpty(nodeList)) {
            nodeList = new CopyOnWriteArrayList<Node>();
            List<Node> oldNodeList = NODES.putIfAbsent(node.getNodeType(), nodeList);
            if (oldNodeList != null) {
                nodeList = oldNodeList;
            }
        }
        nodeList.add(node);
        LOGGER.info("Add {}", node);
    }

    public List<Node> getNodeList(final NodeType nodeType, final String nodeGroup) {

        List<Node> nodes = NODES.get(nodeType);

        return ListUtils.filter(nodes, new ListUtils.Filter<Node>() {
            @Override
            public boolean filter(Node node) {
                return node.getGroup().equals(nodeGroup);
            }
        });
    }

    public List<Node> getNodeList(NodeType nodeType) {
        return NODES.get(nodeType);
    }

    public List<Node> getNodeList() {
        List<Node> nodes = new ArrayList<Node>();

        for (Map.Entry<NodeType, List<Node>> entry : NODES.entrySet()) {
            if (CollectionUtils.isNotEmpty(entry.getValue())) {
                nodes.addAll(entry.getValue());
            }
        }
        return nodes;
    }

    private void removeNode(Node delNode) {
        List<Node> nodeList = NODES.get(delNode.getNodeType());
        if (CollectionUtils.isNotEmpty(nodeList)) {
            for (Node node : nodeList) {
                if (node.getIdentity().equals(delNode.getIdentity())) {
                    nodeList.remove(node);
                    LOGGER.info("Remove {}", node);
                }
            }
        }
    }

    @Override
    public void addNodes(List<Node> nodes) {
        if (CollectionUtils.isEmpty(nodes)) {
            return;
        }
        for (Node node : nodes) {
            addNode(node);
        }
    }

    @Override
    public void removeNodes(List<Node> nodes) {
        if (CollectionUtils.isEmpty(nodes)) {
            return;
        }
        for (Node node : nodes) {
            removeNode(node);
        }
    }
}