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
    boolean emittedOutput;
    boolean aborted;
    boolean completed;
    String responseReasoning;
    ONode toolCalls;
    final Map<String, int[]> lastUsage = new HashMap<>();
    boolean inThinkContent;
}
