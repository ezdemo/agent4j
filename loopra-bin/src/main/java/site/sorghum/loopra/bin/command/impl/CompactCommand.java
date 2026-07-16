package site.sorghum.loopra.bin.command.impl;

import org.noear.solon.annotation.Component;
import site.sorghum.loopra.bin.agent.core.LoopraAgent;
import site.sorghum.loopra.bin.command.ChatCommand;
import site.sorghum.loopra.bin.command.ChatCommandContext;
import site.sorghum.loopra.bin.command.MessageWrapper;

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
    public CommandResult execute(MessageWrapper input, ChatCommandContext context) throws Exception {
        LoopraAgent agent = context.getAgent();
        int before = agent.historySize();
        agent.compact();
        agent.flushSession();
        int after = agent.historySize();
        int folded = before - after;
        agent.getOutput().onReasoning("折叠完成：释放 " + folded + " 条消息（" + before + " → " + after + " 条）");
        return CommandResult.CONTINUE;
    }
}
