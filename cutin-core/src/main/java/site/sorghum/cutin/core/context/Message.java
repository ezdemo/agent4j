package site.sorghum.cutin.core.context;

import site.sorghum.cutin.core.tool.ToolCall;

import java.util.List;
import java.util.Map;

/**
 * 对话消息，是模型请求与快照持久化的基本单位。
 *
 * <p>支持普通文本消息、带工具调用 id 的 tool 结果消息，以及包含多个工具调用的
 * assistant 消息；额外的协议字段统一放在 {@code metadata} 中。</p>
 */
public record Message(
    String role,
    String content,
    String toolCallId,
    List<ToolCall> toolCalls,
    Map<String, Object> metadata
) {

    /** 构造一条无工具信息、无元数据的普通消息。 */
    public Message(String role, String content) {
        this(role, content, null, List.of(), Map.of());
    }

    /** 构造一条携带工具调用 id 与工具调用列表的消息。 */
    public Message(String role, String content, String toolCallId, List<ToolCall> toolCalls) {
        this(role, content, toolCallId, toolCalls, Map.of());
    }

    /** 记录构造校验：对工具调用与元数据做不可变拷贝，避免外部修改。 */
    public Message {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** 是否携带至少一个工具调用。 */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    /** 按 key 读取元数据，不存在时返回 null。 */
    public Object metadata(String key) {
        return metadata.get(key);
    }

    /** 在保留原消息内容的基础上合并一个新的元数据键值对。 */
    public Message withMetadata(String key, Object value) {
        Map<String, Object> merged = new java.util.HashMap<>(metadata);
        merged.put(key, value);
        return new Message(role, content, toolCallId, toolCalls, merged);
    }
}
