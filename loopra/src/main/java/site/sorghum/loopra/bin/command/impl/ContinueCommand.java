package site.sorghum.loopra.bin.command.impl;

import org.noear.solon.annotation.Component;
import site.sorghum.loopra.bin.command.ChatCommand;
import site.sorghum.loopra.bin.command.ChatCommandContext;
import site.sorghum.loopra.bin.command.MessageWrapper;

/**
 * /continue — 让 AI 继续推理循环。
 * <p>
 * 功能：
 * - 有 HITL 待审批时：批准并继续执行（行为同 /agree）
 * - 无 HITL 时：不追加任何消息，直接基于现有上下文继续推理
 * </p>
 *
 * @author Sorghum
 */
@Component
public class ContinueCommand implements ChatCommand {

    @Override
    public String getCommand() {
        return "continue";
    }

    @Override
    public String getDescription() {
        return "/continue    让 AI 继续生成/推理（HITL 模式下等同于 /agree）";
    }

    @Override
    public String getCommandType() {
        return "mode";
    }


    @Override
    public CommandResult execute(MessageWrapper input, ChatCommandContext context) {
        // 有 HITL 待审批 → 批准并恢复（同 /agree）
        if (!context.getAgent().noPendingHITL()) {
            context.getAgent().approveHITL();
        }
        // 空输入会跳过用户消息追加，直接进入模型节点继续推理。
        input.setMessage(null);
        return CommandResult.LOOP;
    }
}
