package site.sorghum.agent4j.bin.workflow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.bin.command.ChatCommandContext;
import site.sorghum.agent4j.tool.LogLevel;

import java.time.Instant;
import java.util.List;

/**
 * WorkflowEngine — 工作流执行引擎。
 * <p>
 * 负责工作流状态查询、暂停/恢复、节点执行等操作。
 * 工作流创建由 {@link site.sorghum.agent4j.bin.builtin.WorkflowCreateDagTool} 工具完成。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowEngine {

    /**
     * 获取当前会话的工作流。
     */
    public Workflow getCurrentWorkflow(ChatCommandContext ctx) {
        try {
            String sessionId = ctx.getAgent().getSessionStore().currentName();
            if (sessionId == null) return null;
            WorkflowStore store = ctx.getAgent().getWorkspaceManager().getWorkflowStore();
            return store.findBySession(sessionId);
        } catch (Exception e) {
            log.warn("[workflow] 获取当前工作流失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 暂停工作流。
     */
    public void pause(Workflow workflow, ChatCommandContext ctx) {
        try {
            workflow.setStatus(WorkflowStatus.PAUSED);
            workflow.setUpdatedAt(Instant.now());
            ctx.getAgent().getWorkspaceManager().getWorkflowStore().save(workflow);
        } catch (Exception e) {
            log.warn("[workflow] 暂停工作流失败: {}", e.getMessage());
        }
    }

    /**
     * 恢复工作流。
     */
    public void resume(Workflow workflow, ChatCommandContext ctx) {
        try {
            workflow.setStatus(WorkflowStatus.ACTIVE);
            workflow.setUpdatedAt(Instant.now());
            ctx.getAgent().getWorkspaceManager().getWorkflowStore().save(workflow);
        } catch (Exception e) {
            log.warn("[workflow] 恢复工作流失败: {}", e.getMessage());
        }
    }

    /**
     * 标记某节点已完成。
     */
    public void markNodeDone(Workflow workflow, String nodeId, String result, ChatCommandContext ctx) {
        try {
            WorkflowNode node = workflow.findNode(nodeId);
            if (node == null) return;
            node.setStatus(NodeStatus.DONE);
            node.setResult(result);
            node.setCompletedAt(Instant.now());
            workflow.setUpdatedAt(Instant.now());

            if (workflow.isAllDone()) {
                workflow.setStatus(WorkflowStatus.COMPLETED);
                workflow.setCompletedAt(Instant.now());
            }

            ctx.getAgent().getWorkspaceManager().getWorkflowStore().save(workflow);
        } catch (Exception e) {
            log.warn("[workflow] 标记节点完成失败: {}", e.getMessage());
        }
    }

    /**
     * 标记某节点失败。
     */
    public void markNodeFailed(Workflow workflow, String nodeId, String error, ChatCommandContext ctx) {
        try {
            WorkflowNode node = workflow.findNode(nodeId);
            if (node == null) return;
            node.setStatus(NodeStatus.FAILED);
            node.setLastError(error);
            node.setRetryCount(node.getRetryCount() + 1);
            workflow.setUpdatedAt(Instant.now());

            if (workflow.hasFailed()) {
                workflow.setStatus(WorkflowStatus.FAILED);
            }

            ctx.getAgent().getWorkspaceManager().getWorkflowStore().save(workflow);
        } catch (Exception e) {
            log.warn("[workflow] 标记节点失败失败: {}", e.getMessage());
        }
    }

    /**
     * 重试某节点。
     */
    public void retryNode(Workflow workflow, String nodeId, ChatCommandContext ctx) {
        try {
            WorkflowNode node = workflow.findNode(nodeId);
            if (node == null) return;
            node.setStatus(NodeStatus.PENDING);
            node.setRetryCount(0);
            node.setLastError(null);
            node.setCompletedAt(null);
            workflow.setUpdatedAt(Instant.now());
            ctx.getAgent().getWorkspaceManager().getWorkflowStore().save(workflow);
        } catch (Exception e) {
            log.warn("[workflow] 重试节点失败: {}", e.getMessage());
        }
    }

    /**
     * 跳过某节点。
     */
    public void skipNode(Workflow workflow, String nodeId, ChatCommandContext ctx) {
        try {
            WorkflowNode node = workflow.findNode(nodeId);
            if (node == null) return;
            node.setStatus(NodeStatus.SKIPPED);
            node.setCompletedAt(Instant.now());
            workflow.setUpdatedAt(Instant.now());

            if (workflow.isAllDone()) {
                workflow.setStatus(WorkflowStatus.COMPLETED);
                workflow.setCompletedAt(Instant.now());
            }

            ctx.getAgent().getWorkspaceManager().getWorkflowStore().save(workflow);
        } catch (Exception e) {
            log.warn("[workflow] 跳过节点失败: {}", e.getMessage());
        }
    }

    /**
     * 添加节点。
     */
    public void addNode(Workflow workflow, String description, NodeType type, ChatCommandContext ctx) {
        try {
            String nodeId = workflow.generateNodeId();
            WorkflowNode node = WorkflowNode.builder()
                    .id(nodeId)
                    .description(description)
                    .type(type)
                    .status(NodeStatus.PENDING)
                    .retryCount(0)
                    .build();
            workflow.addNode(node);
            workflow.setUpdatedAt(Instant.now());
            ctx.getAgent().getWorkspaceManager().getWorkflowStore().save(workflow);
        } catch (Exception e) {
            log.warn("[workflow] 添加节点失败: {}", e.getMessage());
        }
    }

    /**
     * 移除节点。
     */
    public void removeNode(Workflow workflow, String nodeId, ChatCommandContext ctx) {
        try {
            workflow.removeNode(nodeId);
            workflow.setUpdatedAt(Instant.now());
            ctx.getAgent().getWorkspaceManager().getWorkflowStore().save(workflow);
        } catch (Exception e) {
            log.warn("[workflow] 移除节点失败: {}", e.getMessage());
        }
    }

    /**
     * 添加边。
     */
    public void addEdge(Workflow workflow, String from, String to, EdgeType type, String condition, ChatCommandContext ctx) {
        try {
            String edgeId = workflow.generateEdgeId();
            WorkflowEdge edge = WorkflowEdge.builder()
                    .id(edgeId)
                    .from(from)
                    .to(to)
                    .type(type)
                    .condition(condition)
                    .build();
            workflow.addEdge(edge);
            workflow.setUpdatedAt(Instant.now());
            ctx.getAgent().getWorkspaceManager().getWorkflowStore().save(workflow);
        } catch (Exception e) {
            log.warn("[workflow] 添加边失败: {}", e.getMessage());
        }
    }

    /**
     * 移除边。
     */
    public void removeEdge(Workflow workflow, String from, String to, ChatCommandContext ctx) {
        try {
            workflow.removeEdge(from, to);
            workflow.setUpdatedAt(Instant.now());
            ctx.getAgent().getWorkspaceManager().getWorkflowStore().save(workflow);
        } catch (Exception e) {
            log.warn("[workflow] 移除边失败: {}", e.getMessage());
        }
    }

    /**
     * 获取下一个要执行的节点。
     */
    public WorkflowNode getNextNode(Workflow workflow) {
        List<WorkflowNode> readyNodes = workflow.getReadyNodes();
        if (readyNodes.isEmpty()) {
            return null;
        }
        // 返回第一个就绪节点
        return readyNodes.get(0);
    }

    /**
     * 执行工作流。
     */
    public void executeWorkflow(Workflow workflow, ChatCommandContext ctx) {
        try {
            workflow.setStatus(WorkflowStatus.ACTIVE);
            workflow.setUpdatedAt(Instant.now());
            ctx.getAgent().getWorkspaceManager().getWorkflowStore().save(workflow);

            // 生成工作流结构文本
            String structureText = generateStructureText(workflow);

            String prompt = """
                    当前会话已设定工作流：「%s」
                    
                    工作流结构：
                    %s
                    
                    请按照工作流结构执行节点。
                    每完成一个节点，**必须**调用 workflow_mark_node 工具通知系统，参数传入节点ID。
                    如果有节点失败，系统会自动重试（最多 %d 次）。
                    全部完成后总结汇报。
                    """
                    .formatted(workflow.getTitle(), structureText, workflow.getMaxRetries());

            ctx.getAgent().getOutput().onLog(LogLevel.INFO, prompt);
        } catch (Exception e) {
            log.error("[workflow] 执行工作流失败: {}", e.getMessage());
            ctx.getAgent().getOutput().onLog(LogLevel.ERROR,
                    "❌ 工作流执行失败: " + e.getMessage());
        }
    }

    /**
     * 生成工作流结构文本。
     */
    private String generateStructureText(Workflow workflow) {
        StringBuilder sb = new StringBuilder();
        sb.append("节点列表:\n");
        for (WorkflowNode node : workflow.getNodes()) {
            sb.append("  ").append(node.getId()).append(". ").append(node.getDescription())
                    .append(" (").append(node.getType()).append(")\n");
        }
        sb.append("\n依赖关系:\n");
        for (WorkflowEdge edge : workflow.getEdges()) {
            sb.append("  ").append(edge.getFrom()).append(" -> ").append(edge.getTo());
            if (edge.getCondition() != null) {
                sb.append(" [条件: ").append(edge.getCondition()).append("]");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
