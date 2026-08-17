package site.sorghum.loopra.bin.builtin;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.ai.chat.tool.AbsToolProvider;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Param;
import site.sorghum.loopra.bin.checklist.Checklist;
import site.sorghum.loopra.bin.checklist.ChecklistEngine;
import site.sorghum.loopra.bin.project.ProjectRegistry;
import site.sorghum.loopra.tool.SolonToTools;
import site.sorghum.loopra.tool.ToolContext;

import java.util.Collection;

/**
 * Checklist Step 工具 —— 标记当前步骤完成/失败/跳过。
 * <p>
 * 自动推进到下一步，无需指定步骤 ID。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
@Component
public class ChecklistStepTool extends AbsToolProvider implements SolonToTools {

    @ToolMapping(name = "checklist_step", description = """
            标记清单的当前步骤完成/失败/跳过，自动推进到下一步。
            
            使用场景：
            - 完成一个步骤后调用 action="done"
            - 步骤执行失败时调用 action="fail"
            - 需要跳过当前步骤时调用 action="skip"
            
            注意：不需要指定步骤 ID，引擎自动管理当前步骤索引。
            """)
    public String checklistStep(
            @Param(name = "action", description = "操作类型：done（完成）| fail（失败）| skip（跳过）") String action,
            @Param(name = "result", description = "执行结果摘要（支持 Markdown）", required = false) String result,
            @Param(name = "ctx", required = false) ToolContext ctx) {
        if (action == null || action.isBlank()) {
            return "PARAM_MISSING: 缺少必填参数 'action'";
        }

        String sessionId = ctx.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return "SESSION_MISSING: 无法获取会话 ID";
        }

        try {
            String rootDir = ctx.getStateRootDir().toAbsolutePath().toString();
            ProjectRegistry projectRegistry = ProjectRegistry.getOrCreate(rootDir);

            // 加载清单
            Checklist cl = projectRegistry.getChecklistStore().findBySession(sessionId);
            if (cl == null) {
                return "CHECKLIST_NOT_FOUND: 当前会话没有活跃清单。请先使用 checklist_start 创建清单。";
            }

            if (!"ACTIVE".equals(cl.getStatus())) {
                return "CHECKLIST_NOT_ACTIVE: 清单状态为 " + cl.getStatus() + "，仅 ACTIVE 状态可标记步骤。";
            }

            ChecklistEngine engine = new ChecklistEngine();
            ChecklistEngine.MarkResult markResult;

            switch (action.toLowerCase()) {
                case "done" -> markResult = engine.markCurrentDone(cl, result);
                case "fail" -> markResult = engine.markCurrentFailed(cl, result);
                case "skip" -> markResult = engine.skipCurrent(cl, result);
                default -> {
                    return "INVALID_ACTION: action 必须是 done / fail / skip 之一";
                }
            }

            if (markResult.isError()) {
                return "STEP_ERROR: " + markResult.getMessage();
            }

            // 持久化
            projectRegistry.getChecklistStore().save(cl);

            log.info("[checklist] 步骤状态更新: action={}, result={}", action, markResult.getType());

            // 构建响应
            var resp = new org.noear.snack4.ONode();
            resp.set("action", action);
            resp.set("result", markResult.getMessage());
            resp.set("status", cl.getStatus());
            resp.set("currentStepIndex", cl.getCurrentStepIndex());
            resp.set("totalSteps", cl.getSteps().size());
            resp.set("progress", cl.progressText());

            var current = cl.currentStep();
            if (current != null) {
                resp.set("currentStep", current.getId() + ": " + current.getDescription());
            }

            if (markResult.isCompleted()) {
                resp.set("completed", true);
            }

            return resp.toJson();

        } catch (Exception e) {
            log.error("[checklist] 标记步骤失败", e);
            return "STEP_FAILED: " + e.getMessage();
        }
    }

    @Override
    public Collection<FunctionTool> getSolonTools() {
        return this.getTools();
    }
}
