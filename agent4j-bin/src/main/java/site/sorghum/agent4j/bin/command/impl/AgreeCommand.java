package site.sorghum.agent4j.bin.command.impl;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.bin.command.ChatCommand;
import site.sorghum.agent4j.bin.command.ChatCommandContext;
import site.sorghum.agent4j.bin.command.MessageWrapper;

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
@Slf4j
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
    public CommandResult execute(MessageWrapper input, ChatCommandContext context) {
        if (context.getAgent().noPendingHITL()) {
            log.warn("当前没有待审批的工具调用");
            return CommandResult.CONTINUE;
        }

        context.getAgent().approveHITL();
        input.setMessage(null);
        return CommandResult.LOOP;
    }
}
