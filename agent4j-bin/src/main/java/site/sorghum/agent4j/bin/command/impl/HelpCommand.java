package site.sorghum.agent4j.bin.command.impl;

import org.noear.solon.Solon;
import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.bin.command.ChatCommand;
import site.sorghum.agent4j.bin.command.ChatCommandContext;
import site.sorghum.agent4j.bin.command.ChatCommandRegistry;

import java.util.List;

/**
 * /help — 显示所有可用命令的帮助信息。
 * <p>
 * 自动从 {@link ChatCommandRegistry} 获取所有已注册命令的帮助文本，
 * 因此当新增命令时，/help 会自动展示，无需手动维护。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class HelpCommand implements ChatCommand {

    @Override
    public String getCommand() {
        return "help";
    }

    @Override
    public String getDescription() {
        return "/help        显示此帮助信息";
    }

    @Override
    public CommandResult execute(String input, ChatCommandContext context) throws Exception {
        // 通过 Solon IoC 获取注册表
        ChatCommandRegistry registry = Solon.context().getBean(ChatCommandRegistry.class);
        if (registry == null) {
            System.out.println("(命令注册表不可用)");
            return CommandResult.CONTINUE;
        }
        List<String> lines = registry.getHelpLines();
        System.out.println("可用命令：");
        for (String line : lines) {
            System.out.println("  " + line);
        }
        return CommandResult.CONTINUE;
    }
}
