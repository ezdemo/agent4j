package site.sorghum.loopra.bin.command.impl;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;
import site.sorghum.loopra.bin.command.ChatCommand;
import site.sorghum.loopra.bin.command.ChatCommandContext;
import site.sorghum.loopra.bin.command.MessageWrapper;

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
@Slf4j
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
    public String getCommandType() {
        return "mode";
    }

    @Override
    public boolean isSilent() {
        return true;
    }

    @Override
    public CommandResult execute(MessageWrapper input, ChatCommandContext context) {
        if (context.getAgent().noPendingHITL()) {
            log.info("(当前没有待审批的工具调用)");
            return CommandResult.CONTINUE;
        }

        context.getAgent().denyHITL();
        try {
            String reply = context.getAgent().chat(null);
            if (reply == null || reply.isEmpty()) {
                log.info("(模型返回空内容)");
            } else {
                log.info(reply);
            }
            context.getAgent().saveUsage();
            context.getAgent().flushSession();
        } catch (Exception e) {
            log.error("错误: {}" , e.getMessage());
        }
        return CommandResult.CONTINUE;
    }
}
