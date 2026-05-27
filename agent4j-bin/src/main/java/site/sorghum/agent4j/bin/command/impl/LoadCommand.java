package site.sorghum.agent4j.bin.command.impl;

import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.bin.command.ChatCommand;
import site.sorghum.agent4j.bin.command.ChatCommandContext;
import site.sorghum.agent4j.bin.session.SessionStore;

import java.util.List;
import java.util.Map;

/**
 * /load N — 加载指定编号的历史会话。
 * <p>
 * 先开启新会话清空上下文，然后切换到选中的历史会话文件，
 * 加载全部消息并注入到当前上下文中。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class LoadCommand implements ChatCommand {

    @Override
    public String getCommand() {
        return "load";
    }

    @Override
    public boolean matches(String input) {
        if (input == null) return false;
        String trimmed = input.trim().toLowerCase();
        return trimmed.matches("/load\\s+\\d+");
    }

    @Override
    public String getDescription() {
        return "/load N      加载编号为 N 的历史会话";
    }

    @Override
    public CommandResult execute(String input, ChatCommandContext context) throws Exception {
        SessionStore store = context.getAgent().getSessionStore();
        if (store == null) {
            System.out.println("(会话存储未启用)");
            return CommandResult.CONTINUE;
        }

        String numPart = input.trim().substring(6).trim(); // 去掉 "/load "
        try {
            int n = Integer.parseInt(numPart);
            List<SessionStore.SessionInfo> sessions = store.list();
            if (n < 0 || n >= sessions.size()) {
                System.out.println("(无效编号)");
                return CommandResult.CONTINUE;
            }
            String name = sessions.get(n).name;

            // 新建会话并切换到目标会话
            context.getAgent().newSession();
            // 复用当前 store（已使用工作区隔离的会话目录）
            SessionStore currentStore = context.getAgent().getSessionStore();
            currentStore.switchTo(name);
            List<Map<String, Object>> loaded = currentStore.load();

            // 注入历史消息
            for (Map<String, Object> m : loaded) {
                context.getAgent().injectHistory(m);
            }

            System.out.println("(已加载会话: " + name + ", " + loaded.size() + " 条消息)");
        } catch (NumberFormatException e) {
            System.out.println("用法: /load N");
        }
        return CommandResult.CONTINUE;
    }
}
