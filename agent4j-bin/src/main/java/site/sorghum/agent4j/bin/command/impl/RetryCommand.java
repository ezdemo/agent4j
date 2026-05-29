package site.sorghum.agent4j.bin.command.impl;

import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.bin.command.ChatCommand;
import site.sorghum.agent4j.bin.command.ChatCommandContext;

/**
 * /retry — 撤回最后一条消息并重试。
 * <p>
 * 删除上下文中最后一条用户消息及其后的全部消息，
 * 然后使用该消息内容重新调用 LLM。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class RetryCommand implements ChatCommand {

    @Override
    public String getCommand() {
        return "retry";
    }

    @Override
    public String getDescription() {
        return "/retry       撤回最后一条消息并重试";
    }

    @Override
    public String getCommandType() {
        return "session";
    }

    @Override
    public CommandResult execute(String input, ChatCommandContext context) throws Exception {
        System.out.println("重试上一条消息...");
        String reply = context.getAgent().retryLast();
        if (reply != null) {
            System.out.println();
            System.out.println(reply);
        } else {
            System.out.println("(没有可重试的消息)");
        }
        return CommandResult.CONTINUE;
    }
}
