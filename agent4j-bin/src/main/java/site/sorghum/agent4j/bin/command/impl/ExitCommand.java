package site.sorghum.agent4j.bin.command.impl;

import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.bin.command.ChatCommand;
import site.sorghum.agent4j.bin.command.ChatCommandContext;

/**
 * /exit — 退出程序。
 * <p>
 * 同时支持 /quit 别名。退出前刷入会话数据并保存 token 用量。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class ExitCommand implements ChatCommand {

    @Override
    public String getCommand() {
        return "exit";
    }

    @Override
    public boolean matches(String input) {
        if (input == null) return false;
        String trimmed = input.trim().toLowerCase();
        return "/exit".equals(trimmed) || "/quit".equals(trimmed);
    }

    @Override
    public String getDescription() {
        return "/exit (/quit)  退出程序";
    }

    @Override
    public String getCommandType() {
        return ChatCommand.super.getCommandType();
    }

    @Override
    public CommandResult execute(String input, ChatCommandContext context) {
        context.getAgent().flushSession();
        context.getAgent().saveUsage();
        System.out.println("再见");
        context.exit();
        return CommandResult.EXIT;
    }
}
