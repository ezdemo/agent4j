package site.sorghum.agent4j.bin.builtin;

import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.ai.chat.tool.AbsToolProvider;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Param;
import site.sorghum.agent4j.bin.workflow.*;
import site.sorghum.agent4j.bin.workspace.WorkspaceManager;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.solon.SolonToTools;

import java.util.Collection;

/**
 * Workflow Visualization 工具 —— 可视化显示工作流结构。
 * <p>
 * 提供工作流的文本表示，包括节点列表、依赖关系和执行状态。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class WorkflowVisualizationTool extends AbsToolProvider implements SolonToTools {

    @ToolMapping(name = "workflow_visualize", description = """
                可视化显示当前会话的工作流结构。
                返回工作流的节点列表、依赖关系和执行状态。
                """)
    public String workflowVisualize(@Param(name = "sessionId", description = "会话 ID。留空自动从上下文获取当前会话", required = false) String sessionId,
                                   ToolContext ctx) {
        // 获取 sessionId
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = ctx.getSessionId();
        }
        if (sessionId == null || sessionId.isBlank()) {
            return "SESSION_MISSING: 无法获取会话 ID，请确保已在会话中";
        }

        try {
            WorkflowStore workflowStore = WorkspaceManager.getOrCreate(ctx.getRootDir().toAbsolutePath().toString()).getWorkflowStore();
            Workflow workflow = workflowStore.findBySession(sessionId);
            if (workflow == null) {
                return "WORKFLOW_NOT_FOUND: 当前会话没有活跃工作流。请先使用 /workflow create 创建工作流。";
            }

            return generateVisualization(workflow);

        } catch (IllegalStateException e) {
            return "WORKSPACE_NOT_INITIALIZED: 工作区未初始化，无法获取工作流：" + e.getMessage();
        } catch (Exception e) {
            return "VISUALIZATION_FAILED: 生成工作流可视化失败：" + e.getMessage();
        }
    }

    /**
     * 生成工作流的文本可视化表示。
     */
    private String generateVisualization(Workflow workflow) {
        StringBuilder sb = new StringBuilder();
        
        // 标题和状态
        sb.append("┌─────────────────────────────────────────────────────────────┐\n");
        sb.append("│ 工作流: ").append(workflow.getTitle()).append("\n");
        sb.append("│ 状态: ").append(formatStatusBadge(workflow.getStatus())).append("\n");
        sb.append("│ 进度: ").append(workflow.progressText()).append("\n");
        sb.append("└─────────────────────────────────────────────────────────────┘\n\n");

        // 节点列表
        sb.append("节点列表:\n");
        sb.append("────────────────────────────────────────────────────────────────\n");
        for (WorkflowNode node : workflow.getNodes()) {
            String icon = getNodeIcon(node.getStatus());
            String typeStr = String.format("%-10s", node.getType());
            sb.append(icon).append(" ").append(node.getId()).append(". ")
                    .append(node.getDescription()).append(" (").append(typeStr).append(")");
            if (node.getRetryCount() > 0) {
                sb.append(" [retry:").append(node.getRetryCount()).append("]");
            }
            sb.append("\n");
            if (node.getLastError() != null && !node.getLastError().isEmpty()) {
                sb.append("   └─ ").append(node.getLastError()).append("\n");
            }
            if (node.getResult() != null && !node.getResult().isEmpty()) {
                sb.append("   └─ 结果: ").append(node.getResult()).append("\n");
            }
        }

        // 依赖关系
        sb.append("\n依赖关系:\n");
        sb.append("────────────────────────────────────────────────────────────────\n");
        for (WorkflowEdge edge : workflow.getEdges()) {
            sb.append("  ").append(edge.getFrom()).append(" → ").append(edge.getTo());
            if (edge.getCondition() != null) {
                sb.append(" [条件: ").append(edge.getCondition()).append("]");
            }
            sb.append("\n");
        }

        // 执行路径（简化版）
        sb.append("\n执行路径:\n");
        sb.append("────────────────────────────────────────────────────────────────\n");
        String executionPath = generateExecutionPath(workflow);
        sb.append(executionPath);

        return sb.toString();
    }

    /**
     * 生成执行路径的文本表示。
     */
    private String generateExecutionPath(Workflow workflow) {
        StringBuilder sb = new StringBuilder();
        
        // 找到开始节点
        WorkflowNode startNode = workflow.findNode("start");
        if (startNode == null) {
            return "  [无开始节点]\n";
        }

        // 从开始节点遍历
        sb.append("  ").append(startNode.getId());
        appendSuccessors(workflow, startNode.getId(), sb, "  ");
        sb.append("\n");

        return sb.toString();
    }

    /**
     * 递归添加后继节点。
     */
    private void appendSuccessors(Workflow workflow, String nodeId, StringBuilder sb, String indent) {
        java.util.List<String> successors = workflow.getSuccessorIds(nodeId);
        if (successors.isEmpty()) {
            return;
        }

        for (int i = 0; i < successors.size(); i++) {
            String successorId = successors.get(i);
            WorkflowNode successor = workflow.findNode(successorId);
            if (successor == null) continue;

            sb.append(" → ").append(successor.getId());
            
            // 如果有多个后继，添加分支标记
            if (successors.size() > 1) {
                if (i == 0) {
                    sb.append(" [分支]");
                }
            }

            // 递归处理后继节点
            appendSuccessors(workflow, successorId, sb, indent + "  ");
        }
    }

    /**
     * 获取节点状态图标。
     */
    private String getNodeIcon(NodeStatus status) {
        return switch (status) {
            case DONE -> "✅";
            case RUNNING -> "🔵";
            case FAILED -> "❌";
            case SKIPPED -> "⏭️";
            case WAITING -> "⏸️";
            case BLOCKED -> "🔒";
            case READY -> "🟢";
            case PENDING -> "⬜";
        };
    }

    /**
     * 格式化状态徽章。
     */
    private String formatStatusBadge(WorkflowStatus status) {
        return switch (status) {
            case DRAFT -> "📝 草稿";
            case ACTIVE -> "🟢 进行中";
            case PAUSED -> "🟡 已暂停";
            case COMPLETED -> "✅ 已完成";
            case FAILED -> "🔴 失败";
        };
    }

    @Override
    public Collection<FunctionTool> getSolonTools() {
        return this.getTools();
    }

    @Override
    public String getSystemPrompt() {
        return """
                可视化显示当前会话的工作流结构。
                返回工作流的节点列表、依赖关系和执行状态。
                """;
    }
}