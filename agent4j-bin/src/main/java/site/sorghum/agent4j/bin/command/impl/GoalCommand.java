package site.sorghum.agent4j.bin.command.impl;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.bin.command.ChatCommand;
import site.sorghum.agent4j.bin.command.ChatCommandContext;
import site.sorghum.agent4j.bin.command.MessageWrapper;
import site.sorghum.agent4j.bin.goal.*;
import site.sorghum.agent4j.tool.LogLevel;

import java.time.Instant;

/**
 * /goal — 设定并跟踪会话目标。
 * <p>
 * 子命令：
 * /goal set &lt;描述&gt;           设定新目标
 * /goal set &lt;描述&gt; --verify "cmd"  设定目标并指定验证命令
 * /goal status                 查看当前目标进度
 * /goal pause                  暂停目标
 * /goal resume                 恢复目标
 * /goal retry &lt;步骤号&gt;      手动重试某一步
 * /goal skip  &lt;步骤号&gt;      跳过某一步
 * /goal clear                  清除当前目标
 * /goal help                   显示帮助
 * </p>
 *
 * @author Sorghum
 */
@Component
@Slf4j
public class GoalCommand implements ChatCommand {

    private static final String NO_GOAL_MSG = "当前会话没有活跃目标。使用 /goal set <描述> 创建目标。";
    private static final int CMD_PREFIX_LEN = "/goal".length();
    private static final int VERIFY_FLAG_LEN = "--verify".length();

    @Inject
    private GoalEngine goalEngine;

    @Override
    public String getCommand() {
        return "goal";
    }

    @Override
    public boolean matches(String input) {
        return input != null && input.trim().toLowerCase().startsWith("/goal");
    }

    @Override
    public String getDescription() {
        return "/goal        设定并跟踪会话目标（子命令：set/status/pause/resume/retry/skip/clear）";
    }

    @Override
    public String getCommandType() {
        return "tool";
    }

    @Override
    public CommandResult execute(MessageWrapper input, ChatCommandContext ctx) throws Exception {
        String text = input.getMessage();
        String remaining = text.trim().substring(CMD_PREFIX_LEN).trim();
        String[] parts = remaining.split("\\s+", 2);
        String subCmd = parts.length > 0 ? parts[0].toLowerCase() : "help";
        String args = parts.length > 1 ? parts[1] : "";

        switch (subCmd) {
            case "set":
                return handleSet(args, ctx, input);
            case "status":
                return handleStatus(ctx);
            case "pause":
                return handlePause(ctx);
            case "resume":
                return handleResume(ctx);
            case "retry":
                return handleRetry(args, ctx);
            case "skip":
                return handleSkip(args, ctx);
            case "clear":
                return handleClear(ctx);
            default:
                return handleHelp(ctx);
        }
    }

