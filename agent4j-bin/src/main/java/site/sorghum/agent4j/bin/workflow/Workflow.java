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
     * 查找从 from 到 to 的边。
     */
    public WorkflowEdge findEdge(String from, String to) {
        return edges.stream()
                .filter(e -> e.getFrom().equals(from) && e.getTo().equals(to))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取所有就绪节点（所有依赖已满足）。
     * <p>
     * 当前驱是 CONDITION 节点时，只激活 conditionResult 匹配的那个分支：
     * 只有目标节点ID等于 conditionResult 的条件边才会使后继就绪，
     * 其余条件边的目标节点不会被激活（应由调用方主动 skipBranch）。
     * 非条件边（NORMAL/DEFAULT）不受影响。
     * 天然支持任意数量分支的单选语义。
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
                    // 过滤掉 LOOP_BACK 边的前驱（循环回边不算在依赖条件内）
                    // 对于非 LOOP 节点，getPredecessorIds 不会返回 LOOP_BACK 边，过滤无副作用
                    List<String> forwardPreds = predecessors.stream()
                            .filter(predId -> {
                                WorkflowEdge edge = findEdge(predId, node.getId());
                                return edge == null || edge.getType() != EdgeType.LOOP_BACK;
                            })
                            .collect(Collectors.toList());
                    // 所有前驱节点必须满足条件
                    return forwardPreds.stream().allMatch(predId -> {
                        WorkflowNode pred = findNode(predId);
                        if (pred == null) {
                            return false;
                        }
                        // 前驱节点必须已完成
                        if (!pred.isCompleted()) {
                            return false;
                        }
                        // 如果前驱是 CONDITION 节点，检查是否选中了当前节点
                        if (pred.getType() == NodeType.CONDITION) {
                            WorkflowEdge edge = findEdge(predId, node.getId());
                            if (edge == null) {
                                return false;
                            }
                            // 非条件边（NORMAL/DEFAULT）直接通过
                            if (!edge.isConditional()) {
                                return true;
                            }
                            // 条件边：只有目标节点ID匹配 conditionResult 才就绪
                            // 支持任意数量分支（2个、3个、5个...）的单选语义
                            String conditionResult = pred.getConditionResult();
                            return node.getId().equals(conditionResult);
                        }
                        return true;
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
     * 递归跳过未选分支的整个子树。
     * <p>
     * 从 startNodeId 开始，只跳过那些<strong>所有前驱路径都来自已跳过节点或 CONDITION 节点</strong>的后继。
     * 如果某个后继还有来自其他活跃分支的前驱，则保留不跳过——这确保了分支汇合点不会被误跳过。
     * </p>
     *
     * @param startNodeId     开始跳过的节点ID
     * @param conditionNodeId 对应的 CONDITION 节点ID（跳过时以此为界）
     */
    public void skipBranch(String startNodeId, String conditionNodeId) {
        WorkflowNode node = findNode(startNodeId);
        if (node == null || node.isCompleted()) {
            return;
        }
        node.setStatus(NodeStatus.SKIPPED);

        List<String> successors = getSuccessorIds(startNodeId);
        for (String succId : successors) {
            // 仅当该后继的 ALL 前驱都来自 CONDITION 节点或已跳过节点时，才继续递归跳过
            boolean canSkip = getPredecessorIds(succId).stream().allMatch(predId -> {
                if (predId.equals(conditionNodeId)) return true;        // 从 CONDITION 出发的边
                WorkflowNode pred = findNode(predId);
                return pred != null && pred.getStatus() == NodeStatus.SKIPPED;
            });
            if (canSkip) {
                skipBranch(succId, conditionNodeId);
            }
        }
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
     * 重置循环体节点，为下一次迭代做准备。
     * <p>
     * 将 loopTarget 到 LOOP 节点之间通过 LOOP_BACK 边连接的所有节点重置为 PENDING，
     * 清除上次迭代的结果和完成时间。
     * </p>
     *
     * @param loopNodeId LOOP 节点ID
     */
    public void resetLoopBody(String loopNodeId) {
        WorkflowNode loopNode = findNode(loopNodeId);
        if (loopNode == null || loopNode.getType() != NodeType.LOOP) {
            return;
        }
        String loopTarget = loopNode.getLoopTarget();
        if (loopTarget == null) {
            return;
        }

        // 收集循环体节点：从 loopTarget 出发，沿边遍历到 LOOP 节点
        java.util.Set<String> bodyNodeIds = new java.util.HashSet<>();
        collectLoopBodyNodes(loopTarget, loopNodeId, bodyNodeIds);

        // 重置循环体节点
        for (String bodyNodeId : bodyNodeIds) {
            WorkflowNode bodyNode = findNode(bodyNodeId);
            if (bodyNode != null && bodyNodeId.equals(loopNodeId)) continue;
            if (bodyNode != null && bodyNode.isCompleted()) {
                bodyNode.setStatus(NodeStatus.PENDING);
                bodyNode.setResult(null);
                bodyNode.setCompletedAt(null);
            }
        }

        // 重置 LOOP 节点本身，使其回到可被 getReadyNodes 拾取的状态
        loopNode.setStatus(NodeStatus.PENDING);
    }

    /**
     * 递归收集循环体内的节点ID。
     */
    private void collectLoopBodyNodes(String currentId, String loopNodeId, java.util.Set<String> visited) {
        if (visited.contains(currentId) || currentId.equals(loopNodeId)) {
            return;
        }
        visited.add(currentId);
        for (String succId : getSuccessorIds(currentId)) {
            collectLoopBodyNodes(succId, loopNodeId, visited);
        }
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