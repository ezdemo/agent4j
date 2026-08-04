package site.sorghum.loopra.bin.command.impl;

import org.noear.solon.annotation.Component;
import site.sorghum.loopra.bin.agent.core.LoopraAgent;
import site.sorghum.loopra.bin.command.ChatCommand;
import site.sorghum.loopra.bin.command.ChatCommandContext;
import site.sorghum.loopra.bin.command.MessageWrapper;
import site.sorghum.loopra.tool.LogLevel;

/**
 * /execute — 退出计划模式（恢复全部工具）。
 * <p>
 * 若存在经 submit_plan 提交且待审查的计划，批准该计划：
 * 将计划内容包装为执行指令消息继续走聊天流程（LOOP），
 * 使 LLM 在全部工具恢复可用后按计划执行。
 * 无待审查计划时仅退出计划模式。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class ExecuteCommand implements ChatCommand {

    @Override
    public String getCommand() {
        return "execute";
    }

    @Override
    public String getDescription() {
        return "/execute     退出计划模式（有待审查计划时批准并按计划执行）";
    }

    @Override
    public String getCommandType() {
        return "mode";
    }

    @Override
    public CommandResult execute(MessageWrapper input, ChatCommandContext context) {
        LoopraAgent agent = context.getAgent();
        String executionMessage = agent.approvePendingPlan();
        if (executionMessage != null) {
            input.setMessage(executionMessage);
            return CommandResult.LOOP;
        }
        agent.setPlanMode(false);
        agent.getOutput().onLog(LogLevel.INFO, "已退出计划模式 — 允许全部操作");
        return CommandResult.CONTINUE;
    }
}
