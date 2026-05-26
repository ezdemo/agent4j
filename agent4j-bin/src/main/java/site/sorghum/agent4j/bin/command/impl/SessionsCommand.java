package site.sorghum.agent4j.bin.command.impl;

import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.bin.command.ChatCommand;
import site.sorghum.agent4j.bin.command.ChatCommandContext;
import site.sorghum.agent4j.bin.session.SessionStore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * /sessions — 列出历史会话。
 * <p>
 * 显示最近 20 个历史会话的名称、消息数量和最后修改时间。
 * 使用 /load N 加载指定会话。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class SessionsCommand implements ChatCommand {

    @Override
    public String getCommand() {
        return "sessions";
    }

    @Override
    public String getDescription() {
        return "/sessions    列出历史会话";
    }

    @Override
    public CommandResult execute(String input, ChatCommandContext context) throws Exception {
        SessionStore store = context.getAgent().getSessionStore();
        if (store == null) {
            System.out.println("(会话存储未启用)");
            return CommandResult.CONTINUE;
        }
        List<SessionStore.SessionInfo> sessions = store.list();
        if (sessions.isEmpty()) {
            System.out.println("(无历史会话)");
            return CommandResult.CONTINUE;
        }
        System.out.println("会话列表：");
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm");
        for (int i = 0; i < Math.min(sessions.size(), 20); i++) {
            SessionStore.SessionInfo s = sessions.get(i);
            System.out.println("  " + i + ". " + s.name + " (" + s.messageCount + " 条消息, "
                    + sdf.format(new Date(s.mtime)) + ")");
        }
        System.out.println("使用 /load N 加载");
        return CommandResult.CONTINUE;
    }
}
