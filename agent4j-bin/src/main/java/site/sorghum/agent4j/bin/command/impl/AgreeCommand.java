package site.sorghum.agent4j.bin.command.impl;

import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.bin.command.ChatCommand;
import site.sorghum.agent4j.bin.command.ChatCommandContext;

/**
 * /agree — 批准 HITL 待执行的工具调用。
 * <p>
 * 在 HITL 模式下，Agent 暂停并等待用户审批时使用。
 * 批准后 Agent 会继续执行工具并生成回复。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class AgreeCommand implements ChatCommand {

    @Override
    public String getCommand() {
        return "agree";
    }

    @Override
    public String getDescription() {
        return "/agree       批准 HITL 待执行的工具调用";
    }

    @Override
    public String getCommandType() {
        return "mode";
    }

    @Override
    public CommandResult execute(String input, ChatCommandContext context) throws Exception {
        if (!context.getAgent().hasPendingHITL()) {
            System.out.println("(当前没有待审批的工具调用)");
            return CommandResult.CONTINUE;
        }

        context.getAgent().approveHITL();
        try {
            long t0 = System.currentTimeMillis();
            String reply = context.getAgent().chat(null);
            long elapsed = System.currentTimeMillis() - t0;
            System.out.println();
            if (reply == null || reply.isEmpty()) {
                System.out.println("(模型返回空内容)");
            } else {
                System.out.println(reply);
            }
            System.out.println("[" + (elapsed / 1000.0) + "s]");
            context.getAgent().saveUsage();
            context.getAgent().flushSession();
        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
        }
        return CommandResult.CONTINUE;
    }
}
