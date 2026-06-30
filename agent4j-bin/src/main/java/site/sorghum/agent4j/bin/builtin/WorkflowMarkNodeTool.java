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
import java.util.List;

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
                """)
    public String workflowMarkNode(@Param(name = "nodeId", description = "已完成的节点ID（如 'n1', 'n2'）") String nodeId,
                                   @Param(name = "result", description = "该节点的执行结果摘要，记录在工作流中供后续查阅", required = false) String result,
                                   @Param(name = "conditionResult", description = "CONDITION 节点专用：选中的分支节点ID（如 'n4'），引擎会自动跳过其他分支", required = false) String conditionResult,
                                   @Param(name = "loopResult", description = "LOOP 节点专用：'continue' 继续下一轮循环 或 'break' 退出循环", required = false) String loopResult,
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

            // 如果是 CONDITION 节点且提供了 conditionResult，处理分支跳过逻辑
            // conditionResult 应为选中的目标节点ID（如 "n4"），引擎会跳过其他所有分支
            if (node.getType() == NodeType.CONDITION && conditionResult != null && !conditionResult.isBlank()) {
                // 存储条件结果到节点
                node.setConditionResult(conditionResult);

                // 查找所有从该条件节点出发的后继节点，跳过不匹配的分支
                List<String> successors = workflow.getSuccessorIds(nodeId);
                for (String succId : successors) {
                    if (!succId.equals(conditionResult)) {
                        // 未选中的分支：递归跳过整条子树（智能跳过，不影响汇合节点）
                        workflow.skipBranch(succId, nodeId);
                    }
                }
            }

            // 如果是 LOOP 节点且提供了 loopResult，处理循环逻辑
            if (node.getType() == NodeType.LOOP && loopResult != null && !loopResult.isBlank()) {
                if ("continue".equalsIgnoreCase(loopResult.trim())) {
                    // 继续循环：重置循环体节点，递增迭代计数
                    int newIteration = node.getIterationCount() + 1;
                    if (newIteration >= node.getMaxIterations()) {
                        // 达到最大迭代次数，强制退出
                        node.setIterationCount(newIteration);
                        workflowStore.save(workflow);
                        return "\u26a0\ufe0f 节点 " + nodeId + " 已达最大迭代次数（" + node.getMaxIterations() + " 轮），强制退出循环。\n当前进度：" + workflow.progressText();
                    }
                    node.setIterationCount(newIteration);
                    // 重置循环体节点为下一次迭代
                    workflow.resetLoopBody(nodeId);
                    workflowStore.save(workflow);
                    return "\ud83d\udd04 节点 " + nodeId + " 继续循环（第 " + newIteration + "/" + node.getMaxIterations() + " 轮）。\n循环体节点已重置，请继续执行。";
                } else if ("break".equalsIgnoreCase(loopResult.trim())) {
                    // 退出循环：标记 LOOP 为完成，迭代计数 +1
                    node.setIterationCount(node.getIterationCount() + 1);
                } else {
                    node.setStatus(NodeStatus.PENDING); // 回滚状态
                    return "PARAM_INVALID: loopResult 必须为 'continue' 或 'break'，收到: " + loopResult;
                }
            }

            // 自动完成 END 节点：如果所有前驱节点都已完成，则 END 节点自动标记为完成
            for (WorkflowNode wn : workflow.getNodes()) {
                if (wn.getType() == NodeType.END && wn.getStatus() != NodeStatus.DONE) {
                    List<String> preds = workflow.getPredecessorIds(wn.getId());
                    if (!preds.isEmpty() && preds.stream().allMatch(predId -> {
                        WorkflowNode pred = workflow.findNode(predId);
                        return pred != null && pred.getStatus() == NodeStatus.DONE;
                    })) {
                        wn.setStatus(NodeStatus.DONE);
                        wn.setCompletedAt(Instant.now());
                    }
                }
            }

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
                ## workflow_mark_node
                
                每完成一个节点后调用此工具，参数传入节点ID。
                如果所有节点都已完成，工作流会自动标记为已完成。
                注意：对于 CONDITION 类型节点，必须传入 conditionResult 参数（值为选中的目标节点ID，如 "n4"），
                系统会自动跳过其他未选中的分支。
                注意：对于 LOOP 类型节点，必须传入 loopResult 参数（"continue" 继续循环 或 "break" 退出循环）。
                """;
    }
}
