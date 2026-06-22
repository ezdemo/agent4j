package site.sorghum.agent4j.bin.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Workflow — 工作流。
 * <p>
 * 支持有向无环图（DAG）结构，节点间可有多依赖关系。
 * 支持混合模式：LLM生成初始工作流 + 用户手动调整。
 * </p>
 *
 * @author Sorghum
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Workflow {
    /** UUID */
    private String id;
    /** 关联的会话 ID */
    private String sessionId;
    /** 工作区 hash（冗余，方便全局巡检） */
    private String workspaceHash;
    /** 工作流标题 */
    private String title;
    /** 详细描述 */
    private String description;
    /** 工作流状态 */
    private WorkflowStatus status;
    /** 每节点最大重试次数（默认 3） */
    @Builder.Default
    private int maxRetries = 3;

    /** 节点列表 */
    @Builder.Default
    private List<WorkflowNode> nodes = new ArrayList<>();
    /** 边列表（依赖关系） */
    @Builder.Default
    private List<WorkflowEdge> edges = new ArrayList<>();

    /** 创建时间 */
    private Instant createdAt;
    /** 最后更新时间 */
    private Instant updatedAt;
    /** 完成时间 */
    private Instant completedAt;

    /**
     * 根据节点ID查找节点。
     */
    public WorkflowNode findNode(String nodeId) {
        return nodes.stream()
                .filter(n -> n.getId().equals(nodeId))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取指定节点的所有前驱节点ID。
     */
    public List<String> getPredecessorIds(String nodeId) {
        return edges.stream()
                .filter(e -> e.getTo().equals(nodeId))
                .map(WorkflowEdge::getFrom)
                .collect(Collectors.toList());
    }

    /**
     * 获取指定节点的所有后继节点ID。
     */
    public List<String> getSuccessorIds(String nodeId) {
        return edges.stream()
                .filter(e -> e.getFrom().equals(nodeId))
                .map(WorkflowEdge::getTo)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有就绪节点（所有依赖已满足）。
     */
    public List<WorkflowNode> getReadyNodes() {
        return nodes.stream()
                .filter(node -> {
                    // 跳过已完成、失败、跳过的节点
                    if (node.isCompleted() || node.getStatus() == NodeStatus.FAILED) {
                        return false;
                    }
                    // 跳过正在执行或等待中的节点
                    if (node.isRunning() || node.isWaiting()) {
                        return false;
                    }
                    // 检查所有前驱节点是否已完成
                    List<String> predecessors = getPredecessorIds(node.getId());
                    if (predecessors.isEmpty()) {
                        // 没有前驱节点，检查是否为开始节点或就绪状态
                        return node.getType() == NodeType.START || node.getStatus() == NodeStatus.READY;
                    }
                    // 所有前驱节点必须已完成
                    return predecessors.stream().allMatch(predId -> {
                        WorkflowNode pred = findNode(predId);
                        return pred != null && pred.isCompleted();
                    });
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取所有正在执行的节点。
     */
    public List<WorkflowNode> getRunningNodes() {
        return nodes.stream()
                .filter(WorkflowNode::isRunning)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有等待中的节点。
     */
    public List<WorkflowNode> getWaitingNodes() {
        return nodes.stream()
                .filter(WorkflowNode::isWaiting)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有失败的节点。
     */
    public List<WorkflowNode> getFailedNodes() {
        return nodes.stream()
                .filter(n -> n.getStatus() == NodeStatus.FAILED)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有可重试的节点。
     */
    public List<WorkflowNode> getRetriableNodes() {
        return nodes.stream()
                .filter(n -> n.isRetriable(maxRetries))
                .collect(Collectors.toList());
    }

    /**
     * 生成进度文本：如 "3/6 (50%)"
     */
    public String progressText() {
        long done = nodes.stream().filter(WorkflowNode::isCompleted).count();
        long total = nodes.size();
        long pct = total > 0 ? (done * 100 / total) : 0;
        return done + "/" + total + " (" + pct + "%)";
    }

    /**
     * 判断工作流是否全部完成。
     */
    public boolean isAllDone() {
        return nodes != null && !nodes.isEmpty() && nodes.stream()
                .allMatch(n -> n.isCompleted() || n.getType() == NodeType.START || n.getType() == NodeType.END);
    }

    /**
     * 判断工作流是否有失败节点。
     */
    public boolean hasFailed() {
        return nodes.stream().anyMatch(n -> n.getStatus() == NodeStatus.FAILED);
    }

    /**
     * 判断工作流是否正在运行。
     */
    public boolean isRunning() {
        return status == WorkflowStatus.ACTIVE && !isAllDone() && !hasFailed();
    }

    /**
     * 添加节点。
     */
    public void addNode(WorkflowNode node) {
        nodes.add(node);
    }

    /**
     * 移除节点。
     */
    public void removeNode(String nodeId) {
        nodes.removeIf(n -> n.getId().equals(nodeId));
        edges.removeIf(e -> e.getFrom().equals(nodeId) || e.getTo().equals(nodeId));
    }

    /**
     * 添加边。
     */
    public void addEdge(WorkflowEdge edge) {
        edges.add(edge);
    }

    /**
     * 移除边。
     */
    public void removeEdge(String from, String to) {
        edges.removeIf(e -> e.getFrom().equals(from) && e.getTo().equals(to));
    }

    /**
     * 生成节点ID。
     */
    public String generateNodeId() {
        int maxId = nodes.stream()
                .map(n -> {
                    try {
                        return Integer.parseInt(n.getId().substring(1));
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .max(Integer::compareTo)
                .orElse(0);
        return "n" + (maxId + 1);
    }

    /**
     * 生成边ID。
     */
    public String generateEdgeId() {
        int maxId = edges.stream()
                .map(e -> {
                    try {
                        return Integer.parseInt(e.getId().substring(1));
                    } catch (Exception ex) {
                        return 0;
                    }
                })
                .max(Integer::compareTo)
                .orElse(0);
        return "e" + (maxId + 1);
    }
}