package site.sorghum.agent4j.bin.builtin;

import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.ai.chat.tool.AbsToolProvider;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Param;
import site.sorghum.agent4j.bin.goal.*;
import site.sorghum.agent4j.bin.workspace.WorkspaceManager;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.solon.SolonToTools;

import java.time.Instant;
import java.util.Collection;

/**
 * Goal Mark Step 工具 —— 标记目标中的某一步为已完成。
 * <p>
 * LLM 在执行目标步骤时，每完成一步应调用此工具告知系统，
 * 系统会自动检查是否所有步骤完成并更新目标整体状态。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class GoalMarkDoneTool extends AbsToolProvider implements SolonToTools {

    @ToolMapping(name = "goal_mark_step", description = """
                标记当前会话目标中的某一步为"已完成"。
                每完成一个步骤后调用此工具，参数传入步骤序号（从 1 开始计数）。
                如果所有步骤都已完成，目标会自动标记为已完成。
                """)
    public String goalMarkStep(@Param(name = "stepIndex", description = "已完成的步骤序号，从 1 开始计数（第一步为 1，第二步为 2，依此类推)") Integer stepIndex,
                               @Param(name = "output", description = "该步骤的执行结果摘要，记录在目标中供后续查阅", required = false) String output,
                               @Param(name = "sessionId", description = "会话 ID。留空自动从上下文获取当前会话", required = false) String sessionId,
                               ToolContext ctx) {
        // 校验 stepIndex
        if (stepIndex == null) {
            return "PARAM_MISSING: 缺少必填参数 'stepIndex'，请传入已完成的步骤序号（从 1 开始）";
        }

        // 获取 sessionId
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = ctx.getSessionId();
        }
        if (sessionId == null || sessionId.isBlank()) {
            return "SESSION_MISSING: 无法获取会话 ID，请确保已在会话中";
        }

        try {
            GoalStore goalStore = WorkspaceManager.getOrCreate(ctx.getRootDir().toAbsolutePath().toString()).getGoalStore();
            Goal goal = goalStore.findBySession(sessionId);
            if (goal == null) {
                return "GOAL_NOT_FOUND: 当前会话没有活跃目标。请先使用 /goal set 创建目标。";
            }

            // stepIndex 从 1 开始计数，转为 0-based
            int idx = stepIndex - 1;
            if (idx < 0 || idx >= goal.getSteps().size()) {
                return "INVALID_STEP_INDEX: 步骤序号无效。当前目标共有 " + goal.getSteps().size()
                        + " 步，传入的 stepIndex=" + stepIndex + " 超出范围（1-"
                        + goal.getSteps().size() + ")。";
            }

            GoalStep step = goal.getSteps().get(idx);
            if (step.getStatus() == StepStatus.DONE) {
                return "步骤 " + stepIndex + " 之前已标记为完成，无需重复操作。";
            }

            // 标记为完成
            step.setStatus(StepStatus.DONE);
            step.setCompletedAt(Instant.now());
            if (output != null) {
                step.setLastError(output);
            }
            goal.setUpdatedAt(Instant.now());

            // 检查所有步骤是否都已完成
            if (goal.isAllDone()) {
                goal.setStatus(GoalStatus.COMPLETED);
                goal.setCompletedAt(Instant.now());
            }

            goalStore.save(goal);

            // 获取下一步提示
            String nextStepHint = "";
            for (GoalStep s : goal.getSteps()) {
                if (s.getStatus() == StepStatus.PENDING) {
                    nextStepHint = "\n\n下一步：" + (s.getIndex() + 1) + ". " + s.getDescription();
                    break;
                }
            }

            String statusMsg;
            if (goal.getStatus() == GoalStatus.COMPLETED) {
                statusMsg = "🎉 恭喜！目标全部完成！" + goal.getTitle();
            } else {
                statusMsg = "✅ 步骤 " + stepIndex + " 已标记为完成。"
                        + "当前进度：" + goal.progressText()
                        + nextStepHint;
            }

            return statusMsg;

        } catch (IllegalStateException e) {
            return "WORKSPACE_NOT_INITIALIZED: 工作区未初始化，无法更新目标状态：" + e.getMessage();
        } catch (Exception e) {
            return "UPDATE_FAILED: 标记步骤完成失败：" + e.getMessage();
        }
    }

    @Override
    public Collection<FunctionTool> getSolonTools() {
        return this.getTools();
    }

    @Override
    public String getSystemPrompt() {
        return """
                标记当前会话目标中的某一步为"已完成"。
                每完成一个步骤后调用此工具，参数传入步骤序号（从 1 开始计数）。
                如果所有步骤都已完成，目标会自动标记为已完成。
                """;
    }
}