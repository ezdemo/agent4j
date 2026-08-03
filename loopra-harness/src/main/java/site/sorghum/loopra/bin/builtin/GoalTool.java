package site.sorghum.loopra.bin.builtin;

import org.noear.snack4.ONode;
import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.ai.chat.tool.AbsToolProvider;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Param;
import site.sorghum.loopra.bin.goal.Goal;
import site.sorghum.loopra.bin.goal.GoalRuntime;
import site.sorghum.loopra.bin.goal.GoalService;
import site.sorghum.loopra.bin.goal.StepStatus;
import site.sorghum.loopra.tool.ToolContext;
import site.sorghum.loopra.tool.SolonToTools;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Goal 工具集。状态转换由 GoalService 校验，不能依靠模型自行描述完成状态。 */
@Component
public class GoalTool extends AbsToolProvider implements SolonToTools {
    private final GoalService goals = new GoalService();

    @ToolMapping(name = "goal_create", description = """
            为当前会话创建一个可恢复的 Goal。仅在用户明确要求追踪复杂、长期或多步骤工作时使用。
            参数：objective(必填)，steps(可选 JSON 字符串数组)，verifyCommand(可选)。同一会话只能有一个未关闭 Goal。
            """)
    public String create(@Param(name = "objective", description = "目标及可验证的完成条件") String objective,
                         @Param(name = "steps", description = "可选 JSON 数组，每项是一条步骤描述", required = false) String steps,
                         @Param(name = "verifyCommand", description = "可选验证命令", required = false) String verifyCommand,
                         ToolContext ctx) {
        try {
            GoalRuntime.Scope scope = GoalRuntime.forTool(ctx);
            Goal goal = goals.create(scope.sessionId(), scope.workspaceHash(), objective,
                    parseSteps(steps), verifyCommand);
            Goal stored = scope.createIfNoOpenGoal(goal);
            if (!goal.getId().equals(stored.getId())) {
                return "GOAL_EXISTS: " + goals.describe(stored);
            }
            return goals.describe(stored);
        } catch (Exception e) {
            return "GOAL_CREATE_ERROR: " + e.getMessage();
        }
    }

    @ToolMapping(name = "goal_status", description = "读取当前会话 Goal 的状态、步骤和阻塞原因。只读。")
    public String status(ToolContext ctx) {
        try {
            return goals.describe(GoalRuntime.forTool(ctx).load());
        } catch (Exception e) {
            return "GOAL_STATUS_ERROR: " + e.getMessage();
        }
    }

    @ToolMapping(name = "goal_update_step", description = """
            更新当前 Goal 的一个步骤。status 只能是 in_progress、done、blocked 或 skipped。
            done、blocked、skipped 时必须在 evidence 中记录测试结果、产物或具体阻塞原因。
            """)
    public String updateStep(@Param(name = "stepIndex", description = "步骤编号，从 1 开始") int stepIndex,
                             @Param(name = "status", description = "in_progress / done / blocked / skipped") String status,
                             @Param(name = "evidence", description = "执行证据或阻塞原因", required = false) String evidence,
                             ToolContext ctx) {
        try {
            GoalRuntime.Scope scope = GoalRuntime.forTool(ctx);
            Goal goal = scope.update(current -> {
                StepStatus target = parseStepStatus(status);
                goals.updateStep(current, stepIndex, target, evidence);
            });
            return goals.describe(goal);
        } catch (Exception e) {
            return "GOAL_UPDATE_ERROR: " + e.getMessage();
        }
    }

    @ToolMapping(name = "goal_complete", description = """
            在所有步骤 done 或 skipped 且验证已完成后结束当前 Goal。
            summary 必须说明验证命令或其他完成证据；未完成步骤时调用会被拒绝。
            """)
    public String complete(@Param(name = "summary", description = "验证和完成摘要") String summary,
                           ToolContext ctx) {
        try {
            if (summary == null || summary.isBlank()) return "GOAL_COMPLETE_ERROR: summary 不能为空";
            GoalRuntime.Scope scope = GoalRuntime.forTool(ctx);
            Goal goal = scope.update(current -> goals.complete(current, summary));
            return goals.describe(goal);
        } catch (Exception e) {
            return "GOAL_COMPLETE_ERROR: " + e.getMessage();
        }
    }

    @ToolMapping(name = "goal_block", description = "将当前 Goal 标为阻塞，记录需要用户或外部系统解决的具体原因。")
    public String block(@Param(name = "reason", description = "具体阻塞原因和需要的输入") String reason,
                        ToolContext ctx) {
        try {
            GoalRuntime.Scope scope = GoalRuntime.forTool(ctx);
            Goal goal = scope.update(current -> goals.block(current, reason));
            return goals.describe(goal);
        } catch (Exception e) {
            return "GOAL_BLOCK_ERROR: " + e.getMessage();
        }
    }

    @ToolMapping(name = "goal_resume", description = "用户提供了阻塞所需的信息后，恢复当前被暂停或阻塞的 Goal。")
    public String resume(ToolContext ctx) {
        try {
            GoalRuntime.Scope scope = GoalRuntime.forTool(ctx);
            Goal goal = scope.update(goals::resume);
            return goals.describe(goal);
        } catch (Exception e) {
            return "GOAL_RESUME_ERROR: " + e.getMessage();
        }
    }

    private static StepStatus parseStepStatus(String value) {
        if (value == null) throw new IllegalArgumentException("status 不能为空");
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "in_progress" -> StepStatus.IN_PROGRESS;
            case "done" -> StepStatus.DONE;
            case "blocked" -> StepStatus.BLOCKED;
            case "skipped" -> StepStatus.SKIPPED;
            default -> throw new IllegalArgumentException("不支持的步骤状态: " + value);
        };
    }

    private static List<String> parseSteps(String json) {
        if (json == null || json.isBlank()) return List.of();
        ONode node = ONode.ofJson(json);
        if (!node.isArray()) throw new IllegalArgumentException("steps 必须是 JSON 数组");
        List<String> steps = new ArrayList<>();
        for (ONode item : node.getArray()) {
            String description = item.getString();
            if (description != null && !description.isBlank()) steps.add(description);
        }
        return steps;
    }

    @Override
    public Collection<FunctionTool> getSolonTools() {
        return getTools();
    }

    @Override
    public String getSystemPrompt() {
        return """
                ## Goal 工具
                用户明确要求追踪复杂工作时用 goal_create 创建一个会话目标。
                每完成一个步骤必须调用 goal_update_step 并附证据；所有步骤完成并验证后调用 goal_complete。
                无法继续时调用 goal_block，不要把阻塞目标伪装成完成。
                """;
    }
}
