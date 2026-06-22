package site.sorghum.agent4j.bin.model;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.noear.snack4.ONode;
import site.sorghum.agent4j.bin.agent.model.ChatMessage;
import site.sorghum.agent4j.bin.agent.model.ToolCallEntry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI 兼容 API 的 HTTP 客户端 —— {@link ModelClient} 的 OkHttp 实现。
 * <p>
 * 支持非流式 ({@link ModelClient#chat}) 和流式 ({@link ModelClient#chatStream}) 两种调用。
 * 底层基于 OkHttp，支持连接池、超时控制和请求中断。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
public class HttpModelClient implements ModelClient {

    private static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json; charset=utf-8");

    /**
     * 重试间隔（秒），共 10 次：3,3,5,5,8,10,12,16,22,36 — 总计约 120 秒（2 分钟）。
     * 指数退避策略，应对 API 临时故障。
     */
    private static final int[] RETRY_DELAYS = {3, 3, 5, 5, 8, 10, 12, 16, 22, 36};

    // ==================== OpenAI API JSON 字段名常量 ====================

    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_REASONING_CONTENT = "reasoning_content";
    private static final String FIELD_REASONING_CONTENT_V2 = "reasoning";
    private static final String FIELD_TOOL_CALLS = "tool_calls";
    private static final String FIELD_CHOICES = "choices";
    private static final String FIELD_ROLE = "role";
    private static final String FIELD_FUNCTION = "function";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_ARGUMENTS = "arguments";
    private static final String FIELD_ID = "id";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_MODEL = "model";
    private static final String FIELD_MESSAGES = "messages";
    private static final String FIELD_TOOLS = "tools";
    private static final String FIELD_USAGE = "usage";
    private static final String FIELD_REASONING_TOKENS = "reasoning_tokens";
    private static final String FIELD_CACHED_TOKENS = "cached_tokens";
    private static final String FIELD_STREAM = "stream";
    private static final String FIELD_MESSAGE = "message";
    private static final String FIELD_INDEX = "index";

    // ==================== Usage 相关字段常量 ====================

    private static final String FIELD_PROMPT_TOKENS = "prompt_tokens";
    private static final String FIELD_COMPLETION_TOKENS = "completion_tokens";
    private static final String FIELD_TOTAL_TOKENS = "total_tokens";
    private static final String FIELD_PROMPT_TOKENS_DETAILS = "prompt_tokens_details";
    private static final String FIELD_COMPLETION_TOKENS_DETAILS = "completion_tokens_details";
    private static final String FIELD_PROMPT_CACHE_HIT_TOKENS = "prompt_cache_hit_tokens";
    private static final String FIELD_PROMPT_CACHE_MISS_TOKENS = "prompt_cache_miss_tokens";

    // ==================== 核心字段 ====================

    private final String apiUrl;
    private final String apiKey;
    /**
     * reasoning_effort 取值: low / medium / high / max
     */
    private String reasoningEffort;
    private volatile String model;
    /**
     * 流式中断标志（ReasonBreaker 触发时设置）
     */
    private volatile boolean abortRequested = false;
    /**
     * 当前活跃的 OkHttp Call（用于中断）
     */
    private volatile Call activeCall;

    /**
     * OkHttp 客户端（带连接池和超时配置）
     */
    private final OkHttpClient client;

    /**
     * 上下文大小提供者（可选，用于从外部源获取模型的上下文大小）
     */
    @Getter
    @Setter
    private static volatile ContextSizeProvider contextSizeProvider;

    public HttpModelClient(String apiUrl, String apiKey, String model) {
        this(apiUrl, apiKey, model, "high");
    }

    public HttpModelClient(String apiUrl, String apiKey, String model, String reasoningEffort) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.reasoningEffort = reasoningEffort;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.MINUTES)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false) // 由我们自己的重试逻辑控制
                .build();
    }

    /**
     * 判断 HTTP 状态码是否应重试。
     * 5xx 服务端错误、429 限流或 0（连接失败）需要重试，其他 4xx 客户端错误不重试。
     */
    private static boolean retryable(int status) {
        return status >= 500 || status == 429 || status == 0;
    }

    // ==================== 统一重试机制 ====================

    /**
     * 重试上下文 — 封装重试状态和控制逻辑。
     * <p>
     * 使用方式：
     * <pre>{@code
     * RetryContext retry = new RetryContext("流式");
     * for (int attempt = 0; ; attempt++) {
     *     try {
     *         // 执行请求...
     *         if (retryable(status)) {
     *             retry.waitOrThrow("HTTP " + status, attempt);
     *             continue;
     *         }
     *         return result;
     *     } catch (IOException e) {
     *         retry.waitOrThrow(e, attempt);
     *         continue;
     *     }
     * }
     * }</pre>
     * </p>
     */
    private class RetryContext {
        private final String tag;
        /**
         * 是否为流式调用（流式调用需要检查 SSE 连接状态）
         */
        private final boolean streamMode;

        RetryContext(String tag) {
            this(tag, false);
        }

        RetryContext(String tag, boolean streamMode) {
            this.tag = tag;
            this.streamMode = streamMode;
        }

        /**
         * 检查是否应该重试，如果是则等待并返回；否则抛出异常。
         *
         * @param reason  重试原因（用于日志）
         * @param attempt 当前尝试次数（从 0 开始）
         * @throws IOException 如果不应重试或等待被中断
         */
        void waitOrThrow(String reason, int attempt) throws IOException {
            if (abortRequested) {
                log.debug("[{}] {} 可重试，但已请求中断，跳过重试", tag, reason);
                abortRequested = false;
                throw new IOException("Request aborted by user");
            }
            // 流式模式下检查 SSE 连接是否还活着
            if (streamMode && !isSseAlive()) {
                log.debug("[{}] {} 可重试，但 SSE 连接已断开，跳过重试", tag, reason);
                throw new IOException("SSE connection closed");
            }
            if (attempt >= RETRY_DELAYS.length) {
                throw new IOException("[" + tag + "] 重试耗尽: " + reason);
            }
            int delay = RETRY_DELAYS[attempt];
            log.warn("[retry][{}] {}，第{}次重试，等待{}s...", tag, reason, attempt + 1, delay);
            doSleep(delay);
            checkAbort();
        }

        /**
         * 检查是否应该重试（IO 异常版本），如果是则等待并返回；否则抛出异常。
         *
         * @param e       捕获的 IO 异常
         * @param attempt 当前尝试次数（从 0 开始）
         * @throws IOException 如果不应重试或等待被中断
         */
        void waitOrThrow(IOException e, int attempt) throws IOException {
            if (abortRequested) {
                log.debug("[{}] IO异常，但已请求中断，跳过重试", tag);
                abortRequested = false;
                throw new IOException("Request aborted by user", e);
            }
            // 流式模式下检查 SSE 连接是否还活着
            if (streamMode && !isSseAlive()) {
                log.debug("[{}] IO异常，但 SSE 连接已断开，跳过重试", tag);
                throw new IOException("SSE connection closed", e);
            }
            if (attempt >= RETRY_DELAYS.length) {
                throw e;
            }
            int delay = RETRY_DELAYS[attempt];
            log.warn("[retry][{}] {}，第{}次重试，等待{}s...", tag, e.getMessage(), attempt + 1, delay);
            doSleep(delay);
            checkAbort();
        }

        /**
         * 等待指定秒数，响应中断。
         */
        private void doSleep(int delaySec) throws IOException {
            try {
                Thread.sleep(delaySec * 1000L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted during retry", ie);
            }
        }

        /**
         * 等待后检查是否收到中断请求。
         */
        private void checkAbort() throws IOException {
            if (abortRequested) {
                log.debug("[{}] 重试等待期间收到中断请求，跳过重试", tag);
                abortRequested = false;
                throw new IOException("Request aborted by user");
            }
        }
    }

    /**
     * 检查 SSE 连接是否还活着。
     * <p>
     * 通过检查 activeCall 是否被取消来判断 SSE 连接状态。
     * 如果 activeCall 为 null 或已被取消，说明 SSE 连接已断开。
     * </p>
     *
     * @return true 如果 SSE 连接还活着，false 如果已断开
     */
    private boolean isSseAlive() {
        Call call = activeCall;
        return call != null && !call.isCanceled();
    }

    /**
     * 安全执行回调，捕获异常防止 SSE 连接断开时影响主流程。
     *
     * @param name   回调名称（用于日志）
     * @param action 回调动作
     */
    private void safeCallback(String name, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.debug("{}回调异常（可能SSE连接已断开）: {}", name, e.getMessage());
        }
    }

    @Override
    public String getModel() {
        return model;
    }

    /**
     * 设置模型名称（运行时切换）。
     */
    @Override
    public void setModel(String model) {
        this.model = model;
    }

    /**
     * 设置推理强度（运行时切换）。
     * 取值: low / medium / high / max
     */
    @Override
    public void setReasoningEffort(String reasoningEffort) {
        this.reasoningEffort = reasoningEffort;
    }

    /**
     * 中断当前流式请求（ReasonBreaker 触发时调用）
     */
    @Override
    public void abortStream() {
        abortRequested = true;
        Call call = activeCall;
        if (call != null && !call.isCanceled()) {
            call.cancel();
            log.debug("已取消当前 OkHttp Call");
        }
    }

    /**
     * 模型最大上下文窗口 token 数。
     * 优先级：环境变量 AGENT4J_MAX_CONTEXT_TOKENS > 模型名后缀 [大小] > 模型名推断 > 默认 256K。
     * <p>
     * 支持模型名后缀配置上下文大小，例如：
     * <ul>
     *   <li>mimo-v2.5[512k] → 512,000 tokens</li>
     *   <li>mimo-v2.5[1m] → 1,000,000 tokens</li>
     *   <li>deepseek-v4-pro[2m] → 2,000,000 tokens</li>
     * </ul>
     * 后缀格式：[数字k] 或 [数字m]（不区分大小写）
     * </p>
     */
    @Override
    public int getMaxContextTokens() {
        // 环境变量覆盖
        String env = System.getenv("AGENT4J_MAX_CONTEXT_TOKENS");
        if (env != null && !env.isEmpty()) {
            try {
                return Integer.parseInt(env);
            } catch (NumberFormatException ignored) {
            }
        }
        if (model == null) return 200_000;

        // 检查模型名后缀 [大小] 配置（最高优先级）
        int suffixSize = ModelContextUtils.parseContextSizeSuffix(model);
        if (suffixSize > 0) {
            return suffixSize;
        }

        // 从上下文大小提供者获取（次优先级，如 ModelMetaService）
        if (contextSizeProvider != null) {
            int providerSize = contextSizeProvider.getContextSize(model);
            if (providerSize > 0) {
                return providerSize;
            }
        }
        // 默认
        return 256_000;
    }

    /**
     * 非流式调用（用于 fold 摘要、/compact 等后台操作），5xx 自动重试最多 10 次
     */
    @Override
    public ONode chat(List<ChatMessage> messages,
                      ONode tools) throws IOException {
        String jsonBody = buildBody(messages, tools);
        ONode bodyWithStream = ONode.ofJson(jsonBody);
        bodyWithStream.set(FIELD_STREAM, false);
        jsonBody = bodyWithStream.toJson();

        RetryContext retry = new RetryContext("非流式");
        for (int attempt = 0; ; attempt++) {
            Request request = new Request.Builder()
                    .url(apiUrl)
                    .post(RequestBody.create(jsonBody, MEDIA_TYPE_JSON))
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                int status = response.code();
                ResponseBody responseBody = response.body();
                String responseText = responseBody != null ? responseBody.string() : "";

                log.debug("发送API请求到 {}，模型: {}，消息数: {}，工具数: {}",
                        apiUrl, model, messages.size(), tools != null ? tools.size() : 0);
                log.debug("收到API响应（完整响应）: {}", responseText);

                if (retryable(status)) {
                    retry.waitOrThrow("HTTP " + status, attempt);
                    continue;
                }

                if (status >= 400) {
                    throw new IOException("API error " + status + ": " + responseText);
                }

                ONode resp = ONode.ofJson(responseText);
                ONode choices = resp.get(FIELD_CHOICES);
                if (choices == null || !choices.isArray() || choices.getArray().isEmpty()) {
                    throw new IOException("No choices in response: " + responseText);
                }
                return choices.get(0).get(FIELD_MESSAGE);

            } catch (IOException e) {
                log.error("非流式API调用IO异常: {}", e.getMessage(), e);
                retry.waitOrThrow(e, attempt);
                continue;
            }
        }
    }

    /**
     * 流式调用 — 通过回调逐 token 推送，5xx / IO 异常自动重试最多 10 次。
     * <p>
     * 解析 OpenAI SSE 格式：
     * {@code data: {"choices":[{"delta":{FIELD_CONTENT:"..."}}]}}
     * 支持 reasoning_content 和 tool_calls。
     * </p>
     */
    @Override
    public void chatStream(List<ChatMessage> messages,
                           ONode tools,
                           StreamCallback callback) {
        String jsonBody;
        try {
            jsonBody = buildBody(messages, tools);
            ONode bodyWithStream = ONode.ofJson(jsonBody);
            bodyWithStream.set(FIELD_STREAM, true);
            jsonBody = bodyWithStream.toJson();
        } catch (Exception e) {
            callback.onError(e.getMessage());
            return;
        }

        RetryContext retry = new RetryContext("流式", true);
        for (int attempt = 0; attempt <= RETRY_DELAYS.length; attempt++) {
            Request request = new Request.Builder()
                    .url(apiUrl)
                    .post(RequestBody.create(jsonBody, MEDIA_TYPE_JSON))
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .build();

            Call call = client.newCall(request);
            activeCall = call;

            try (Response response = call.execute()) {
                log.debug("发送流式API请求到 {}，模型: {}，消息数: {}，工具数: {}",
                        apiUrl, model, messages.size(), tools != null ? tools.size() : 0);

                int status = response.code();
                if (retryable(status)) {
                    try {
                        retry.waitOrThrow("HTTP " + status, attempt);
                    } catch (IOException e) {
                        // 用户中断或重试耗尽
                        safeCallback("onDone", callback::onDone);
                        return;
                    }
                    activeCall = null;
                    continue;
                }
                if (status >= 400) {
                    ResponseBody errorBody = response.body();
                    String err = errorBody != null ? errorBody.string() : "unknown error";
                    safeCallback("onError", () -> callback.onError("API error " + status + ": " + err));
                    return;
                }

                ResponseBody body = response.body();
                if (body == null) {
                    safeCallback("onError", () -> callback.onError("Empty response body"));
                    return;
                }

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8))) {
                    SseParseResult parseResult = processSseStream(reader, callback);

                    // ★ SSE流错误重试逻辑
                    if (parseResult.sseErrorData != null) {
                        try {
                            retry.waitOrThrow("SSE流错误: " + parseResult.sseErrorData, attempt);
                        } catch (IOException e) {
                            // 用户中断或重试耗尽
                            safeCallback("onDone", callback::onDone);
                            return;
                        }
                        continue; // 关闭当前response，继续外层for循环重试
                    }

                    if (parseResult.toolCallsAccum != null) {
                        emitToolCalls(parseResult.toolCallsAccum, callback);
                    }
                    safeCallback("onDone", callback::onDone);
                }
                return; // success

            } catch (IOException e) {
                if (abortRequested) {
                    // 主动中断，不重试，不报错
                    // ★ 必须回调 onDone() 释放 AgentLoop.streamLLM() 中的 streamLatch，
                    //    否则调用方线程会永久阻塞在 CountDownLatch.await() 上，
                    //    导致 AgentService 的会话锁（ReentrantLock）永远无法释放。
                    log.debug("流式请求已被中断，跳过重试");
                    abortRequested = false;
                    safeCallback("onDone", callback::onDone);
                    return;
                }
                log.error("流式API调用IO异常: {}", e.getMessage(), e);
                try {
                    retry.waitOrThrow(e, attempt);
                } catch (IOException retryEx) {
                    // 重试耗尽或中断
                    safeCallback("onError", () -> callback.onError(retryEx.getMessage()));
                    return;
                }
                continue;
            } catch (Exception e) {
                log.error("流式API调用异常: {}", e.getMessage(), e);
                // 非 IO 异常（如 JSON 解析错误），不重试
                safeCallback("onError", () -> callback.onError(e.getMessage()));
                return;
            } finally {
                activeCall = null;
            }
        }
    }


    /**
     * SSE 流解析结果。
     */
    private static class SseParseResult {
        String sseErrorData;
        ONode toolCallsAccum;
        /** 每个请求 ID 上次报告的 usage 值，用于计算差量（某些平台会在同一流中多次发送 usage） */
        final Map<String, int[]> lastUsage = new HashMap<>();
    }

    /**
     * 处理 SSE 流，解析所有数据行。
     *
     * @param reader   SSE 流的 BufferedReader
     * @param callback 流式回调
     * @return 解析结果（包含错误数据和累积的 tool_calls）
     */
    private SseParseResult processSseStream(BufferedReader reader, StreamCallback callback) throws IOException {
        SseParseResult result = new SseParseResult();
        String line;
        while ((line = reader.readLine()) != null) {
            if (abortRequested) {
                abortRequested = false;
                log.debug("流式请求被 ReasonBreaker 中断");
                break;
            }
            if (!line.startsWith("data: ")) continue;
            String data = line.substring(6).trim();
            if ("[DONE]".equals(data)) {
                log.debug("收到SSE流结束标记");
                break;
            }
            if (data.trim().startsWith("{\"error\":")) {
                log.warn("收到SSE流错误（可重试）: {}", data);
                result.sseErrorData = data;
                break;
            }
            ONode chunk = ONode.ofJson(data);
            log.debug("收到SSE数据块，大小: {} 字符", data.length());

            processChunk(chunk, callback, result);
        }
        return result;
    }

    /**
     * 处理单个 SSE 数据块。
     *
     * @param chunk    解析后的 JSON 数据块
     * @param callback 流式回调
     * @param result   累积结果（用于 tool_calls）
     */
    private void processChunk(ONode chunk, StreamCallback callback, SseParseResult result) {
        // 捕获 usage
        ONode usage = chunk.get(FIELD_USAGE);
        if (usage != null && !usage.isNull()) {
            handleUsage(usage, chunk.get(FIELD_ID).getString(), callback, result.lastUsage);
        }

        // delta 处理
        ONode delta = chunk.select("$.choices[0].delta");
        if (delta == null || delta.isNull()) return;

        // reasoning content
        ONode rd = delta.get(FIELD_REASONING_CONTENT) == null ? delta.get(FIELD_REASONING_CONTENT_V2) : delta.get(FIELD_REASONING_CONTENT);
        // 回设
        delta.set(FIELD_REASONING_CONTENT, rd);
        if (rd != null && rd.isString()) {
            String tok = rd.getString();
            if (tok != null && !tok.isEmpty()) {
                log.debug("收到reasoning_content: {}", tok);
                safeCallback("onReasoningDelta", () -> callback.onReasoningDelta(tok));
            }
        }

        // content
        ONode cd = delta.get(FIELD_CONTENT);
        if (cd != null && cd.isString()) {
            String tok = cd.getString();
            if (tok != null && !tok.isEmpty()) {
                log.debug("收到content: {}", tok);
                safeCallback("onContentDelta", () -> callback.onContentDelta(tok));
            }
        }

        // tool_calls 累积
        ONode tcDelta = delta.get(FIELD_TOOL_CALLS);
        if (tcDelta != null && tcDelta.isArray()) {
            log.debug("收到tool_calls数据，数量: {}", tcDelta.getArray().size());
            accumulateToolCalls(tcDelta, result);
        }
    }

    /**
     * 从 usage ONode 解析出 [prompt, completion, cacheHit, cacheMiss, total]。
     */
    private int[] parseUsage(ONode usage) {
        int pt = usage.get(FIELD_PROMPT_TOKENS).isNull() ? 0 : usage.get(FIELD_PROMPT_TOKENS).getInt();
        int ct = usage.get(FIELD_COMPLETION_TOKENS).isNull() ? 0 : usage.get(FIELD_COMPLETION_TOKENS).getInt();
        int tt = usage.get(FIELD_TOTAL_TOKENS).isNull() ? 0 : usage.get(FIELD_TOTAL_TOKENS).getInt();
        int cacheHit = usage.get(FIELD_PROMPT_CACHE_HIT_TOKENS).isNull() ? 0 : usage.get(FIELD_PROMPT_CACHE_HIT_TOKENS).getInt();
        int cacheMiss = usage.get(FIELD_PROMPT_CACHE_MISS_TOKENS).isNull() ? 0 : usage.get(FIELD_PROMPT_CACHE_MISS_TOKENS).getInt();

        if (cacheHit == 0 && cacheMiss == 0) {
            ONode ptDetails = usage.get(FIELD_PROMPT_TOKENS_DETAILS);
            if (ptDetails != null && !ptDetails.isNull()) {
                cacheHit = ptDetails.get(FIELD_CACHED_TOKENS).isNull() ? 0 : ptDetails.get(FIELD_CACHED_TOKENS).getInt();
                cacheMiss = Math.max(0, pt - cacheHit);
            }
        }
        return new int[]{pt, ct, cacheHit, cacheMiss, tt};
    }

    /**
     * 处理 usage 统计数据。
     * 同一请求 ID 多次出现时，仅上报与上次的差量，确保最终累计值与最后一次（最准确的）数据一致。
     */
    private void handleUsage(ONode usage, String requestId, StreamCallback callback,
                             Map<String, int[]> lastUsage) {
        int[] vals = parseUsage(usage);

        // 差量去重：同一 requestId 只上报增量
        if (requestId != null) {
            int[] prev = lastUsage.put(requestId, vals);
            if (prev != null) {
                int dp = vals[0] - prev[0];
                int dc = vals[1] - prev[1];
                int dch = vals[2] - prev[2];
                int dcm = vals[3] - prev[3];
                int dtt = vals[4] - prev[4];
                if (dp == 0 && dc == 0 && dch == 0 && dcm == 0) {
                    log.debug("usage数据无变化，跳过，requestId={}", requestId);
                } else {
                    log.debug("usage增量更新: requestId={}, +prompt={}, +completion={}, +cacheHit={}, +cacheMiss={}",
                            requestId, dp, dc, dch, dcm);
                    safeCallback("onUsage", () -> callback.onUsage(dp, dc, dtt, dch, dcm));
                }
                return;
            }
        }

        // 首次出现，原样上报
        ONode ctDetails = usage.get(FIELD_COMPLETION_TOKENS_DETAILS);
        if (ctDetails != null && !ctDetails.isNull()) {
            int reasoningTokens = ctDetails.get(FIELD_REASONING_TOKENS).isNull() ? 0 : ctDetails.get(FIELD_REASONING_TOKENS).getInt();
            if (reasoningTokens > 0) {
                log.debug("推理 token 消耗: {}", reasoningTokens);
            }
        }

        log.debug("收到usage数据: prompt={}, completion={}, cacheHit={}, cacheMiss={}",
                vals[0], vals[1], vals[2], vals[3]);
        safeCallback("onUsage", () -> callback.onUsage(vals[0], vals[1], vals[4], vals[2], vals[3]));
    }

    /**
     * 累积 tool_calls delta 数据。
     */
    private void accumulateToolCalls(ONode tcDelta, SseParseResult result) {
        for (ONode tcd : tcDelta.getArray()) {
            if (result.toolCallsAccum == null) {
                result.toolCallsAccum = org.noear.snack4.ONode.ofJson("[]").asArray();
            }
            int idx = tcd.get(FIELD_INDEX).isNull() ? 0 : tcd.get(FIELD_INDEX).getInt();
            ONode func = tcd.get(FIELD_FUNCTION);
            while (result.toolCallsAccum.getArray().size() <= idx) {
                result.toolCallsAccum.addNew().set(FIELD_TYPE, FIELD_FUNCTION);
            }
            ONode existing = result.toolCallsAccum.get(idx);
            if (func == null || func.isNull()) {
                continue;
            }

            if (existing.get(FIELD_ID).isNull()) {
                existing.set(FIELD_ID, tcd.get(FIELD_ID).getString());
            }
            if (existing.select("$.function.name").isNull()) {
                existing.getOrNew(FIELD_FUNCTION).set(FIELD_NAME, func.get(FIELD_NAME).getString());
            }
            if (!func.get(FIELD_ARGUMENTS).isNull()) {
                String prev = existing.getOrNew(FIELD_FUNCTION).get(FIELD_ARGUMENTS).getString();
                String add = func.get(FIELD_ARGUMENTS).getString();
                existing.getOrNew(FIELD_FUNCTION).set(FIELD_ARGUMENTS,
                        (prev != null ? prev : "") + (add != null ? add : ""));
            }
            log.debug("tool_calls索引: {}, 函数名: {}", idx,
                    func.get(FIELD_NAME).isNull() ? "null" : func.get(FIELD_NAME).getString());
        }
    }

    /**
     * 过滤并发送累积的 tool_calls。
     *
     * @param toolCallsAccum 累积的 tool_calls
     * @param callback       流式回调
     */
    private void emitToolCalls(ONode toolCallsAccum, StreamCallback callback) {
        // 过滤掉 name 为 null/empty 的 tool call（SSE 分块缺失导致）
        List<ONode> valid = new ArrayList<>();
        for (ONode tc : toolCallsAccum.getArray()) {
            ONode fn = tc.get(FIELD_FUNCTION);
            if (fn != null && !fn.isNull()) {
                ONode nm = fn.get(FIELD_NAME);
                if (nm != null && nm.isString()
                        && nm.getString() != null
                        && !nm.getString().isEmpty()) {
                    valid.add(tc);
                }
            }
        }
        if (!valid.isEmpty()) {
            ONode filtered = org.noear.snack4.ONode.ofJson("[]").asArray();
            for (ONode v : valid) {
                ONode copy = filtered.addNew();
                copy.set(FIELD_ID, v.get(FIELD_ID).isNull() ? "" : v.get(FIELD_ID).getString());
                copy.set(FIELD_TYPE, FIELD_FUNCTION);
                ONode copyFn = copy.getOrNew(FIELD_FUNCTION);
                copyFn.set(FIELD_NAME, v.get(FIELD_FUNCTION).get(FIELD_NAME).getString());
                copyFn.set(FIELD_ARGUMENTS, v.get(FIELD_FUNCTION).get(FIELD_ARGUMENTS).getString());
            }
            log.debug("完成tool_calls累积，共 {} 个有效调用", valid.size());
            safeCallback("onToolCalls", () -> callback.onToolCalls(filtered));
        }
    }

    /**
     * 构建 API 请求体 JSON。
     * 包含 model、messages、tools 等字段，
     * 对 tool 消息做防御性检查（缺少 tool_call_id 时跳过）。
     */
    private String buildBody(List<ChatMessage> messages,
                             ONode tools) {
        ONode body = new ONode(ONode.ofJson("{}").options()).asObject();
        // 剥离模型名称中的上下文大小后缀，例如 "mimo-v2.5[512k]" → "mimo-v2.5"
        body.set(FIELD_MODEL, ModelContextUtils.stripContextSizeSuffix(model));
        if (reasoningEffort != null && !reasoningEffort.isEmpty() && !Objects.equals(reasoningEffort, "none")) {
            body.set("reasoning_effort", reasoningEffort);
            body.set("chat_template_kwargs", ONode.ofJson("{}").set("enable_thinking", true));
            body.set("enable_thinking", true);
        }

        ONode msgs = body.getOrNew(FIELD_MESSAGES).asArray();
        for (ChatMessage m : messages) {
            // 防御：tool 消息必须有 tool_call_id，缺少时跳过该消息
            if (m.isTool() && (m.getToolCallId() == null || m.getToolCallId().isEmpty())) {
                log.warn("buildBody: 跳过没有tool_call_id的tool消息");
                continue;
            }
            // 防御：tool 消息必须有 content，缺少时补齐错误
            if (m.isTool() && (m.getContent() == null || m.getContent().isEmpty())) {
                m.setContent("ERROR 工具执行失败或者工具执行结果为空");
            }

            ONode msg = new ONode();
            msg.set(FIELD_ROLE, m.getRole());
            boolean skip = false;

            // 防御：assistant 消息必须有 content 或 tool_calls（OpenAI/DeepSeek API 要求）
            boolean isAssistant = m.isAssistant();
            boolean isUser = m.isUser();
            boolean hasTc = m.hasToolCalls();
            boolean hasContent = m.hasContent();
            boolean hasContentParts = m.getContentParts() != null && !m.getContentParts().isEmpty();
            boolean hasReasoning = m.getReasoningContent() != null && !m.getReasoningContent().isEmpty();

            if (isUser && !hasContent && !hasReasoning && !hasTc && !hasContentParts) {
                continue;
            }
            // 多模态 contentParts 序列化为 JSON array
            if (hasContentParts) {
                ONode contentArray = msg.getOrNew(FIELD_CONTENT).asArray();
                for (ChatMessage.ContentPart part : m.getContentParts()) {
                    ONode partNode = contentArray.addNew();
                    partNode.set(FIELD_TYPE, part.getType());
                    if ("text".equals(part.getType())) {
                        partNode.set("text", part.getText() != null ? part.getText() : "");
                    } else if ("image_url".equals(part.getType())) {
                        ChatMessage.ContentPart.ImageUrl iu = part.getImageUrl();
                        if (iu != null) {
                            ONode urlNode = partNode.getOrNew("image_url");
                            urlNode.set("url", iu.getUrl() != null ? iu.getUrl() : "");
                            if (iu.getDetail() != null) urlNode.set("detail", iu.getDetail());
                        }
                    }
                }
            } else if (isAssistant && !hasContent && !hasTc) {
                // 既无 content 也无 tool_calls → 强制补空 content 防止 API 400
                // 这种情况不应出现在正常流程中，但历史消息损坏或 Healer 遗漏时兜底
                log.warn("buildBody: 检测到空 assistant 消息（无 content 且无 tool_calls），强制删除");
                msg.set(FIELD_CONTENT, "");
                skip = true;
            } else {
                if (hasContent) msg.set(FIELD_CONTENT, m.getContent());
            }

            if (hasTc) {
                ONode tcArray = msg.getOrNew(FIELD_TOOL_CALLS).asArray();
                for (ToolCallEntry tc : m.getToolCalls()) {
                    ONode tcNode = tcArray.addNew();
                    tcNode.set(FIELD_ID, tc.id());
                    tcNode.set(FIELD_TYPE, FIELD_FUNCTION);
                    ONode funcNode = tcNode.getOrNew(FIELD_FUNCTION);
                    funcNode.set(FIELD_NAME, tc.name());
                    // arguments 可能为 String（API 返回/AgentLoop 构造）或 Map（JSONL 加载后解析），统一转字符串
                    Object argsObj = tc.arguments();
                    String argsStr = "{}";
                    if (argsObj != null) {
                        if (argsObj instanceof String) {
                            argsStr = (String) argsObj;
                        } else {
                            // Map → JSON 字符串
                            argsStr = org.noear.snack4.ONode.serialize(argsObj);
                        }
                    }
                    funcNode.set(FIELD_ARGUMENTS, argsStr);
                }
            }
            if (m.getReasoningContent() != null) msg.set(FIELD_REASONING_CONTENT, m.getReasoningContent());
            if (m.getToolCallId() != null) msg.set("tool_call_id", m.getToolCallId());
            if (!skip) {
                msgs.add(msg);
            }
        }

        if (tools != null && !tools.isEmpty()) {
            body.set(FIELD_TOOLS, tools);
        }

        String jsonBody = body.toJson();
        log.debug("构建请求体: 大小={} 字符, 工具数={}, 消息数={}",
                jsonBody.length(), tools != null ? tools.size() : 0, messages.size());
        return jsonBody;
    }
}
