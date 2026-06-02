package site.sorghum.agent4j.bin.command.impl;

import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.bin.command.ChatCommand;
import site.sorghum.agent4j.bin.command.ChatCommandContext;

/**
 * /hitl — 切换 HITL（Human-In-The-Loop）模式。
 * <p>
 * 开启后，每次 Agent 执行非只读工具前会暂停，
 * 等待用户发送 /agree 批准或 /deny 拒绝。
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
        return "/hitl        切换 HITL 模式（执行前需用户审批）";
    }

    @Override
    public String getCommandType() {
        return "mode";
    }

    @Override
    public CommandResult execute(String input, ChatCommandContext context) {
        context.getAgent().toggleHitl();
        boolean on = context.getAgent().isHitlMode();
        if (on) {
            System.out.println("✅ HITL 模式已开启 — 非只读工具执行前需用户审批");
            System.out.println("   使用 /agree 批准执行，/deny 拒绝执行");
        } else {
            System.out.println("❌ HITL 模式已关闭 — 工具将自动执行");
        }
        return CommandResult.CONTINUE;
    }
}