    private CommandResult handleSet(String args, ChatCommandContext ctx, MessageWrapper input) throws Exception {
        if (args.isEmpty()) {
            ctx.getAgent().getOutput().onLog(LogLevel.INFO,
                    "用法: /goal set <描述> [--verify \"命令\"]");
            return CommandResult.CONTINUE;
        }

        // 提取 --verify 标志
        String description = args;
        String verifyCmd = null;
        int verifyIdx = args.indexOf("--verify");
        if (verifyIdx >= 0) {
            description = args.substring(0, verifyIdx).trim();
            String afterFlag = args.substring(verifyIdx + VERIFY_FLAG_LEN).trim();
            if (afterFlag.startsWith("\"")) {
                int endQuote = afterFlag.indexOf("\"", 1);
                if (endQuote > 0) {
                    verifyCmd = afterFlag.substring(1, endQuote);
                }
            } else {
                int spaceIdx = afterFlag.indexOf(' ');
                verifyCmd = spaceIdx > 0 ? afterFlag.substring(0, spaceIdx) : afterFlag;
            }
        }

        // 创建目标（LLM 拆解步骤）
        Goal goal;
        try {
            goal = goalEngine.createGoal(description, verifyCmd, ctx);
        } catch (IllegalStateException e) {
            ctx.getAgent().getOutput().onLog(LogLevel.ERROR,
                    "❌ " + e.getMessage());
            return CommandResult.CONTINUE;
        }

        // 持久化并激活
        goalEngine.activateGoal(goal, ctx);

        // 生成步骤文本并注入到 LLM 的下一次请求中
        StringBuilder stepsText = new StringBuilder();
        for (int i = 0; i < goal.getSteps().size(); i++) {
            GoalStep step = goal.getSteps().get(i);
            stepsText.append("  ").append(i + 1).append(". [ ] ").append(step.getDescription()).append("\n");
        }

        String prompt = """
                当前会话已设定目标：「%s」
                
                步骤计划：
                %s
                
                请逐条执行以上步骤。
                每完成一步，**必须**调用 goal_mark_step 工具通知系统，参数 stepIndex 从 1 开始。
                如果有步骤失败，系统会自动重试（最多 %d 次）。
                全部完成后总结汇报。
                """
                .formatted(goal.getTitle(), stepsText.toString(), goal.getMaxRetries());

        input.setMessage(prompt);
        return CommandResult.LOOP;
    }

