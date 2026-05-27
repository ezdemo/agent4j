package site.sorghum.agent4j.bin.command.impl;

import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.bin.command.ChatCommand;
import site.sorghum.agent4j.bin.command.ChatCommandContext;

/**
 * /deny — 拒绝 HITL 待执行的工具调用。
 * <p>
 * 在 HITL 模式下，Agent 暂停并等待用户审批时使用。
 * 拒绝后 Agent 会跳过工具执行，通知模型工具被拒绝。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class DenyCommand implements ChatCommand {

    @Override
    public String getCommand() {
        return "deny";
    }

    @Override
    public String getDescription() {
        return "/deny        拒绝 HITL 待执行的工具调用";
    }

    @Override
    public CommandResult execute(String input, ChatCommandContext context) throws Exception {
        if (!context.getAgent().hasPendingHITL()) {
            System.out.println("(当前没有待审批的工具调用)");
            return CommandResult.CONTINUE;
        }

        context.getAgent().denyHITL();
        try {
            String reply = context.getAgent().chat(null);
            System.out.println();
            if (reply == null || reply.isEmpty()) {
                System.out.println("(模型返回空内容)");
            } else {
                System.out.println(reply);
            }
            context.getAgent().saveUsage();
            context.getAgent().flushSession();
        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
        }
        return CommandResult.CONTINUE;
    }
}
