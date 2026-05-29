package site.sorghum.agent4j.bin.command.impl;

import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.bin.command.ChatCommand;
import site.sorghum.agent4j.bin.command.ChatCommandContext;

/**
 * /execute — 退出计划模式（允许全部操作）。
 * <p>
 * 切换回正常模式后，所有工具恢复可用。
 * 同时自动向 LLM 发送一条消息，使其感知到已退出计划模式。
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
        return "/execute     退出计划模式（允许全部操作）";
    }

    @Override
    public String getCommandType() {
        return "mode";
    }

    @Override
    public CommandResult execute(String input, ChatCommandContext context) throws Exception {
        context.getAgent().setPlanMode(false);
        System.out.println("(已退出计划模式 — 允许全部操作)");
        try {
            String reply = context.getAgent().chat("退出计划模式，等待用户消息输入。");
            System.out.println();
            System.out.println(reply);
        } catch (Exception e) {
            System.out.println("(执行模式初始化失败: " + e.getMessage() + ")");
        }
        return CommandResult.CONTINUE;
    }
}
