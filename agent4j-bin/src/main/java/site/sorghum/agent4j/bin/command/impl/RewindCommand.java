package site.sorghum.agent4j.bin.command.impl;

import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.bin.command.ChatCommand;
import site.sorghum.agent4j.bin.command.ChatCommandContext;

/**
 * /rewind N — 回退到第 N 轮对话。
 * <p>
 * 删除第 N 条用户消息之后的所有消息（包括第 N 条之后的全部回合），
 * 然后使用第 N 条用户消息的内容重新调用 LLM。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class RewindCommand implements ChatCommand {

    @Override
    public String getCommand() {
        return "rewind";
    }

    @Override
    public boolean matches(String input) {
        if (input == null) return false;
        String trimmed = input.trim().toLowerCase();
        return trimmed.matches("/rewind\\s+\\d+");
    }

    @Override
    public String getDescription() {
        return "/rewind N    回退到第 N 轮对话";
    }

    @Override
    public String getCommandType() {
        return "session";
    }

    @Override
    public String getArgHint() {
        return "N";
    }

    @Override
    public CommandResult execute(String input, ChatCommandContext context) throws Exception {
        String numPart = input.trim().substring(8).trim(); // 去掉 "/rewind "
        try {
            int n = Integer.parseInt(numPart);
            System.out.println("回退到第 " + n + " 轮...");
            String reply = context.getAgent().rewind(n);
            if (reply != null) {
                System.out.println();
                System.out.println(reply);
            } else {
                System.out.println("(无效的轮次)");
            }
        } catch (NumberFormatException e) {
            System.out.println("用法: /rewind N");
        }
        return CommandResult.CONTINUE;
    }
}
