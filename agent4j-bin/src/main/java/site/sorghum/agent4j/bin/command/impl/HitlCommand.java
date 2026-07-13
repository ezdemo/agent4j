package site.sorghum.agent4j.bin.command.impl;

import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.bin.command.ChatCommand;
import site.sorghum.agent4j.bin.command.ChatCommandContext;
import site.sorghum.agent4j.bin.command.MessageWrapper;

/**
 * /hitl — 切换 HITL（Human-In-The-Loop）模式。
 * <p>
 * 循环切换三种模式：
 * <ul>
 *   <li><b>free</b>（自由）— 所有工具直接执行，无需审批</li>
 *   <li><b>approval</b>（审批）— 非只读工具执行前需用户审批</li>
 *   <li><b>auto</b>（自动）— 自动批准所有工具调用</li>
 * </ul>
 * </p>
 *
 * @author Sorghum
 */
@Component
public class HitlCommand implements ChatCommand {

    @Override
    public String getCommand() {
        return "hitl";
    }

    @Override
    public String getDescription() {
        return "/hitl        切换 HITL 模式（free/approval/auto）";
    }

    @Override
    public String getCommandType() {
        return "mode";
    }

    @Override
    public CommandResult execute(MessageWrapper input, ChatCommandContext context) {
        context.getAgent().toggleHitl();
        String mode = context.getAgent().getHitlMode();
        switch (mode) {
            case "approval" ->
                context.getAgent().getOutput().onReasoning("✅ HITL 模式已切换为「审批」— 非只读工具执行前需用户审批\n" +
                        "   使用 /agree 批准执行，/deny 拒绝执行\n");
            case "auto" ->
                context.getAgent().getOutput().onReasoning("✅ HITL 模式已切换为「自动」— 工具调用将自动批准\n");
            default ->
                context.getAgent().getOutput().onReasoning("❌ HITL 模式已切换为「自由」— 工具将自动执行\n");
        }
        return CommandResult.CONTINUE;
    }
}
