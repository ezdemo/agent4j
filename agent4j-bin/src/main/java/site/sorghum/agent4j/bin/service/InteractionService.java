package site.sorghum.agent4j.bin.service;

import org.noear.solon.annotation.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 交互服务 —— 用户选择(ask_choice)和任务跟踪(todo_write)。
 * <p>
 * 从 Tools.java 中抽出。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class InteractionService {

    private List<Map<String, Object>> currentTodos = new ArrayList<>();

    /** 选择对话框 */
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

    /** 任务列表更新 */
    @SuppressWarnings("unchecked")
    public String todoWrite(List<Map<String, Object>> todos) {
        currentTodos = todos != null ? new ArrayList<>(todos) : new ArrayList<>();
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
}
