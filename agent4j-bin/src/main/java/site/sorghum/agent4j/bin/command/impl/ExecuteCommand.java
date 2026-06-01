package site.sorghum.agent4j.bin.command.impl;

import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.bin.agent.LogLevel;
import site.sorghum.agent4j.bin.command.ChatCommand;
import site.sorghum.agent4j.bin.command.ChatCommandContext;

/**
 * /execute — 退出计划模式（允许全部操作）。
 * <p>
 * 仅切换模式标志，不向 LLM 发送消息。
 * 用户输入下一条消息时，LLM 自然感知所有工具已恢复可用。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class ExecuteCommand implements ChatCommand {

    @Override
    public String getCommand() {
        return "execute";
    }

    @Override
    public String getDescription() {
        return "/execute     退出计划模式（允许全部操作）";
    }

    @Override
    public String getCommandType() {
        return "mode";
    }

    @Override
    public CommandResult execute(String input, ChatCommandContext context) throws Exception {
        context.getAgent().setPlanMode(false);
        context.getAgent().getOutput().onLog(LogLevel.INFO, "已退出计划模式 — 允许全部操作");
        context.getAgent().getOutput().onMessage("已退出计划模式 — 允许全部操作");
        return CommandResult.CONTINUE;
    }
}
