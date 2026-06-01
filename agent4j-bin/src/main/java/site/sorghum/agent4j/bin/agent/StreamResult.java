package site.sorghum.agent4j.bin.agent;

import org.noear.snack4.ONode;

/**
 * 流式调用结果封装 —— 从 AgentLoop 提取为独立类型。
 *
 * @param content         模型回复内容
 * @param reasoningContent 推理内容
 * @param toolCalls       工具调用（ONode 数组）
 * @param error           是否发生错误
 * @param loopAborted     是否因推理循环被断路器终止
 * @author Sorghum
 */
record StreamResult(String content, String reasoningContent, ONode toolCalls,
                    boolean error, boolean loopAborted) {
    StreamResult(String content, String reasoningContent, ONode toolCalls, boolean error) {
        this(content, reasoningContent, toolCalls, error, false);
    }
}
