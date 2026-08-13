package site.sorghum.loopra.bin.model;

import org.noear.snack4.ONode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable state accumulated while decoding one SSE response.
 */
final class ModelApiStreamState {
    String errorData;
    boolean retryableError = true;
    /** 上下文超限：可恢复，但必须由上层折叠历史后重试。 */
    boolean contextLengthExceeded;
    /** Responses API 拒绝回放的历史 item，可净化请求后重试一次。 */
    boolean invalidRequestError;
    boolean emittedOutput;
    boolean emittedReasoning;
    boolean aborted;
    boolean completed;
    String responseReasoning;
    ONode toolCalls;
    /** Anthropic message_start 携带的输入侧用量，待 message_delta 与输出侧合并后上报。 */
    ONode anthropicInputUsage;
    /** Anthropic 流中已完成的 thinking/redacted_thinking 原始块 JSON，保序。 */
    final List<String> anthropicThinkingBlocks = new ArrayList<>();
    /** 正在累积的 thinking/redacted_thinking 块（流中同一时刻至多一个活跃块）。 */
    ONode anthropicActiveThinkingBlock;
    final Map<String, int[]> lastUsage = new HashMap<>();
    boolean inThinkContent;
}
