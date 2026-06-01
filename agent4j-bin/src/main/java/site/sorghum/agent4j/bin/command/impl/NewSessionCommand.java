package site.sorghum.agent4j.bin.command.impl;

import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.bin.command.ChatCommand;
import site.sorghum.agent4j.bin.command.ChatCommandContext;

/**
 * /new — 开启新会话。
 * <p>
 * 清空当前上下文的历史消息，但不影响底层会话文件的物理存储。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class NewSessionCommand implements ChatCommand {

    @Override
    public String getCommand() {
        return "new";
    }

    @Override
    public String getDescription() {
        return "/new         开启新会话";
    }

    @Override
    public String getCommandType() {
        return "session";
    }

    @Override
    public CommandResult execute(String input, ChatCommandContext context) {
        context.getAgent().newSession();
        context.getAgent().getOutput().onReasoning("已开启新会话");
        return CommandResult.CONTINUE;
    }
}
