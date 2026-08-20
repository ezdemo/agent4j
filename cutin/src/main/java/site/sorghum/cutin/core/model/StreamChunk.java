package site.sorghum.cutin.core.model;

import site.sorghum.cutin.core.context.Usage;
import site.sorghum.cutin.core.tool.ToolCall;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 与业务无关的模型流式增量块。
 *
 * <p>插件可以通过 {@code ON_MODEL_STREAM} 拦截点返回修改后的 {@link StreamChunk}
 * 来替换某个增量块。Provider 在流结束时发出终止块；若 Provider 未发出，
 * 网关会自动合成一个终止块，以便 {@code AFTER_MODEL} 插件检查完整响应。</p>
 */
public record StreamChunk(
    String content,
    String reasoning,
    List<ToolCall> toolCalls,
    List<String> thinkingBlocks,
    Usage usage,
    Set<ModelStreamPhase> phases,
    Map<String, Object> metadata,
    boolean terminal
) {

    /** 快捷构造一个只有正文内容的非终止增量块。 */
    public StreamChunk(String content, Usage usage) {
        this(content, null, List.of(), List.of(), usage, Set.of(), Map.of(), false);
    }

    /** 兼容既有构造方式；未显式指定时没有生命周期阶段。 */
    public StreamChunk(String content, String reasoning, List<ToolCall> toolCalls,
                       List<String> thinkingBlocks, Usage usage,
                       Map<String, Object> metadata, boolean terminal) {
        this(content, reasoning, toolCalls, thinkingBlocks, usage, Set.of(), metadata, terminal);
    }

    /** 记录构造校验：对列表与元数据做不可变拷贝，用量为空时使用零值。 */
    public StreamChunk {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        thinkingBlocks = thinkingBlocks == null ? List.of() : List.copyOf(thinkingBlocks);
        phases = phases == null ? Set.of() : Set.copyOf(phases);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        usage = usage == null ? Usage.ZERO : usage;
    }

    /** 生成正文不同的新增量块。 */
    public StreamChunk withContent(String newContent) {
        return new StreamChunk(newContent, reasoning, toolCalls, thinkingBlocks, usage, phases, metadata, terminal);
    }

    /** 生成推理内容不同的新增量块。 */
    public StreamChunk withReasoning(String newReasoning) {
        return new StreamChunk(content, newReasoning, toolCalls, thinkingBlocks, usage, phases, metadata, terminal);
    }

    /** 生成工具调用列表不同的新增量块。 */
    public StreamChunk withToolCalls(List<ToolCall> newToolCalls) {
        return new StreamChunk(content, reasoning, newToolCalls, thinkingBlocks, usage, phases, metadata, terminal);
    }

    /** 合并一个元数据键值对，生成新的增量块。 */
    public StreamChunk withMetadata(String key, Object value) {
        Map<String, Object> merged = new java.util.HashMap<>(metadata);
        merged.put(key, value);
        return new StreamChunk(content, reasoning, toolCalls, thinkingBlocks, usage, phases, merged, terminal);
    }

    /** 合并一个强类型生命周期阶段，生成新的增量块。 */
    public StreamChunk withPhase(ModelStreamPhase phase) {
        Set<ModelStreamPhase> merged = new java.util.HashSet<>(phases);
        merged.add(phase);
        return new StreamChunk(content, reasoning, toolCalls, thinkingBlocks, usage, merged, metadata, terminal);
    }

    /** 将该增量块标记为终止块。 */
    public StreamChunk asTerminal() {
        return new StreamChunk(content, reasoning, toolCalls, thinkingBlocks, usage, phases, metadata, true);
    }
}
