package site.sorghum.agent4j.tool.interact;

import org.noear.solon.annotation.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 交互服务 —— 用户选择(ask_choice)和任务跟踪(todo_write)。
 *
 * @author Sorghum
 */
@Component
public class InteractionService {

    /**
     * 获取默认会话ID（用于未指定会话ID的情况）
     */
    private static final String DEFAULT_SESSION = "__default__";
    /**
     * 按会话ID隔离的 todo 列表
     */
    private final Map<String, List<Map<String, Object>>> sessionTodos = new ConcurrentHashMap<>();

    /**
     * 渲染一个用户选择菜单。
     */
    public String askChoice(String question, List<Map<String, Object>> options,
                            Boolean allowCustom) {
        if (options == null || options.isEmpty())
            return "{\"error\":\"ask_choice requires at least one option\"}";
        StringBuilder sb = new StringBuilder();
        sb.append("┌─ ").append(question).append("\n");
        for (int i = 0; i < options.size(); i++) {
            Map<String, Object> opt = options.get(i);
            String label = (String) opt.getOrDefault("title", "option-" + (i + 1));
            String summary = (String) opt.get("summary");
            sb.append("│ ").append(i + 1).append(". ").append(label);
            if (summary != null) sb.append(" — ").append(summary);
            sb.append("\n");
        }
        if (allowCustom == Boolean.TRUE) {
            sb.append("│ 0. (type your own answer)\n");
        }
        sb.append("└─ 输入编号选择");
        return sb.toString();
    }

    /**
     * 更新会话内的任务跟踪列表。
     *
     * @param todos     todo 列表
     * @param sessionId 会话ID（为 null 时使用默认会话）
     */
    @SuppressWarnings("unchecked")
    public String todoWrite(List<Map<String, Object>> todos, String sessionId) {
        String key = sessionId != null ? sessionId : DEFAULT_SESSION;
        List<Map<String, Object>> currentTodos = todos != null ? new ArrayList<>(todos) : new ArrayList<>();
        sessionTodos.put(key, currentTodos);
        if (currentTodos.isEmpty()) return "todos cleared (0 items)";
        long done = currentTodos.stream().filter(t -> "completed".equals(t.get("status"))).count();
        long inProg = currentTodos.stream().filter(t -> "in_progress".equals(t.get("status"))).count();
        long pending = currentTodos.stream().filter(t -> "pending".equals(t.get("status"))).count();
        StringBuilder sb = new StringBuilder();
        sb.append("todos updated · ").append(done).append(" done · ")
                .append(inProg).append(" in progress · ").append(pending).append(" pending\n");
        for (Map<String, Object> t : currentTodos) {
            String s = (String) t.get("status");
            String content = (String) t.getOrDefault("content", "");
            String active = (String) t.getOrDefault("activeForm", "");
            if ("completed".equals(s)) sb.append("[x] ").append(content).append("\n");
            else if ("in_progress".equals(s)) sb.append("[>] ").append(active).append("\n");
            else sb.append("[ ] ").append(content).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * 获取指定会话的 todo 列表。
     *
     * @param sessionId 会话ID（为 null 时使用默认会话）
     * @return todo 列表的副本，如果不存在返回空列表
     */
    public List<Map<String, Object>> getTodos(String sessionId) {
        String key = sessionId != null ? sessionId : DEFAULT_SESSION;
        List<Map<String, Object>> todos = sessionTodos.get(key);
        return todos != null ? new ArrayList<>(todos) : new ArrayList<>();
    }
}
