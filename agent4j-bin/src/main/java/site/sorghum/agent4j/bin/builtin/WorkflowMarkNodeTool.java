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

import java.time.Instant;
import java.util.Collection;

/**
 * Workflow Mark Node 工具 —— 标记工作流中的某个节点为已完成。
 * <p>
 * LLM 在执行工作流节点时，每完成一个节点应调用此工具告知系统，
 * 系统会自动检查是否所有节点完成并更新工作流整体状态。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class WorkflowMarkNodeTool extends AbsToolProvider implements SolonToTools {

    @ToolMapping(name = "workflow_mark_node", description = """
                标记当前会话工作流中的某个节点为"已完成"。
                每完成一个节点后调用此工具，参数传入节点ID。
                如果所有节点都已完成，工作流会自动标记为已完成。
                """)
    public String workflowMarkNode(@Param(name = "nodeId", description = "已完成的节点ID（如 'n1', 'n2'）") String nodeId,
                                   @Param(name = "result", description = "该节点的执行结果摘要，记录在工作流中供后续查阅", required = false) String result,
                                   @Param(name = "sessionId", description = "会话 ID。留空自动从上下文获取当前会话", required = false) String sessionId,
                                   ToolContext ctx) {
        // 校验 nodeId
        if (nodeId == null || nodeId.isBlank()) {
            return "PARAM_MISSING: 缺少必填参数 'nodeId'，请传入已完成的节点ID";
        }

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

            // 查找节点
            WorkflowNode node = workflow.findNode(nodeId);
            if (node == null) {
                return "INVALID_NODE_ID: 节点ID无效。当前工作流没有ID为 '" + nodeId + "' 的节点。";
            }

            if (node.getStatus() == NodeStatus.DONE) {
                return "节点 " + nodeId + " 之前已标记为完成，无需重复操作。";
            }

            // 标记为完成
            node.setStatus(NodeStatus.DONE);
            node.setCompletedAt(Instant.now());
            if (result != null) {
                node.setResult(result);
            }
            workflow.setUpdatedAt(Instant.now());

            // 检查所有节点是否都已完成
            if (workflow.isAllDone()) {
                workflow.setStatus(WorkflowStatus.COMPLETED);
                workflow.setCompletedAt(Instant.now());
            }

            workflowStore.save(workflow);

            // 获取下一步提示
            String nextNodeHint = "";
            WorkflowNode nextNode = workflow.getReadyNodes().stream().findFirst().orElse(null);
            if (nextNode != null) {
                nextNodeHint = "\n\n下一步：" + nextNode.getId() + ". " + nextNode.getDescription();
            }

            String statusMsg;
            if (workflow.getStatus() == WorkflowStatus.COMPLETED) {
                statusMsg = "🎉 恭喜！工作流全部完成！" + workflow.getTitle();
            } else {
                statusMsg = "✅ 节点 " + nodeId + " 已标记为完成。"
                        + "当前进度：" + workflow.progressText()
                        + nextNodeHint;
            }

            return statusMsg;

        } catch (IllegalStateException e) {
            return "WORKSPACE_NOT_INITIALIZED: 工作区未初始化，无法更新工作流状态：" + e.getMessage();
        } catch (Exception e) {
            return "UPDATE_FAILED: 标记节点完成失败：" + e.getMessage();
        }
    }

    @Override
    public Collection<FunctionTool> getSolonTools() {
        return this.getTools();
    }

    @Override
    public String getSystemPrompt() {
        return """
                标记当前会话工作流中的某个节点为"已完成"。
                每完成一个节点后调用此工具，参数传入节点ID。
                如果所有节点都已完成，工作流会自动标记为已完成。
                """;
    }
}