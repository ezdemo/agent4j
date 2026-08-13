package site.sorghum.loopra.bin.agent.model;

import org.noear.snack4.ONode;

import java.util.List;

/**
 * 流式调用结果封装。
 * <p>
 * 由 {@code streamLLM} 返回，包含模型回复的内容、思考过程和工具调用。
 * </p>
 *
 * @param content         模型回复的文本内容（可能为 null）
 * @param reasoningContent 模型的思考过程（推理模型的 reasoning_content，可能为 null）
 * @param toolCalls       工具调用列表 ONode（可能为 null）
 * @param error           是否发生流式错误
 * @param loopAborted     是否因推理断路器提前终止
 * @param errorMessage    流式错误原文（可能为 null）
 * @param thinkingBlocks  原始 thinking/redacted_thinking 块 JSON 列表（Anthropic 协议，可能为 null）
 */
public record StreamResult(String content, String reasoningContent, ONode toolCalls,
                           boolean error, boolean loopAborted, String errorMessage,
                           List<String> thinkingBlocks) {

    /**
     * 无推理断路器终止和错误详情的快捷构造。
     */
    public StreamResult(String content, String reasoningContent, ONode toolCalls, boolean error) {
        this(content, reasoningContent, toolCalls, error, false, null, null);
    }

    /**
     * 保留错误详情的快捷构造。
     */
    public StreamResult(String content, String reasoningContent, ONode toolCalls,
                        boolean error, String errorMessage) {
        this(content, reasoningContent, toolCalls, error, false, errorMessage, null);
    }

    /**
     * 兼容原有的推理断路器构造。
     */
    public StreamResult(String content, String reasoningContent, ONode toolCalls,
                        boolean error, boolean loopAborted) {
        this(content, reasoningContent, toolCalls, error, loopAborted, null, null);
    }
}
