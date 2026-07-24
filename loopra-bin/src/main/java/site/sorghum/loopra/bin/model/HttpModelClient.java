package site.sorghum.loopra.bin.model;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.noear.snack4.ONode;
import site.sorghum.loopra.bin.agent.model.ChatMessage;
import site.sorghum.loopra.bin.agent.model.ToolCallEntry;
import site.sorghum.loopra.bin.config.UserIdProvider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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

    /**
     * 当前请求对应的会话名称，供日志记录使用。
     * 由上层（如 AgentService）在调用 chat/chatStream 前设置，调用后清理。
     */
    public static final ThreadLocal<String> CURRENT_LOG_SESSION = new ThreadLocal<>();

    private static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json; charset=utf-8");

    /**
     * 重试间隔（秒），共 10 次：3,3,5,5,8,10,12,16,22,36 — 总计约 120 秒（2 分钟）。
     * 指数退避策略，应对 API 临时故障。
     */
    private static final int[] DEFAULT_RETRY_DELAYS = {3, 3, 5, 5, 8, 10, 12, 16, 22, 36};

    private static final String FIELD_STREAM = "stream";

    // ==================== 核心字段 ====================

    private final String apiUrl;
    private final String apiKey;
    private final String modelChannelId;
    private final ModelApiProtocol apiProtocol;
    private final int[] retryDelays;
    /**
     * reasoning_effort 取值: low / medium / high / max
     */
    private String reasoningEffort;
    private volatile String model;
    /** 显式请求会话亲和标识；子代理使用独立且固定的值，避免依赖线程局部上下文。 */
    private volatile String sessionAffinity;
    /**
     * 流式中断标志（ReasonBreaker 触发时设置）
     */
    private final AtomicBoolean abortRequested = new AtomicBoolean(false);
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
        this(apiUrl, apiKey, model, reasoningEffort, (String) null);
    }

    public HttpModelClient(String apiUrl, String apiKey, String model, String reasoningEffort, String modelChannelId) {
        this(apiUrl, apiKey, model, reasoningEffort, modelChannelId, "chat_completions");
    }

    public HttpModelClient(String apiUrl, String apiKey, String model, String reasoningEffort, String modelChannelId,
                           String apiProtocol) {
        this(apiUrl, apiKey, model, reasoningEffort, modelChannelId, apiProtocol, DEFAULT_RETRY_DELAYS);
    }

    /** 创建用于工具安全校验的短超时、零重试客户端。 */
    public static HttpModelClient forValidation(String apiUrl, String apiKey, String model,
                                                String modelChannelId, String apiProtocol) {
        return new HttpModelClient(apiUrl, apiKey, model, "none", modelChannelId, apiProtocol,
                new int[0], 30, TimeUnit.SECONDS);
    }

    HttpModelClient(String apiUrl, String apiKey, String model, String reasoningEffort, int[] retryDelays) {
        this(apiUrl, apiKey, model, reasoningEffort, null, "chat_completions", retryDelays);
    }

    HttpModelClient(String apiUrl, String apiKey, String model, String reasoningEffort, String modelChannelId,
                    String apiProtocol, int[] retryDelays) {
        this(apiUrl, apiKey, model, reasoningEffort, modelChannelId, apiProtocol,
                retryDelays, 10, TimeUnit.MINUTES);
    }

    private HttpModelClient(String apiUrl, String apiKey, String model, String reasoningEffort, String modelChannelId,
                            String apiProtocol, int[] retryDelays, long readTimeout, TimeUnit readTimeoutUnit) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.modelChannelId = modelChannelId;
        this.apiProtocol = ModelApiProtocols.resolve(apiProtocol);
        this.model = model;
        this.reasoningEffort = reasoningEffort;
        this.retryDelays = retryDelays.clone();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(readTimeout, readTimeoutUnit)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false) // 由我们自己的重试逻辑控制
                .build();
    }

    /**
     * 向 OkHttp Request.Builder 添加会话标识请求头。
     */
    private void addSessionHeaders(Request.Builder builder, String model) {
        String sessionId = resolveSessionAffinity();
        String userId = UserIdProvider.getUserId();
        if (sessionId != null && !sessionId.isEmpty()) {
            builder.addHeader("x-session-affinity", sessionId);
            builder.addHeader("X-Session-ID",sessionId);
            if (model.contains("gpt") || model.contains("codex")) {
                builder.addHeader("X-Claude-Code-Session-Id", sessionId);
                builder.addHeader("specific_channel_id", sessionId);
                builder.addHeader("Session_id", sessionId);
                builder.addHeader("channel_affinity", sessionId);
            }
        }
        if (userId != null && !userId.isEmpty()) {
            if (model.contains("gpt") || model.contains("codex")) {
                builder.addHeader("user_id", userId);
            }
        }
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
        private final StreamCallback callback;

        RetryContext(String tag) {
            this(tag, null);
        }

        RetryContext(String tag, StreamCallback callback) {
            this.tag = tag;
            this.callback = callback;
        }

        /**
         * 检查是否应该重试，如果是则等待并返回；否则抛出异常。
         *
         * @param reason  重试原因（用于日志）
         * @param attempt 当前尝试次数（从 0 开始）
         * @throws IOException 如果不应重试或等待被中断
         */
        void waitOrThrow(String reason, int attempt) throws IOException {
            if (abortRequested.compareAndSet(true, false)) {
                log.debug("[{}] {} 可重试，但已请求中断，跳过重试", tag, reason);
                throw new IOException("Request aborted by user");
            }
            if (attempt >= retryDelays.length) {
                throw new IOException("[" + tag + "] 重试耗尽: " + reason);
            }
            int delay = retryDelays[attempt];
            log.warn("[retry][{}] {}，第{}次重试，等待{}s...", tag, reason, attempt + 1, delay);
            notifyRetry(reason, attempt, delay);
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
            if (abortRequested.compareAndSet(true, false)) {
                log.debug("[{}] IO异常，但已请求中断，跳过重试", tag);
                throw new IOException("Request aborted by user", e);
            }
            if (attempt >= retryDelays.length) {
                throw e;
            }
            int delay = retryDelays[attempt];
            log.warn("[retry][{}] {}，第{}次重试，等待{}s...", tag, e.getMessage(), attempt + 1, delay);
            notifyRetry(e.getMessage(), attempt, delay);
            doSleep(delay);
            checkAbort();
        }

        private void notifyRetry(String reason, int attempt, int delay) {
            if (callback != null) {
                safeCallback("onRetry", () -> callback.onRetry(
                        reason, attempt + 1, retryDelays.length, delay));
            }
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
            if (abortRequested.compareAndSet(true, false)) {
                log.debug("[{}] 重试等待期间收到中断请求，跳过重试", tag);
                throw new IOException("Request aborted by user");
            }
        }
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

    @Override
    public String getModelChannelId() {
        return modelChannelId;
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
        abortRequested.set(true);
        Call call = activeCall;
        if (call != null && !call.isCanceled()) {
            call.cancel();
            log.debug("已取消当前 OkHttp Call");
        }
    }

    @Override
    public void resetStreamAbort() {
        abortRequested.set(false);
    }

    @Override
    public void setSessionAffinity(String sessionAffinity) {
        this.sessionAffinity = sessionAffinity;
    }

    private String resolveSessionAffinity() {
        String explicit = sessionAffinity;
        return explicit != null && !explicit.isEmpty() ? explicit : CURRENT_LOG_SESSION.get();
    }

    @Override
    public ModelClient fork() {
        HttpModelClient fork = new HttpModelClient(
                apiUrl, apiKey, model, reasoningEffort, modelChannelId, apiProtocol.name());
        fork.setSessionAffinity(sessionAffinity);
        return fork;
    }

    private ModelApiProtocol.RequestContext requestContext(List<ChatMessage> messages, ONode tools) {
        return new ModelApiProtocol.RequestContext(
                model, reasoningEffort, messages, tools, UserIdProvider.getUserId(), resolveSessionAffinity());
    }

    /**
     * 模型最大上下文窗口 token 数。
     * 优先级：环境变量 LOOPRA_MAX_CONTEXT_TOKENS > 模型名后缀 [大小] > 模型名推断 > 默认 256K。
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
        String env = System.getenv("LOOPRA_MAX_CONTEXT_TOKENS");
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
            int providerSize = contextSizeProvider.getContextSize(modelChannelId, model);
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
        ONode body = apiProtocol.buildRequest(requestContext(messages, tools));
        body.set(FIELD_STREAM, false);
        String jsonBody = body.toJson();
        log.debug("构建请求体: 大小={} 字符, 工具数={}, 消息数={}",
                jsonBody.length(), tools != null ? tools.size() : 0, messages.size());

        RetryContext retry = new RetryContext("非流式");
        for (int attempt = 0; ; attempt++) {
            Request.Builder requestBuilder = new Request.Builder()
                    .url(apiUrl)
                    .post(RequestBody.create(jsonBody, MEDIA_TYPE_JSON))
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("User-Agent", "opencode/1.14.21 ai-sdk/provider-utils/4.0.23 runtime/bun/1.3.13");
            addSessionHeaders(requestBuilder, ModelContextUtils.stripContextSizeSuffix(model));
            Request request = requestBuilder.build();

            try (Response response = client.newCall(request).execute()) {
                int status = response.code();
                ResponseBody responseBody = response.body();
                String responseText = responseBody != null ? responseBody.string() : "";

                log.debug("发送API请求到 {}，模型: {}，消息数: {}，工具数: {}",
                        apiUrl, model, messages.size(), tools != null ? tools.size() : 0);
                log.debug("收到API响应（完整响应）: {}", responseText);

                if (retryable(status)) {
                    retry.waitOrThrow("HTTP " + status + ": " + responseText, attempt);
                    continue;
                }

                if (status >= 400) {
                    throw new IOException("API error " + status + ": " + responseText);
                }

                // ★ 记录请求日志
                writeApiLog(jsonBody);

                ONode resp = ONode.ofJson(responseText);
                return apiProtocol.parseResponse(resp, responseText);

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
     * 通过配置的协议策略解析 SSE 事件。
     * </p>
     */
    @Override
    public void chatStream(List<ChatMessage> messages,
                           ONode tools,
                           StreamCallback callback) {
        String jsonBody;
        try {
            jsonBody = buildStreamRequest(messages, tools);
            log.debug("构建流式请求体: 大小={} 字符, 工具数={}, 消息数={}",
                    jsonBody.length(), tools != null ? tools.size() : 0, messages.size());
        } catch (Exception e) {
            callback.onError(e.getMessage());
            return;
        }

        boolean recoveredInvalidRequest = false;

        int retryAttempt = 0;
        RetryContext retry = new RetryContext("流式", callback);
        while (true) {
            ModelApiStreamState parseResult = new ModelApiStreamState();
            Request.Builder requestBuilder = new Request.Builder()
                    .url(apiUrl)
                    .post(RequestBody.create(jsonBody, MEDIA_TYPE_JSON))
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("User-Agent", "opencode/1.14.21 ai-sdk/provider-utils/4.0.23 runtime/bun/1.3.13");
            addSessionHeaders(requestBuilder, ModelContextUtils.stripContextSizeSuffix(model));
            Request request = requestBuilder.build();

            Call call = client.newCall(request);
            activeCall = call;

            try (Response response = call.execute()) {
                log.debug("发送流式API请求到 {}，模型: {}，消息数: {}，工具数: {}",
                        apiUrl, model, messages.size(), tools != null ? tools.size() : 0);

                int status = response.code();
                if (retryable(status)) {
                    try {
                        retry.waitOrThrow("HTTP " + status, retryAttempt);
                        retryAttempt++;
                    } catch (IOException e) {
                        finishRetryFailure(e, callback);
                        return;
                    }
                    activeCall = null;
                    continue;
                }
                if (status >= 400) {
                    ResponseBody errorBody = response.body();
                    String err = errorBody != null ? errorBody.string() : "unknown error";
                    if (!recoveredInvalidRequest && !ModelApiError.isContextLengthExceeded(err)
                            && ModelApiError.isInvalidRequestError(err)) {
                        List<ChatMessage> recoveryMessages = withoutLatestResponsesAssistant(messages);
                        if (recoveryMessages != null) {
                            jsonBody = buildStreamRequest(recoveryMessages, tools);
                            recoveredInvalidRequest = true;
                            log.warn("Responses API 返回 invalid_request_error，已回退最近的 reasoning assistant 轮次后重试一次");
                            continue;
                        }
                    }
                    safeCallback("onError", () -> callback.onError(
                            ModelApiError.annotate("API error " + status + ": " + err)));
                    return;
                }

                ResponseBody body = response.body();
                if (body == null) {
                    safeCallback("onError", () -> callback.onError("Empty response body"));
                    return;
                }

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8))) {
                    processSseStream(reader, callback, parseResult);

                    // ★ 记录请求日志
                    writeApiLog(jsonBody);
                    if (log.isTraceEnabled()) {
                        String sessionHeader = request.header("Session_id");
                        String userIdHeader = request.header("user_id");
                        log.trace("[ai-trace] request headers: Session_id={}, user_id={}", sessionHeader, userIdHeader);
                    }

                    // ★ SSE流错误重试逻辑
                    if (parseResult.errorData != null) {
                        String errorData = ModelApiError.annotate(parseResult.errorData);
                        boolean contextLengthExceeded = parseResult.contextLengthExceeded
                                || ModelApiError.isContextLengthExceeded(parseResult.errorData);

                        // 上下文超限不能用原始请求重试，交给 AgentLoop 折叠历史后重新请求。
                        if (contextLengthExceeded) {
                            safeCallback("onError", () -> callback.onError(errorData));
                            return;
                        }

                        if (parseResult.invalidRequestError && !recoveredInvalidRequest
                                && !parseResult.emittedOutput) {
                            List<ChatMessage> recoveryMessages = withoutLatestResponsesAssistant(messages);
                            if (recoveryMessages != null) {
                                try {
                                    jsonBody = buildStreamRequest(recoveryMessages, tools);
                                    recoveredInvalidRequest = true;
                                    log.warn("Responses API 返回 invalid_request_error，已回退最近的 reasoning assistant 轮次后重试一次");
                                    continue;
                                } catch (Exception e) {
                                    safeCallback("onError", () -> callback.onError(e.getMessage()));
                                    return;
                                }
                            }
                        }

                        if (!parseResult.retryableError || parseResult.emittedOutput) {
                            safeCallback("onError", () -> callback.onError(errorData));
                            return;
                        }
                        try {
                            retry.waitOrThrow("SSE流错误: " + errorData, retryAttempt);
                            retryAttempt++;
                        } catch (IOException e) {
                            finishRetryFailure(e, callback);
                            return;
                        }
                        continue; // 关闭当前response，继续外层for循环重试
                    }

                    if (parseResult.aborted) {
                        safeCallback("onDone", callback::onDone);
                        return;
                    }

                    String completionError = apiProtocol.streamCompletionError(parseResult);
                    if (completionError != null) {
                        safeCallback("onError", () -> callback.onError(completionError));
                        return;
                    }

                    apiProtocol.completeStream(parseResult, callback);
                    safeCallback("onDone", callback::onDone);
                }
                return; // success

            } catch (IOException e) {
                if (abortRequested.compareAndSet(true, false)) {
                    // 主动中断，不重试，不报错
                    // ★ 必须回调 onDone() 释放 AgentLoop.streamLLM() 中的 streamLatch，
                    //    否则调用方线程会永久阻塞在 CountDownLatch.await() 上，
                    //    导致 AgentService 的会话锁（ReentrantLock）永远无法释放。
                    log.debug("流式请求已被中断，跳过重试");
                    safeCallback("onDone", callback::onDone);
                    return;
                }
                log.error("流式API调用IO异常: {}", e.getMessage(), e);
                if (parseResult.emittedOutput) {
                    safeCallback("onError", () -> callback.onError(e.getMessage()));
                    return;
                }
                try {
                    retry.waitOrThrow(e, retryAttempt);
                    retryAttempt++;
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

    private String buildStreamRequest(List<ChatMessage> messages, ONode tools) {
        ONode body = apiProtocol.buildRequest(requestContext(messages, tools));
        body.set(FIELD_STREAM, true);
        return body.toJson();
    }

    /**
     * Removes only the newest assistant turn that carries a replayable Responses reasoning item.
     * Tool outputs produced by that turn are removed with it so their function_call_output items do
     * not become orphaned. The persisted conversation remains untouched.
     *
     * @return recovery messages, or {@code null} when no reasoning assistant can be rolled back
     */
    private static List<ChatMessage> withoutLatestResponsesAssistant(List<ChatMessage> messages) {
        int assistantIndex = -1;
        Set<String> toolCallIds = new HashSet<>();
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (!"assistant".equals(message.getRole()) || !hasResponseReasoning(message)) continue;
            assistantIndex = i;
            if (message.getToolCalls() != null) {
                for (ToolCallEntry toolCall : message.getToolCalls()) {
                    if (toolCall.id() != null) toolCallIds.add(toolCall.id());
                }
            }
            break;
        }
        if (assistantIndex < 0) return null;

        List<ChatMessage> cleaned = new ArrayList<>(messages.size() - 1);
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (i == assistantIndex) continue;
            if (message.isTool() && toolCallIds.contains(message.getToolCallId())) continue;
            cleaned.add(message);
        }
        return cleaned;
    }

    private static boolean hasResponseReasoning(ChatMessage message) {
        if (message.getResponseReasoning() != null) return true;
        return message.getToolCalls() != null && message.getToolCalls().stream()
                .anyMatch(toolCall -> toolCall.responseReasoning() != null);
    }

    private void finishRetryFailure(IOException error, StreamCallback callback) {
        if (error.getMessage() != null && error.getMessage().contains("aborted by user")) {
            safeCallback("onDone", callback::onDone);
        } else {
            safeCallback("onError", () -> callback.onError(error.getMessage()));
        }
    }

    /**
     * 处理 SSE 流，解析所有数据行。
     *
     * @param reader   SSE 流的 BufferedReader
     * @param callback 流式回调
     * @return 解析结果（包含错误数据和累积的 tool_calls）
     */
    private void processSseStream(BufferedReader reader, StreamCallback callback,
                                  ModelApiStreamState result) throws IOException {
        String line;
        StringBuilder content = new StringBuilder();
        boolean process = false;
        while ((line = reader.readLine()) != null) {
            content.append(line).append("\n");
            line = line.trim();
            if (abortRequested.compareAndSet(true, false)) {
                result.aborted = true;
                log.info("流式请求被 ReasonBreaker 中断");
                break;
            }
            if (!line.startsWith("data:")) {
                continue;
            }
            String data = line.substring(5).trim();
            if ("[DONE]".equals(data)) {
                log.info("收到SSE流结束标记");
                break;
            }
            if (data.trim().startsWith("{\"error\":")) {
                log.warn("收到SSE流错误（可重试）: {}", data);
                result.errorData = data;
                result.contextLengthExceeded = ModelApiError.isContextLengthExceeded(data);
                result.retryableError = true;
                process = true;
                break;
            }
            ONode chunk = ONode.ofJson(data);
            log.debug("收到SSE数据块，大小: {} 字符", data.length());
            process = true;
            apiProtocol.processStreamChunk(chunk, callback, result);
        }
        if (!process) {
            String msg = "未收到SSE数据块，内容:\n ```" + content + "```";
            callback.onError(msg);
            log.warn(msg);
        }
    }

    // ==================== API 请求/响应日志 ====================

    private static final java.nio.file.Path API_LOG_DIR =
            java.nio.file.Paths.get(System.getProperty("user.home"), ".loopra", "logs", "http");

    private static final java.time.format.DateTimeFormatter API_TS_FMT =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                    .withZone(java.time.ZoneId.systemDefault());

    /**
     * 写入一次 AI API 调用的请求日志到 ~/.loopra/logs/http/{sessionName}.log。
     * 只保留最近 2 次请求记录，旧记录自动丢弃。
     */
    private void writeApiLog(String requestBody) {
        String sessionName = CURRENT_LOG_SESSION.get();
        if (sessionName == null || sessionName.isEmpty()) {
            sessionName = "unknown";
        }
        try {
            java.nio.file.Files.createDirectories(API_LOG_DIR);
            java.nio.file.Path logFile = API_LOG_DIR.resolve(sanitizeFileName(sessionName) + ".log");

            StringBuilder newEntry = new StringBuilder();
            newEntry.append("================================================================================\n");
            newEntry.append(">>> AI API Call : ").append(API_TS_FMT.format(java.time.Instant.now())).append('\n');
            newEntry.append(">>> URL         : ").append(apiUrl).append('\n');
            newEntry.append(">>> Request     :\n");
            newEntry.append(requestBody).append('\n');
            newEntry.append("<<< END\n");
            newEntry.append('\n');

            // 只保留最近 2 条：取已有日志的最后 1 条 + 当前新条目
            String existing = "";
            if (java.nio.file.Files.exists(logFile)) {
                existing = java.nio.file.Files.readString(logFile);
            }
            String[] parts = existing.split("(?m)^={60,}$");
            // parts[0] 是分隔符前的空串，跳过；取最后 1 条旧记录
            StringBuilder keep = new StringBuilder();
            if (parts.length > 0) {
                for (int i = Math.max(0, parts.length - 1); i < parts.length; i++) {
                    String p = parts[i].trim();
                    if (!p.isEmpty()) {
                        keep.append("================================================================================\n");
                        keep.append(p).append('\n');
                    }
                }
            }
            keep.append(newEntry);

            java.nio.file.Files.writeString(logFile, keep.toString(),
                    java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            log.warn("[api-log] 写入 AI 调用日志失败: {}", e.getMessage());
        }
    }

    private static String sanitizeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
