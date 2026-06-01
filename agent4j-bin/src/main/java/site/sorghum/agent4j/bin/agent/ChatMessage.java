package site.sorghum.agent4j.bin.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天消息 —— 替代 Map&lt;String, Object&gt; 表示消息的强类型封装。
 * <p>
 * 统一表示 OpenAI 兼容 API 的四种消息角色：
 * system / user / assistant / tool。
 * </p>
 *
 * @author Sorghum
 */
public class ChatMessage {

    private final String role;
    private String content;
    private List<ToolCallEntry> toolCalls;
    private String toolCallId;
    private String reasoningContent;

    private ChatMessage(String role) {
        this.role = role;
    }

    // ==================== 工厂方法 ====================

    public static ChatMessage system(String content) {
        ChatMessage msg = new ChatMessage("system");
        msg.content = content;
        return msg;
    }

    public static ChatMessage user(String content) {
        ChatMessage msg = new ChatMessage("user");
        msg.content = content;
        return msg;
    }

    public static ChatMessage assistant(String content, List<ToolCallEntry> toolCalls, String reasoningContent) {
        ChatMessage msg = new ChatMessage("assistant");
        msg.content = content;
        msg.toolCalls = toolCalls;
        msg.reasoningContent = reasoningContent;
        return msg;
    }

    public static ChatMessage tool(String toolCallId, String content) {
        ChatMessage msg = new ChatMessage("tool");
        msg.toolCallId = toolCallId;
        msg.content = content != null ? content : "(empty)";
        return msg;
    }

    // ==================== Getters ====================

    public String getRole() { return role; }

    public String getContent() { return content; }

    public void setContent(String content) { this.content = content; }

    public List<ToolCallEntry> getToolCalls() { return toolCalls; }

    public void setToolCalls(List<ToolCallEntry> toolCalls) { this.toolCalls = toolCalls; }

    public String getToolCallId() { return toolCallId; }

    public String getReasoningContent() { return reasoningContent; }

    public void setReasoningContent(String reasoningContent) { this.reasoningContent = reasoningContent; }

    // ==================== 便捷判断 ====================

    public boolean isSystem() { return "system".equals(role); }
    public boolean isUser() { return "user".equals(role); }
    public boolean isAssistant() { return "assistant".equals(role); }
    public boolean isTool() { return "tool".equals(role); }

    public boolean hasContent() { return content != null && !content.isEmpty(); }
    public boolean hasToolCalls() { return toolCalls != null && !toolCalls.isEmpty(); }
    public boolean hasReasoning() { return reasoningContent != null && !reasoningContent.isEmpty(); }

    // ==================== 反序列化 ====================

    /**
     * 从 Map 构建 ChatMessage（兼容旧的 JSONL 反序列化路径）。
     */
    @SuppressWarnings("unchecked")
    public static ChatMessage fromMap(Map<String, Object> m) {
        String role = String.valueOf(m.getOrDefault("role", "user"));
        ChatMessage msg = new ChatMessage(role);
        Object content = m.get("content");
        msg.content = content != null ? content.toString() : null;
        Object reasoning = m.get("reasoning_content");
        msg.reasoningContent = reasoning != null ? reasoning.toString() : null;
        Object toolCallId = m.get("tool_call_id");
        msg.toolCallId = toolCallId != null ? toolCallId.toString() : null;
        if (m.containsKey("tool_calls")) {
            List<Map<String, Object>> tcMaps = (List<Map<String, Object>>) m.get("tool_calls");
            if (tcMaps != null) {
                msg.toolCalls = new ArrayList<>();
                for (Map<String, Object> tc : tcMaps) {
                    String tcId = String.valueOf(tc.getOrDefault("id", "unknown"));
                    String tcName = String.valueOf(tc.getOrDefault("name", "unknown"));
                    String tcArgs = tc.get("arguments") != null ? tc.get("arguments").toString() : "{}";
                    msg.toolCalls.add(new ToolCallEntry(tcId, tcName, tcArgs));
                }
            }
        }
        return msg;
    }

    // ==================== 序列化 ====================

    /**
     * 转换为 Map（兼容旧的 JSON 序列化路径）。
     * 仅在序列化边界使用，内部逻辑应直接使用类型化字段。
     */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        if (content != null) m.put("content", content);
        if (toolCallId != null) m.put("tool_call_id", toolCallId);
        if (reasoningContent != null) m.put("reasoning_content", reasoningContent);
        if (toolCalls != null && !toolCalls.isEmpty()) {
            List<Map<String, Object>> tcMaps = new ArrayList<>();
            for (ToolCallEntry tc : toolCalls) {
                tcMaps.add(tc.toMap());
            }
            m.put("tool_calls", tcMaps);
        }
        return m;
    }

    @Override
    public String toString() {
        return "ChatMessage{role='" + role + "', content='" +
                (content != null ? content.substring(0, Math.min(50, content.length())) : "null") + "'}";
    }
}
