package site.sorghum.agent4j.bin.command.impl;

import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.bin.command.ChatCommand;
import site.sorghum.agent4j.bin.command.ChatCommandContext;
import site.sorghum.agent4j.bin.command.MessageWrapper;

/**
 * /continue — 让 AI 继续推理循环。
 * <p>
 * 功能：
 * - 有 HITL 待审批时：批准并继续执行（行为同 /agree）
 * - 无 HITL 时：发送"继续"消息，让 AI 延续当前思路生成更多内容
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
        input.setMessage(null);
        return CommandResult.LOOP;
    }
}
