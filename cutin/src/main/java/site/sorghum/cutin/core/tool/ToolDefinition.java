package site.sorghum.cutin.core.tool;

import java.util.Map;

/**
 * 工具定义，描述工具的 id、说明、输入 JSON Schema 与附加元数据。
 *
 * <p>定义会被 Provider 转换成各家协议（OpenAI function、Anthropic tool 等）的
 * 工具声明，因此内容需要保持协议中立。</p>
 */
public record ToolDefinition(
    String id,
    String description,
    Map<String, Object> inputSchema,
    ToolMetadata metadata
) {

    /** 使用默认元数据创建工具定义。 */
    public ToolDefinition(String id, String description, Map<String, Object> inputSchema) {
        this(id, description, inputSchema, ToolMetadata.DEFAULT);
    }

    /** 记录构造校验：对输入 Schema 与元数据做不可变拷贝。 */
    public ToolDefinition {
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema);
        metadata = metadata == null ? ToolMetadata.DEFAULT : metadata;
    }

    /** 生成携带指定元数据的新工具定义。 */
    public ToolDefinition withMetadata(ToolMetadata metadata) {
        return new ToolDefinition(id, description, inputSchema, metadata);
    }
}
