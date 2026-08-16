package site.sorghum.loopra.bin.command.impl;

import org.noear.solon.annotation.Component;
import site.sorghum.loopra.bin.agent.core.LoopraAgent;
import site.sorghum.loopra.bin.command.ChatCommand;
import site.sorghum.loopra.bin.command.ChatCommandContext;
import site.sorghum.loopra.bin.command.MessageWrapper;
import site.sorghum.loopra.tool.LogLevel;

/**
 * /plan — 进入计划模式（仅允许只读操作）。
 * <p>
 * 切换会话级计划模式状态（AgentLoop 负责工具过滤、执行拒绝与指令注入），
 * 并通过 mode_changed 事件同步前端。支持两种用法：
 * <ul>
 *   <li>{@code /plan} —— 仅进入计划模式，等待用户下一条消息</li>
 *   <li>{@code /plan <任务描述>} —— 进入计划模式并立即将任务交给 LLM 开始规划</li>
 * </ul>
 * </p>
 *
 * @author Sorghum
 */
@Component
public class PlanCommand implements ChatCommand {

    @Override
    public String getCommand() {
        return "plan";
    }

    @Override
    public String getDescription() {
        return "/plan [任务]   进入计划模式（只读探索，用 submit_plan 提交计划供审查）";
    }

    @Override
    public String getCommandType() {
        return "mode";
    }

    @Override
    public String getArgHint() {
        return "任务描述（可选）";
    }

    @Override
    public boolean matches(String input) {
        if (input == null) return false;
        String trimmed = input.trim();
        return trimmed.equalsIgnoreCase("/plan")
                || trimmed.regionMatches(true, 0, "/plan ", 0, 6);
    }

    @Override
    public CommandResult execute(MessageWrapper input, ChatCommandContext context) {
        LoopraAgent agent = context.getAgent();
        agent.setPlanMode(true);

        String task = stripCommandPrefix(input.getMessage());
        if (!task.isEmpty()) {
            // /plan <任务>：进入计划模式并立即开始规划
            input.setMessage(task);
            return CommandResult.LOOP;
        }
        agent.getOutput().onLog(LogLevel.INFO,
                "已进入计划模式：仅允许只读操作。请先探索，再用 submit_plan 提交执行计划供审查；确认后输入 /execute 批准执行");
        return CommandResult.CONTINUE;
    }

    /** 去掉 "/plan" 前缀（不区分大小写），返回剩余任务文本。 */
    static String stripCommandPrefix(String message) {
        if (message == null) return "";
        String trimmed = message.trim();
        if (trimmed.regionMatches(true, 0, "/plan", 0, 5)) {
            return trimmed.substring(5).trim();
        }
        return trimmed;
    }
}
