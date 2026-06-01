package site.sorghum.agent4j.bin.agent;

import lombok.Data;
import org.noear.snack4.ONode;
import org.noear.snack4.annotation.ONodeAttr;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天消息 —— 替代 Map&lt;String, Object&gt; 表示消息的强类型封装。
 * <p>
 * 统一表示 OpenAI 兼容 API 的四种消息角色：system / user / assistant / tool。
 * </p>
 *
 * @author Sorghum
 */
@Data
public class ChatMessage {

    private final String role;

    @ONodeAttr(name = "content")
    private String content;

    @ONodeAttr(name = "tool_calls")
    private List<ToolCallEntry> toolCalls;

    @ONodeAttr(name = "tool_call_id")
    private String toolCallId;

    @ONodeAttr(name = "reasoning_content")
    private String reasoningContent;

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

    // ==================== 便捷判断 ====================

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
                    String tcName = "unknown";
                    Object tcArgsObj = "{}";
                    Object funcObj = tc.get("function");
                    if (funcObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> func = (Map<String, Object>) funcObj;
                        tcName = String.valueOf(func.getOrDefault("name", "unknown"));
                        tcArgsObj = func.get("arguments");
                        if (tcArgsObj == null) tcArgsObj = "{}";
                    } else {
                        tcName = String.valueOf(tc.getOrDefault("name", "unknown"));
                        tcArgsObj = tc.get("arguments");
                        if (tcArgsObj == null) tcArgsObj = "{}";
                    }
                    if (tcArgsObj instanceof String tcArgsStr) {
                        try {
                            tcArgsObj = ONode.ofJson(tcArgsStr).toData();
                        } catch (Exception ignored) {
                        }
                    }
                    msg.toolCalls.add(new ToolCallEntry(tcId, tcName, tcArgsObj));
                }
            }
        }
        return msg;
    }

    public boolean isSystem() {
        return "system".equals(role);
    }

    public boolean isUser() {
        return "user".equals(role);
    }

    public boolean isAssistant() {
        return "assistant".equals(role);
    }

    public boolean isTool() {
        return "tool".equals(role);
    }

    public boolean hasContent() {
        return content != null && !content.isEmpty();
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    // ==================== 反序列化 ====================

    public boolean hasReasoning() {
        return reasoningContent != null && !reasoningContent.isEmpty();
    }

    // ==================== 序列化 ====================

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
}
