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

    /**
     * 渲染一个用户选择菜单，展示选项列表供用户选择。
     * 支持自定义输入选项。
     *
     * @param question    问题描述
     * @param options     选项列表 [{title, summary}, ...]
     * @param allowCustom 是否允许用户自定义输入
     * @return 格式化菜单文本
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
     * 统计各状态（pending/in_progress/completed）的任务数量，
     * 并格式化输出当前任务状态。
     *
     * @param todos 任务列表 [{status, content, activeForm?}, ...]
     * @return 格式化任务状态文本
     */
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
