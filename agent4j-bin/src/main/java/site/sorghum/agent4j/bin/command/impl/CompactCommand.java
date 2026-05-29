package site.sorghum.agent4j.bin.command.impl;

import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.bin.command.ChatCommand;
import site.sorghum.agent4j.bin.command.ChatCommandContext;

/**
 * /compact — 折叠历史消息。
 * <p>
 * 将较长对话历史进行语义摘要压缩，保留近 20 条消息完整，
 * 较早消息用摘要替代。同时刷入会话数据到磁盘。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class CompactCommand implements ChatCommand {

    @Override
    public String getCommand() {
        return "compact";
    }

    @Override
    public String getDescription() {
        return "/compact     折叠历史消息（语义摘要）";
    }

    @Override
    public String getCommandType() {
        return "session";
    }

    @Override
    public CommandResult execute(String input, ChatCommandContext context) throws Exception {
        System.out.println("正在折叠历史消息...");
        context.getAgent().compact();
        context.getAgent().flushSession();
        System.out.println("(完成，当前 " + context.getAgent().historySize() + " 条消息)");
        return CommandResult.CONTINUE;
    }
}
