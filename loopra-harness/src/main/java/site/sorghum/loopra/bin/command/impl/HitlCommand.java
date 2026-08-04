package site.sorghum.loopra.bin.command.impl;

import org.noear.solon.annotation.Component;
import site.sorghum.loopra.bin.command.ChatCommand;
import site.sorghum.loopra.bin.command.ChatCommandContext;
import site.sorghum.loopra.bin.command.MessageWrapper;
import site.sorghum.loopra.bin.config.LoopraConfig;

import java.util.List;

/**
 * /hitl — 切换 HITL（Human-In-The-Loop）模式。
 * <p>
 * 循环切换三种模式：
 * <ul>
 *   <li><b>free</b>（自由）— 所有工具直接执行，无需审批</li>
 *   <li><b>approval</b>（审批）— 非只读工具执行前需用户审批</li>
 *   <li><b>auto</b>（自动）— 基于白名单自动过滤（匹配白名单自动放行，否则需审批）</li>
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
            case "auto" -> {
                List<String> whitelist = LoopraConfig.getInstance().autoWhitelist();
                String display = whitelist.size() <= 6
                        ? String.join(", ", whitelist)
                        : String.join(", ", whitelist.subList(0, 5)) + " ... (" + whitelist.size() + " 条)";
                context.getAgent().getOutput().onReasoning(
                        "✅ HITL 模式已切换为「自动」— 匹配白名单的工具自动放行，否则需审批\n" +
                        "   白名单规则: " + display + "\n");
            }
            default ->
                context.getAgent().getOutput().onReasoning("❌ HITL 模式已切换为「自由」— 工具将自动执行\n");
        }
        return CommandResult.CONTINUE;
    }
}
