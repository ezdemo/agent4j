package site.sorghum.loopra.bin.command.impl;

import org.noear.solon.annotation.Component;
import site.sorghum.loopra.bin.command.ChatCommand;
import site.sorghum.loopra.bin.command.ChatCommandContext;
import site.sorghum.loopra.bin.command.MessageWrapper;
import site.sorghum.loopra.tool.LogLevel;

/**
 * /plan — 进入计划模式（仅允许只读操作）。
 * <p>
 * 仅切换模式标志，不向 LLM 发送消息。
 * 计划模式规则已永久在 system prompt 中描述，
 * 用户输入下一条消息时 LLM 自然感知约束。
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
        return "/plan        进入计划模式（仅只读操作）";
    }

    @Override
    public String getCommandType() {
        return "mode";
    }

    @Override
    public CommandResult execute(MessageWrapper input, ChatCommandContext context) {
        context.getAgent().getOutput().onLog(LogLevel.INFO, "探索完成后使用 输入 /execute 开始执行");
        context.getAgent().getOutput().onReasoning(
                "已进入计划模式 — 仅允许只读操作"
        );
        return CommandResult.CONTINUE;
    }
}
