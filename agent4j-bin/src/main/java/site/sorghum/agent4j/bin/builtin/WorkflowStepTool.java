package site.sorghum.agent4j.bin.builtin;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.ai.chat.tool.AbsToolProvider;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Param;
import site.sorghum.agent4j.bin.workflow2.SimpleWorkflow;
import site.sorghum.agent4j.bin.workflow2.SimpleWorkflowEngine;
import site.sorghum.agent4j.bin.workspace.WorkspaceManager;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.solon.SolonToTools;

import java.util.Collection;
import java.util.List;

/**
 * Workflow Step 工具 —— 标记当前步骤完成/失败/跳过。
 * <p>
 * 替代旧的 workflow_mark_node。
 * 自动推进到下一步，无需指定节点 ID。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
@Component
public class WorkflowStepTool extends AbsToolProvider implements SolonToTools {

    @ToolMapping(name = "workflow_step", description = """
            标记工作流的当前步骤完成/失败/跳过，自动推进到下一步。
            
            使用场景：
            - 完成一个步骤后调用 action="done"
            - 步骤执行失败时调用 action="fail"
            - 需要跳过当前步骤时调用 action="skip"
            
            注意：不需要指定步骤 ID，引擎自动管理当前步骤索引。
            """)
    public String workflowStep(
            @Param(name = "action", description = "操作类型：done（完成）| fail（失败）| skip（跳过）") String action,
            @Param(name = "result", description = "执行结果摘要（支持 Markdown）", required = false) String result,
            ToolContext ctx) {
        if (action == null || action.isBlank()) {
            return "PARAM_MISSING: 缺少必填参数 'action'";
        }

        String sessionId = ctx.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return "SESSION_MISSING: 无法获取会话 ID";
        }

        try {
            String rootDir = ctx.getRootDir().toAbsolutePath().toString();
            WorkspaceManager workspaceManager = WorkspaceManager.getOrCreate(rootDir);

            // 加载工作流
            SimpleWorkflow wf = workspaceManager.getWorkflowStore2().findBySession(sessionId);
            if (wf == null) {
                return "WORKFLOW_NOT_FOUND: 当前会话没有活跃工作流。请先使用 workflow_start 创建工作流。";
            }

            if (!"ACTIVE".equals(wf.getStatus())) {
                return "WORKFLOW_NOT_ACTIVE: 工作流状态为 " + wf.getStatus() + "，仅 ACTIVE 状态可标记步骤。";
            }

            SimpleWorkflowEngine engine = new SimpleWorkflowEngine();
            SimpleWorkflowEngine.MarkResult markResult;

            switch (action.toLowerCase()) {
                case "done" -> markResult = engine.markCurrentDone(wf, result);
                case "fail" -> markResult = engine.markCurrentFailed(wf, result);
                case "skip" -> markResult = engine.skipCurrent(wf, result);
                default -> {
                    return "INVALID_ACTION: action 必须是 done / fail / skip 之一";
                }
            }

            if (markResult.isError()) {
                return "STEP_ERROR: " + markResult.getMessage();
            }

            // 持久化
            workspaceManager.getWorkflowStore2().save(wf);

            log.info("[workflow] 步骤状态更新: action={}, result={}", action, markResult.getType());

            // 构建响应
            var resp = new org.noear.snack4.ONode();
            resp.set("action", action);
            resp.set("result", markResult.getMessage());
            resp.set("status", wf.getStatus());
            resp.set("currentStepIndex", wf.getCurrentStepIndex());
            resp.set("totalSteps", wf.getSteps().size());
            resp.set("progress", wf.progressText());

            var current = wf.currentStep();
            if (current != null) {
                resp.set("currentStep", current.getId() + ": " + current.getDescription());
            }

            if (markResult.isCompleted()) {
                resp.set("completed", true);
            }

            return resp.toJson();

        } catch (Exception e) {
            log.error("[workflow] 标记步骤失败", e);
            return "STEP_FAILED: " + e.getMessage();
        }
    }

    @Override
    public Collection<FunctionTool> getSolonTools() {
        return this.getTools();
    }
}
