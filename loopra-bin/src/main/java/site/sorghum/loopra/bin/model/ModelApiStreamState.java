package site.sorghum.loopra.bin.model;

import org.noear.snack4.ONode;

import java.util.HashMap;
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
    boolean aborted;
    boolean completed;
    String responseReasoning;
    ONode toolCalls;
    final Map<String, int[]> lastUsage = new HashMap<>();
    boolean inThinkContent;
}
