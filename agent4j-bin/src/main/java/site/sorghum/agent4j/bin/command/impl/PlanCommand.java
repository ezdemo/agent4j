package site.sorghum.agent4j.bin.command.impl;

import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.bin.command.ChatCommand;
import site.sorghum.agent4j.bin.command.ChatCommandContext;

/**
 * /plan — 进入计划模式（仅允许只读操作）。
 * <p>
 * 切换 Plan Mode 后，工具调度器将拒绝所有写入类工具调用。
 * 同时自动向 LLM 发送一条消息，使其感知到已进入计划模式。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class PlanCommand implements ChatCommand {

    @Override
    public String getCommand() {
        return "plan";
    }

    @Override
    public String getDescription() {
        return "/plan        进入计划模式（仅只读操作）";
    }

    @Override
    public String getCommandType() {
        return "mode";
    }

    @Override
    public CommandResult execute(String input, ChatCommandContext context) throws Exception {
        context.getAgent().setPlanMode(true);
        System.out.println("(已进入计划模式 — 仅允许只读操作)");
        System.out.println("探索完成后使用 submit_plan 提交计划，或输入 /execute 开始执行");
        try {
            String reply = context.getAgent().chat("进入计划模式，等待用户消息输入。");
            System.out.println();
            System.out.println(reply);
        } catch (Exception e) {
            System.out.println("(计划模式初始化失败: " + e.getMessage() + ")");
        }
        return CommandResult.CONTINUE;
    }
}