    private CommandResult handleStatus(ChatCommandContext ctx) {
        Goal goal = goalEngine.getCurrentGoal(ctx);
        if (goal == null) {
            ctx.getAgent().getOutput().onLog(LogLevel.INFO, NO_GOAL_MSG);
            return CommandResult.CONTINUE;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n──────────────────────────────────────\n");
        sb.append("🎯 目标：").append(goal.getTitle()).append("\n");
        sb.append("状态：").append(formatStatusBadge(goal.getStatus())).append("\n\n");
        sb.append("进度：").append(goal.progressText()).append("\n\n");

        for (GoalStep step : goal.getSteps()) {
            String icon = switch (step.getStatus()) {
                case DONE -> "✅";
                case IN_PROGRESS -> "⏳";
                case FAILED -> "❌";
                case SKIPPED -> "⏭️";
                case PENDING -> "⬜";
            };
            String retryInfo = step.getRetryCount() > 0 ? " (retry:" + step.getRetryCount() + ")" : "";
            sb.append(icon).append(" ").append(step.getIndex() + 1).append(". ")
                    .append(step.getDescription()).append(retryInfo).append("\n");
            if (step.getLastError() != null && !step.getLastError().isEmpty()) {
                sb.append("   └─ ").append(step.getLastError()).append("\n");
            }
        }
        sb.append("──────────────────────────────────────");

        ctx.getAgent().getOutput().onReasoning(sb.toString());
        return CommandResult.CONTINUE;
    }

    private CommandResult handlePause(ChatCommandContext ctx) throws Exception {
        Goal goal = goalEngine.getCurrentGoal(ctx);
        if (goal == null) return noGoalResponse(ctx);
        goalEngine.pause(goal, ctx);
        ctx.getAgent().getOutput().onLog(LogLevel.INFO, "⏸️ 目标已暂停： " + goal.getTitle());
        return CommandResult.CONTINUE;
    }

    private CommandResult handleResume(ChatCommandContext ctx) throws Exception {
        Goal goal = goalEngine.getCurrentGoal(ctx);
        if (goal == null) return noGoalResponse(ctx);
        goalEngine.resume(goal, ctx);
        ctx.getAgent().getOutput().onLog(LogLevel.INFO, "▶️ 目标已恢复： " + goal.getTitle());
        return CommandResult.CONTINUE;
    }

    /**
     * 从参数中解析步骤索引（1-based），并校验范围。返回解析后的 GoalStep，
     * 若解析失败或超出范围则通过 output 报告错误并返回 null。
     */
    private GoalStep resolveStepIndex(String args, ChatCommandContext ctx) throws Exception {
        if (args.isEmpty()) {
            ctx.getAgent().getOutput().onLog(LogLevel.INFO, "用法: /goal retry/skip <步骤号>");
            return null;
        }
        int stepIndex;
        try {
            stepIndex = Integer.parseInt(args.trim()) - 1;
        } catch (NumberFormatException e) {
            ctx.getAgent().getOutput().onLog(LogLevel.INFO, "步骤号必须是数字");
            return null;
        }
        Goal goal = goalEngine.getCurrentGoal(ctx);
        if (goal == null) {
            noGoalResponse(ctx);
            return null;
        }
        if (stepIndex < 0 || stepIndex >= goal.getSteps().size()) {
            ctx.getAgent().getOutput().onLog(LogLevel.INFO, "步骤号超出范围（1-" + goal.getSteps().size() + "）");
            return null;
        }
        return goal.getSteps().get(stepIndex);
    }

    private CommandResult handleRetry(String args, ChatCommandContext ctx) throws Exception {
        GoalStep step = resolveStepIndex(args, ctx);
        if (step == null) return CommandResult.CONTINUE;

        Goal goal = goalEngine.getCurrentGoal(ctx);
        step.setStatus(StepStatus.PENDING);
        step.setRetryCount(0);
        step.setLastError(null);
        step.setCompletedAt(null);
        goal.setUpdatedAt(Instant.now());
        ctx.getAgent().getWorkspaceManager().getGoalStore().save(goal);

        ctx.getAgent().getOutput().onReasoning(
                "🔄 步骤 " + (step.getIndex() + 1) + " 已重置为待执行状态，请继续工作。");
        return CommandResult.CONTINUE;
    }

    private CommandResult handleSkip(String args, ChatCommandContext ctx) throws Exception {
        GoalStep step = resolveStepIndex(args, ctx);
        if (step == null) return CommandResult.CONTINUE;

        Goal goal = goalEngine.getCurrentGoal(ctx);
        step.setStatus(StepStatus.SKIPPED);
        step.setCompletedAt(Instant.now());
        goal.setUpdatedAt(Instant.now());

        if (goal.isAllDone()) {
            goal.setStatus(GoalStatus.COMPLETED);
            goal.setCompletedAt(Instant.now());
        }

        ctx.getAgent().getWorkspaceManager().getGoalStore().save(goal);
        ctx.getAgent().getOutput().onLog(LogLevel.INFO,
                "⏭️ 已跳过步骤 " + (step.getIndex() + 1) + "：" + step.getDescription());
        return CommandResult.CONTINUE;
    }

    private CommandResult handleClear(ChatCommandContext ctx) throws Exception {
        Goal goal = goalEngine.getCurrentGoal(ctx);
        if (goal == null) return noGoalResponse(ctx);

        ctx.getAgent().getWorkspaceManager().getGoalStore().delete(goal.getSessionId());
        ctx.getAgent().getOutput().onLog(LogLevel.INFO, "🗑️ 目标已清除：" + goal.getTitle());
        return CommandResult.CONTINUE;
    }

    private CommandResult handleHelp(ChatCommandContext ctx) {
        ctx.getAgent().getOutput().onReasoning(
                """
                /goal 子命令：
                  /goal set <描述> [--verify "命令"]    设定新目标
                  /goal status                          查看当前目标进度
                  /goal pause                           暂停目标
                  /goal resume                          恢复目标
                  /goal retry <步骤号>                  手动重试某一步
                  /goal skip <步骤号>                   跳过某一步
                  /goal clear                           清除当前目标
                """);
        return CommandResult.CONTINUE;
    }

    private CommandResult noGoalResponse(ChatCommandContext ctx) {
        ctx.getAgent().getOutput().onLog(LogLevel.INFO, NO_GOAL_MSG);
        return CommandResult.CONTINUE;
    }

    private String formatStatusBadge(GoalStatus status) {
        return switch (status) {
            case ACTIVE -> "🟢 进行中";
            case PAUSED -> "🟡 已暂停";
            case COMPLETED -> "✅ 已完成";
            case FAILED -> "🔴 失败";
        };
    }
}
