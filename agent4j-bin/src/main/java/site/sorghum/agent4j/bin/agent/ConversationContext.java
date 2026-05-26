package site.sorghum.agent4j.bin.agent;

import site.sorghum.agent4j.bin.session.SessionStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话上下文 —— 在内存中管理消息历史。
 * <p>
 * 消息按角色累积：
 * <ul>
 *   <li>{@code user} — 用户输入</li>
 *   <li>{@code assistant} — 模型回复（可能含 tool_calls、reasoning_content）</li>
 *   <li>{@code tool} — 工具执行结果</li>
 * </ul>
 * 每次 API 调用时，{@link #buildMessages(String)} 将 systemPrompt + history 组装为完整消息列表。
 * </p>
 *
 * @author Sorghum
 */
public class ConversationContext {

    private final List<Map<String, Object>> history = new ArrayList<>();
    private final PromptPrefix prefix;
    /** 持久化存储（可选） */
    private SessionStore sessionStore = null;

    public ConversationContext(PromptPrefix prefix) {
        this.prefix = prefix;
    }

    /**
     * 绑定会话存储，所有后续消息将通过此存储持久化。
     * 传入 null 可解除绑定（停止持久化）。
     */
    public void setSessionStore(SessionStore store) {
        this.sessionStore = store;
    }

    public SessionStore getSessionStore() {
        return sessionStore;
    }

    // ---- 写入 ----

    public void addUser(String content) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "user");
        msg.put("content", content);
        history.add(msg);
        persist(msg);
    }

    public void addAssistant(String content, List<Map<String, Object>> toolCalls, String reasoningContent) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "assistant");
        if (content != null && !content.isEmpty()) {
            msg.put("content", content);
        }
        if (toolCalls != null && !toolCalls.isEmpty()) {
            msg.put("tool_calls", toolCalls);
        }
        if (reasoningContent != null && !reasoningContent.isEmpty()) {
            msg.put("reasoning_content", reasoningContent);
        }
        history.add(msg);
        persist(msg);
    }

    public void addToolResult(String toolCallId, String result) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "tool");
        msg.put("tool_call_id", toolCallId);
        msg.put("content", result != null ? result : "(empty)");
        history.add(msg);
        persist(msg);
    }

    /**
     * 将消息追加写入 JSONL 文件。
     * 如果 sessionStore 未设置或写入失败，静默忽略。
     */
    private void persist(Map<String, Object> msg) {
        if (sessionStore != null) {
            try {
                sessionStore.append(msg);
            } catch (Exception e) {
                System.err.println("[session] jsonl 写入失败: " + e.getMessage());
            }
        }
    }

    // ---- 读取 ----

    /**
     * 构建发给 API 的消息列表 = prefix（system msg） + history。
     * prefix 始终保持不变 → DeepSeek 前缀缓存命中。
     */
    public List<Map<String, Object>> buildMessages() {
        List<Map<String, Object>> msgs = prefix.toMessages();
        msgs.addAll(history);
        return msgs;
    }

    /**
     * 替换系统提示词（计划模式切换时用）。
     * 注意：这将导致下一次 API 调用缓存 miss。
     */
    public void replaceSystem(String newSystem) {
        prefix.replaceSystem(newSystem);
    }

    /**
     * 获取工具定义快照（来自 PromptPrefix 的稳定引用）。
     * 跨 turn 不变，用于 DeepSeek 前缀缓存。
     */
    public List<Map<String, Object>> tools() {
        return prefix.tools();
    }

    /**
     * 加载历史会话时注入消息到上下文，不触发持久化。
     * 用于 /load 命令从 JSONL 文件恢复会话。
     */
    public void injectHistory(Map<String, Object> msg) {
        history.add(msg);
    }

    public int size() {
        return history.size();
    }

    /**
     * 清除所有历史消息（新建会话时用）。
     * 同时触发持久化回写。
     */
    public void clear() {
        history.clear();
        rewriteStore();
    }

    /**
     * 撤回最后一条用户消息及其后的所有消息，返回被撤回的用户消息内容。
     * 用于 /retry 命令。同时同步持久化。
     */
    public String retryLastUser() {
        for (int i = history.size() - 1; i >= 0; i--) {
            if ("user".equals(history.get(i).get("role"))) {
                String text = (String) history.get(i).get("content");
                history.subList(i, history.size()).clear();
                // 同步持久化：回写文件以移除被撤回的消息
                rewriteStore();
                return text;
            }
        }
        return null;
    }

    /**
     * 回退到第 N 条用户消息，移除之后的所有消息。
     * 用于 /rewind N 命令。同时同步持久化。
     */
    public String rewindToUser(int userIndex) {
        int count = 0;
        for (int i = 0; i < history.size(); i++) {
            if ("user".equals(history.get(i).get("role"))) {
                if (count == userIndex) {
                    String text = (String) history.get(i).get("content");
                    history.subList(i, history.size()).clear();
                    // 同步持久化：回写文件以移除被回退的消息
                    rewriteStore();
                    return text;
                }
                count++;
            }
        }
        return null;
    }

    /** 将当前历史持久化回写到 session store */
    private void rewriteStore() {
        if (sessionStore != null) {
            try {
                sessionStore.rewrite(history);
            } catch (Exception e) {
                System.err.println("[session] rewrite 失败: " + e.getMessage());
            }
        }
    }

    /**
     * 折叠历史：用折叠后的消息列表替换当前历史。
     * 由 AgentLoop 的预检调用，将旧消息替换为摘要。
     */
    public void compact(List<Map<String, Object>> foldedMessages) {
        history.clear();
        history.addAll(foldedMessages);
        // 持久化回写
        rewriteStore();
    }

    /**
     * 获取完整历史消息列表的副本（用于调试）。
     */
    public List<Map<String, Object>> getHistory() {
        return new ArrayList<>(history);
    }
}
