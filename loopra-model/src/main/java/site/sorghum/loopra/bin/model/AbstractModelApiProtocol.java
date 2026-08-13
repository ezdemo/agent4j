package site.sorghum.loopra.bin.model;

import lombok.extern.slf4j.Slf4j;
import org.noear.snack4.ONode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shared decoding utilities for OpenAI-compatible API protocols.
 */
@Slf4j
abstract class AbstractModelApiProtocol implements ModelApiProtocol {

    protected static final String CONTENT = "content";
    protected static final String REASONING_CONTENT = "reasoning_content";
    protected static final String FUNCTION = "function";
    protected static final String NAME = "name";
    protected static final String ARGUMENTS = "arguments";
    protected static final String ID = "id";
    protected static final String TYPE = "type";
    protected static final String MODEL = "model";
    protected static final String TOOLS = "tools";
    protected static final String USAGE = "usage";

    protected void processContentToken(String token, ModelClient.StreamCallback callback,
                                       ModelApiStreamState state) {
        String lower = token.toLowerCase();
        if (state.inThinkContent) {
            int endIndex = lower.indexOf("</think>");
            if (endIndex >= 0) {
                if (endIndex > 0) {
                    String reasoning = token.substring(0, endIndex);
                    safeCallback("onReasoningDelta", () -> callback.onReasoningDelta(reasoning));
                }
                state.inThinkContent = false;
                String after = token.substring(endIndex + 8);
                if (!after.isEmpty()) processContentToken(after, callback, state);
            } else {
                safeCallback("onReasoningDelta", () -> callback.onReasoningDelta(token));
            }
            return;
        }

        int startIndex = lower.indexOf("<think>");
        if (startIndex >= 0) {
            if (startIndex > 0) {
                String before = token.substring(0, startIndex);
                safeCallback("onContentDelta", () -> callback.onContentDelta(before));
            }
            state.inThinkContent = true;
            String after = token.substring(startIndex + 7);
            if (!after.isEmpty()) processContentToken(after, callback, state);
        } else {
            safeCallback("onContentDelta", () -> callback.onContentDelta(token));
        }
    }

    protected void handleUsage(ONode usage, String requestId, ModelClient.StreamCallback callback,
                               Map<String, int[]> lastUsage) {
        int[] values = parseUsage(usage);
        if (requestId != null) {
            int[] previous = lastUsage.put(requestId, values);
            if (previous != null) {
                int prompt = values[0] - previous[0];
                int completion = values[1] - previous[1];
                int cacheHit = values[2] - previous[2];
                int cacheMiss = values[3] - previous[3];
                int total = values[4] - previous[4];
                if (prompt == 0 && completion == 0 && cacheHit == 0 && cacheMiss == 0) {
                    log.debug("usage数据无变化，跳过，requestId={}", requestId);
                } else {
                    log.debug("usage增量更新: requestId={}, +prompt={}, +completion={}, +cacheHit={}, +cacheMiss={}",
                            requestId, prompt, completion, cacheHit, cacheMiss);
                    safeCallback("onUsage", () -> callback.onUsage(
                            prompt, completion, total, cacheHit, cacheMiss));
                }
                return;
            }
        }

        ONode details = usage.get("completion_tokens_details");
        if (details == null || details.isNull()) details = usage.get("output_tokens_details");
        if (details != null && !details.isNull()) {
            int reasoningTokens = details.get("reasoning_tokens").isNull()
                    ? 0 : details.get("reasoning_tokens").getInt();
            if (reasoningTokens > 0) log.debug("推理 token 消耗: {}", reasoningTokens);
        }
        log.debug("收到usage数据: prompt={}, completion={}, cacheHit={}, cacheMiss={}",
                values[0], values[1], values[2], values[3]);
        safeCallback("onUsage", () -> callback.onUsage(
                values[0], values[1], values[4], values[2], values[3]));
    }

    @Override
    public void completeStream(ModelApiStreamState state, ModelClient.StreamCallback callback) {
        emitToolCalls(state.toolCalls, state.responseReasoning, callback);
    }

    protected void safeCallback(String name, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.debug("{}回调异常（可能SSE连接已断开）: {}", name, e.getMessage());
        }
    }

    private int[] parseUsage(ONode usage) {
        int prompt = usageInt(usage, "prompt_tokens");
        if (prompt < 0) prompt = usageInt(usage, "input_tokens");
        if (prompt < 0) prompt = 0;
        int completion = usageInt(usage, "completion_tokens");
        if (completion < 0) completion = usageInt(usage, "output_tokens");
        if (completion < 0) completion = 0;
        int total = usageInt(usage, "total_tokens");
        if (total < 0) total = prompt + completion;
        int cacheHit = Math.max(0, usageInt(usage, "prompt_cache_hit_tokens"));
        int cacheMiss = Math.max(0, usageInt(usage, "prompt_cache_miss_tokens"));
        if (cacheHit == 0 && cacheMiss == 0) {
            // Anthropic: 缓存读写量位于顶层 cache_read/cache_creation 字段
            int anthropicHit = Math.max(0, usageInt(usage, "cache_read_input_tokens"));
            int anthropicCreated = Math.max(0, usageInt(usage, "cache_creation_input_tokens"));
            if (anthropicHit > 0 || anthropicCreated > 0) {
                cacheHit = anthropicHit;
                cacheMiss = anthropicCreated > 0 ? anthropicCreated : Math.max(0, prompt - cacheHit);
            } else {
                ONode details = usage.get("prompt_tokens_details");
                if (details == null || details.isNull()) details = usage.get("input_tokens_details");
                if (details != null && !details.isNull()) {
                    cacheHit = details.get("cached_tokens").isNull() ? 0 : details.get("cached_tokens").getInt();
                    cacheMiss = Math.max(0, prompt - cacheHit);
                }
            }
        }
        return new int[]{prompt, completion, cacheHit, cacheMiss, total};
    }

    private static int usageInt(ONode usage, String key) {
        ONode node = usage.get(key);
        return node == null || node.isNull() ? -1 : node.getInt();
    }

    private void emitToolCalls(ONode accumulated, String responseReasoning,
                               ModelClient.StreamCallback callback) {
        if (accumulated == null) return;
        List<ONode> valid = new ArrayList<>();
        for (ONode toolCall : accumulated.getArray()) {
            ONode function = toolCall.get(FUNCTION);
            if (function != null && !function.isNull()) {
                String name = function.get(NAME).getString();
                if (name != null && !name.isEmpty()) valid.add(toolCall);
            }
        }
        if (valid.isEmpty()) return;

        ONode filtered = ONode.ofJson("[]").asArray();
        for (ONode toolCall : valid) {
            ONode copy = filtered.addNew();
            copy.set(ID, toolCall.get(ID).isNull() ? "" : toolCall.get(ID).getString());
            copy.set(TYPE, FUNCTION);
            if (responseReasoning != null) copy.set("response_reasoning", responseReasoning);
            ONode copyFunction = copy.getOrNew(FUNCTION);
            copyFunction.set(NAME, toolCall.get(FUNCTION).get(NAME).getString());
            copyFunction.set(ARGUMENTS, toolCall.get(FUNCTION).get(ARGUMENTS).getString());
        }
        log.debug("完成tool_calls累积，共 {} 个有效调用", valid.size());
        safeCallback("onToolCalls", () -> callback.onToolCalls(filtered));
    }
}
