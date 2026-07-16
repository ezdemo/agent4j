package site.sorghum.agent4j.bin.command.impl;

import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.bin.command.ChatCommand;
import site.sorghum.agent4j.bin.command.ChatCommandContext;
import site.sorghum.agent4j.bin.command.MessageWrapper;
import site.sorghum.agent4j.bin.goal.Goal;
import site.sorghum.agent4j.bin.goal.GoalRuntime;
import site.sorghum.agent4j.bin.goal.GoalService;
import site.sorghum.agent4j.bin.goal.StepStatus;
import site.sorghum.agent4j.tool.LogLevel;

import java.util.List;
import java.util.Locale;

/** /goal 命令是用户直接管理当前会话 Goal 的入口。 */
@Component
public class GoalCommand implements ChatCommand {
    private final GoalService goals = new GoalService();

    @Override
    public String getCommand() {
        return "goal";
    }

    @Override
    public boolean matches(String input) {
        if (input == null) return false;
        String trimmed = input.trim().toLowerCase(Locale.ROOT);
        return "/goal".equals(trimmed) || trimmed.startsWith("/goal ");
    }

    @Override
    public String getDescription() {
        return "/goal <create|status|pause|resume|done|block|complete|cancel>  管理当前会话 Goal";
    }

    @Override
    public String getCommandType() {
        return "tool";
    }

    @Override
    public String getArgHint() {
        return "create <目标> | status | done <步骤号> <证据>";
    }

    @Override
    public CommandResult execute(MessageWrapper input, ChatCommandContext context) {
        try {
            String[] parts = input.getMessage().trim().split("\\s+", 3);
            String action = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "status";
            String argument = parts.length > 2 ? parts[2].trim() : "";
            GoalRuntime.Scope scope = GoalRuntime.forWorkspace(context.getAgent().getWorkspace(),
                    context.getAgent().getSessionStore().currentName());
            Goal current = scope.load();

            String result = switch (action) {
                case "create" -> create(scope, current, argument);
                case "status" -> goals.describe(current);
                case "pause" -> mutate(scope, current, () -> goals.pause(current));
                case "resume" -> mutate(scope, current, () -> goals.resume(current));
                case "cancel" -> mutate(scope, current, () -> goals.cancel(current, argument));
                case "block" -> mutate(scope, current, () -> goals.block(current, argument));
                case "complete" -> mutate(scope, current, () -> goals.complete(current, argument));
                case "done" -> markDone(scope, current, argument);
                default -> usage();
            };
            context.getAgent().getOutput().onLog(LogLevel.INFO, result);
        } catch (Exception e) {
            context.getAgent().getOutput().onLog(LogLevel.ERROR, "Goal 命令失败: " + e.getMessage());
        }
        return CommandResult.CONTINUE;
    }

    private String create(GoalRuntime.Scope scope, Goal current, String objective) throws Exception {
        if (current != null && current.isOpen()) return "当前已有未关闭 Goal。请先完成或取消：\n" + goals.describe(current);
        Goal goal = goals.create(scope.sessionId(), scope.workspaceHash(), objective, List.of(), null);
        scope.save(goal);
        return "已创建\n" + goals.describe(goal);
    }

    private String markDone(GoalRuntime.Scope scope, Goal current, String argument) throws Exception {
        String[] parts = argument.split("\\s+", 2);
        if (parts.length < 2) return "用法: /goal done <步骤号> <验证证据>";
        int index = Integer.parseInt(parts[0]);
        goals.updateStep(current, index, StepStatus.DONE, parts[1]);
        scope.save(current);
        return goals.describe(current);
    }

    private String mutate(GoalRuntime.Scope scope, Goal current, ThrowingAction action) throws Exception {
        action.run();
        scope.save(current);
        return goals.describe(current);
    }

    private static String usage() {
        return "用法: /goal create <目标> | /goal status | /goal pause | /goal resume | "
                + "/goal done <步骤号> <证据> | /goal block <原因> | /goal complete <摘要> | /goal cancel [原因]";
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }
}
