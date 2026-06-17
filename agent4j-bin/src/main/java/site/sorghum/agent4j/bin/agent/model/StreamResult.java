package site.sorghum.agent4j.bin.agent.model;

import org.noear.snack4.ONode;

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
 */
public record StreamResult(String content, String reasoningContent, ONode toolCalls,
                           boolean error, boolean loopAborted) {

    /**
     * 无推理断路器终止的快捷构造。
     */
    public StreamResult(String content, String reasoningContent, ONode toolCalls, boolean error) {
        this(content, reasoningContent, toolCalls, error, false);
    }
}
