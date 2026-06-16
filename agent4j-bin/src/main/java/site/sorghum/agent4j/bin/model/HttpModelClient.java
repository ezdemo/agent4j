package site.sorghum.agent4j.bin.model;

import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.noear.snack4.ONode;
import site.sorghum.agent4j.bin.agent.ChatMessage;
import site.sorghum.agent4j.bin.agent.ToolCallEntry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI 兼容 API 的 HTTP 客户端 —— {@link ModelClient} 的 OkHttp 实现。
 * <p>
 * 支持非流式 ({@link #chat}) 和流式 ({@link #chatStream}) 两种调用。
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
    private static final String FIELD_TOOL_CALLS = "tool_calls";
    private static final String FIELD_CHOICES = "choices";
    private static final String FIELD_DELTA = "delta";
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

    // ==================== 核心字段 ====================

    private final String apiUrl;
    private final String apiKey;
    /**
     * reasoning_effort 取值: low / medium / high / max
     */
    private final String reasoningEffort;
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
     * 5xx 服务端错误或 0（连接失败）需要重试，4xx 客户端错误不重试。
     */
    private static boolean retryable(int status) {
        return status >= 500 || status == 0;
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
     * DeepSeek V4 系列推理模型 — reasoning_content 必须回传
     */
    @Override
    public boolean isThinkingMode() {
        // 所有模型都视为思考模型
        return true;
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
        
        // 检查模型名后缀 [大小] 配置
        int suffixSize = parseContextSizeSuffix(model);
        if (suffixSize > 0) {
            return suffixSize;
        }
        
        String m = model.toLowerCase();
        // Gemini 系列支持 1M
        if (m.contains("gemini")) return 1_000_000;
        // Claude 系列 200K
        if (m.contains("claude")) return 200_000;
        // DeepSeek Reasoner 64K，V3/V4 系列 1000K（1M 上下文窗口）
        if (m.contains("reasoner")) return 64_000;
        if (m.contains("deepseek")) return 1_000_000;
        if (m.contains("mimo")) return 1_000_000;
        if (m.contains("m3")) return 512_000;
        // GPT-4 / o1 系列 128K
        if (m.contains("gpt-4") || m.contains("o1") || m.contains("o3")) return 128_000;
        // 默认
        return 256_000;
    }
    
    /**
     * 解析模型名称中的上下文大小后缀。
     * 格式：[数字k] 或 [数字m]（不区分大小写）
     * 
     * @param modelName 模型名称，例如 "mimo-v2.5[512k]"
     * @return 解析出的 token 数，如果解析失败返回 -1
     */
    private int parseContextSizeSuffix(String modelName) {
        if (modelName == null || !modelName.contains("[") || !modelName.contains("]")) {
            return -1;
        }
        
        try {
            int start = modelName.lastIndexOf('[');
            int end = modelName.lastIndexOf(']');
            if (start < 0 || end < 0 || end <= start) {
                return -1;
            }
            
            String suffix = modelName.substring(start + 1, end).trim().toLowerCase();
            if (suffix.isEmpty()) {
                return -1;
            }
            
            // 解析数字部分
            int numberEnd = suffix.length();
            for (int i = 0; i < suffix.length(); i++) {
                char c = suffix.charAt(i);
                if (c < '0' || c > '9') {
                    numberEnd = i;
                    break;
                }
            }
            
            if (numberEnd == 0) {
                return -1; // 没有数字部分
            }
            
            long number = Long.parseLong(suffix.substring(0, numberEnd));
            String unit = suffix.substring(numberEnd).trim();
            
            // 根据单位转换
            switch (unit) {
                case "k":
                    return (int) (number * 1_000);
                case "m":
                    return (int) (number * 1_000_000);
                case "g":
                    return (int) (number * 1_000_000_000);
                case "":
                    // 没有单位，默认为 k
                    return (int) (number * 1_000);
                default:
                    return -1; // 未知单位
            }
        } catch (NumberFormatException e) {
            return -1;
        }
    }
    
    /**
     * 剥离模型名称中的上下文大小后缀。
     * 例如："mimo-v2.5[512k]" → "mimo-v2.5"
     * 
     * @param modelName 模型名称
     * @return 剥离后缀后的模型名称，如果没有后缀则返回原名称
     */
    public static String stripContextSizeSuffix(String modelName) {
        if (modelName == null || !modelName.contains("[") || !modelName.contains("]")) {
            return modelName;
        }
        
        int start = modelName.lastIndexOf('[');
        int end = modelName.lastIndexOf(']');
        if (start < 0 || end < 0 || end <= start) {
            return modelName;
        }
        
        // 检查后缀是否符合格式（数字+可选单位）
        String suffix = modelName.substring(start + 1, end).trim().toLowerCase();
        if (suffix.isEmpty()) {
            return modelName;
        }
        
        // 验证是否是有效的上下文大小后缀
        int numberEnd = suffix.length();
        for (int i = 0; i < suffix.length(); i++) {
            char c = suffix.charAt(i);
            if (c < '0' || c > '9') {
                numberEnd = i;
                break;
            }
        }
        
        if (numberEnd == 0) {
            return modelName; // 没有数字部分，不是有效的后缀
        }
        
        String unit = suffix.substring(numberEnd).trim();
        if (unit.isEmpty() || unit.equals("k") || unit.equals("m") || unit.equals("g")) {
            // 是有效的上下文大小后缀，剥离它
            return modelName.substring(0, start).trim();
        }
        
        return modelName; // 不是有效的后缀
    }

    /**
     * 非流式调用（用于 fold 摘要、/compact 等后台操作），5xx 自动重试最多 10 次
     */
    @Override
    public ONode chat(List<ChatMessage> messages,
                      List<Map<String, Object>> tools) throws IOException {
        String jsonBody = buildBody(messages, tools);
        ONode bodyWithStream = ONode.ofJson(jsonBody);
        bodyWithStream.set("stream", false);
        jsonBody = bodyWithStream.toJson();

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

                if (retryable(status) && attempt < RETRY_DELAYS.length) {
                    int delay = RETRY_DELAYS[attempt];
                    log.warn("[retry] HTTP {} (非流式)，第{}次重试，等待{}s...", status, attempt + 1, delay);
                    Thread.sleep(delay * 1000L);
                    continue;
                }

                if (status >= 400) {
                    throw new IOException("API error " + status + ": " + responseText);
                }

                ONode resp = ONode.ofJson(responseText);
                ONode choices = resp.get("choices");
                if (choices == null || !choices.isArray() || choices.getArray().isEmpty()) {
                    throw new IOException("No choices in response: " + responseText);
                }
                return choices.get(0).get("message");

            } catch (IOException e) {
                log.error("非流式API调用IO异常: {}", e.getMessage(), e);
                if (attempt < RETRY_DELAYS.length) {
                    int delay = RETRY_DELAYS[attempt];
                    log.warn("[retry] " + e.getMessage() + "，第" + (attempt + 1) + "次重试，等待" + delay + "s...");
                    try {
                        Thread.sleep(delay * 1000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    continue;
                }
                throw e;
            } catch (InterruptedException e) {
                log.error("非流式API调用被中断", e);
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted during retry", e);
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
                           List<Map<String, Object>> tools,
                           StreamCallback callback) {
        String jsonBody;
        try {
            jsonBody = buildBody(messages, tools);
            ONode bodyWithStream = ONode.ofJson(jsonBody);
            bodyWithStream.set("stream", true);
            jsonBody = bodyWithStream.toJson();
        } catch (Exception e) {
            callback.onError(e.getMessage());
            return;
        }

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
                if (retryable(status) && attempt < RETRY_DELAYS.length) {
                    int delay = RETRY_DELAYS[attempt];
                    log.warn("[retry] HTTP {} (非流式)，第{}次重试，等待{}s...", status, attempt + 1, delay);
                    Thread.sleep(delay * 1000L);
                    activeCall = null;
                    continue;
                }
                if (status >= 400) {
                    ResponseBody errorBody = response.body();
                    String err = errorBody != null ? errorBody.string() : "unknown error";
                    try {
                        callback.onError("API error " + status + ": " + err);
                    } catch (Exception e) {
                        // SSE连接断开时忽略异常
                        log.debug("onError回调异常（可能SSE连接已断开）: {}", e.getMessage());
                    }
                    return;
                }

                ResponseBody body = response.body();
                if (body == null) {
                    try {
                        callback.onError("Empty response body");
                    } catch (Exception e) {
                        log.debug("onError回调异常: {}", e.getMessage());
                    }
                    return;
                }

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8))) {
                    String line;
                    String sseErrorData = null;
                    StringBuilder contentBuf = new StringBuilder();
                    StringBuilder reasoningBuf = new StringBuilder();
                    ONode toolCallsAccum = null;

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
                            sseErrorData = data;
                            break;
                        }
                        // ★ 优化：快路径 — 无 tool_calls 且无 usage 的 chunk 走轻量字符串提取
                        boolean hasComplexFields = data.contains("\"tool_calls\"")
                                || data.contains("\"usage\"");
                        boolean isShortEnough = data.length() < FAST_PATH_MAX_LEN;

                        if (!hasComplexFields && isShortEnough) {
                            // content 提取（高频路径）
                            String content = extractJsonStringField(data, FIELD_CONTENT);
                            if (content != null && !content.isEmpty()) {
                                contentBuf.append(content);
                                log.debug("收到content: {}", content);
                                try {
                                    callback.onContentDelta(content);
                                } catch (Exception e) {
                                    log.debug("onContentDelta回调异常（可能SSE连接已断开）: {}", e.getMessage());
                                }
                            }
                            // reasoning_content 与 content 可能共存
                            String reasoning = extractJsonStringField(data, "reasoning_content");
                            if (reasoning != null && !reasoning.isEmpty()) {
                                reasoningBuf.append(reasoning);
                                log.debug("收到reasoning_content: {}", reasoning);
                                try {
                                    callback.onReasoningDelta(reasoning);
                                } catch (Exception e) {
                                    log.debug("onReasoningDelta回调异常（可能SSE连接已断开）: {}", e.getMessage());
                                }
                            }
                        } else {
                            // ---- 慢路径：完整 ONode 解析（usage / tool_calls / 超大 chunk） ----
                            ONode chunk = ONode.ofJson(data);
                            log.debug("收到SSE数据块，大小: {} 字符", data.length());

                            // 捕获 usage
                            if (data.contains("\"usage\"")) {
                                ONode usage = chunk.get("usage");
                                if (usage != null && !usage.isNull()) {
                                    int pt = usage.get("prompt_tokens").isNull()
                                            ? 0
                                            : usage.get("prompt_tokens").getInt();
                                    int ct = usage.get("completion_tokens").isNull()
                                            ? 0
                                            : usage.get("completion_tokens").getInt();
                                    int tt = usage.get("total_tokens").isNull()
                                            ? 0
                                            : usage.get("total_tokens").getInt();
                                    int ch = usage.get("prompt_cache_hit_tokens")
                                            .isNull()
                                            ? 0
                                            : usage.get("prompt_cache_hit_tokens")
                                            .getInt();
                                    int cm = usage.get("prompt_cache_miss_tokens")
                                            .isNull()
                                            ? 0
                                            : usage.get("prompt_cache_miss_tokens")
                                            .getInt();
                                    if (ch == 0 && cm == 0) {
                                        ONode ptDetails = usage.get("prompt_tokens_details");
                                        if (ptDetails != null && !ptDetails.isNull()) {
                                            ch = ptDetails.get("cached_tokens")
                                                    .isNull()
                                                    ? 0
                                                    : ptDetails.get("cached_tokens")
                                                    .getInt();
                                            cm = Math.max(0, pt - ch);
                                        }
                                    }
                                    ONode ctDetails = usage.get("completion_tokens_details");
                                    if (ctDetails != null && !ctDetails.isNull()) {
                                        int reasoningTokens = ctDetails
                                                .get("reasoning_tokens").isNull()
                                                ? 0
                                                : ctDetails
                                                .get("reasoning_tokens")
                                                .getInt();
                                        if (reasoningTokens > 0) {
                                            log.debug("推理 token 消耗: {}", reasoningTokens);
                                        }
                                    }
                                    log.debug("收到usage数据（完整API响应）: {}", chunk.toJson());
                                    try {
                                        callback.onUsage(pt, ct, tt, ch, cm);
                                    } catch (Exception e) {
                                        log.debug("onUsage回调异常（可能SSE连接已断开）: {}", e.getMessage());
                                    }
                                }
                            }

                            // tool_calls 累积
                            ONode delta = chunk.select("$.choices[0].delta");
                            if (delta == null || delta.isNull()) continue;

                            ONode rd = delta.get("reasoning_content");
                            if (rd != null && rd.isString()) {
                                String tok = rd.getString();
                                if (tok != null && !tok.isEmpty()) {
                                    reasoningBuf.append(tok);
                                    log.debug("收到reasoning_content: {}", tok);
                                    try {
                                        callback.onReasoningDelta(tok);
                                    } catch (Exception e) {
                                        log.debug("onReasoningDelta回调异常（可能SSE连接已断开）: {}", e.getMessage());
                                    }
                                }
                            }

                            ONode cd = delta.get(FIELD_CONTENT);
                            if (cd != null && cd.isString()) {
                                String tok = cd.getString();
                                if (tok != null && !tok.isEmpty()) {
                                    contentBuf.append(tok);
                                    log.debug("收到content: {}", tok);
                                    try {
                                        callback.onContentDelta(tok);
                                    } catch (Exception e) {
                                        log.debug("onContentDelta回调异常（可能SSE连接已断开）: {}", e.getMessage());
                                    }
                                }
                            }

                            ONode tcDelta = delta.get("tool_calls");
                            if (tcDelta != null && tcDelta.isArray()) {
                                log.debug("收到tool_calls数据，数量: {}", tcDelta.getArray().size());
                                for (ONode tcd : tcDelta.getArray()) {
                                    int idx = tcd.get("index").isNull() ? 0 : tcd.get("index").getInt();
                                    ONode func = tcd.get("function");
                                    if (func == null || func.isNull()) continue;
                                    if (toolCallsAccum == null) {
                                        toolCallsAccum = org.noear.snack4.ONode.ofJson("[]").asArray();
                                    }
                                    while (toolCallsAccum.getArray().size() <= idx) {
                                        toolCallsAccum.addNew().set("type", "function");
                                    }
                                    ONode existing = toolCallsAccum.get(idx);
                                    if (!tcd.get("id").isNull()) existing.set("id", tcd.get("id").getString());
                                    if (!func.get("name").isNull())
                                        existing.getOrNew("function").set("name", func.get("name").getString());
                                    if (!func.get("arguments").isNull()) {
                                        String prev = existing.getOrNew("function").get("arguments").getString();
                                        String add = func.get("arguments").getString();
                                        existing.getOrNew("function").set("arguments",
                                                (prev != null ? prev : "") + (add != null ? add : ""));
                                    }
                                    log.debug("tool_calls索引: {}, 函数名: {}", idx,
                                            func.get("name").isNull() ? "null" : func.get("name").getString());
                                }
                            }
                        }
                    }

                    // ★ SSE流错误重试逻辑 — 突破while循环后在此判断
                    if (sseErrorData != null) {
                        if (attempt < RETRY_DELAYS.length) {
                            int delay = RETRY_DELAYS[attempt];
                            log.warn("[retry] SSE流错误，第{}次重试，等待{}s...", attempt + 1, delay);
                            Thread.sleep(delay * 1000L);
                            continue; // 关闭当前response，继续外层for循环重试
                        }
                        // 重试耗尽，回调错误
                        log.error("SSE流错误（重试耗尽）: {}", sseErrorData);
                        try {
                            callback.onError(sseErrorData);
                        } catch (Exception e) {
                            log.debug("onError回调异常（可能SSE连接已断开）: {}", e.getMessage());
                        }
                        return;
                    }

                    if (toolCallsAccum != null) {
                        // 过滤掉 name 为 null/empty 的 tool call（SSE 分块缺失导致）
                        List<ONode> valid = new ArrayList<>();
                        for (ONode tc : toolCallsAccum.getArray()) {
                            ONode fn = tc.get("function");
                            if (fn != null && !fn.isNull()) {
                                ONode nm = fn.get("name");
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
                                copy.set("id", v.get("id").isNull() ? "" : v.get("id").getString());
                                copy.set("type", "function");
                                ONode copyFn = copy.getOrNew("function");
                                copyFn.set("name", v.get("function").get("name").getString());
                                copyFn.set("arguments", v.get("function").get("arguments").getString());
                            }
                            log.debug("完成tool_calls累积，共 {} 个有效调用", valid.size());
                            try {
                                callback.onToolCalls(filtered);
                            } catch (Exception e) {
                                // SSE连接断开时忽略异常，继续执行
                                log.debug("onToolCalls回调异常（可能SSE连接已断开）: {}", e.getMessage());
                            }
                        }
                    }
                    try {
                        callback.onDone();
                    } catch (Exception e) {
                        // SSE连接断开时忽略异常，继续执行
                        log.debug("onDone回调异常（可能SSE连接已断开）: {}", e.getMessage());
                    }
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
                    try {
                        callback.onDone();
                    } catch (Exception ignored) {
                        log.debug("onDone回调异常（可能SSE连接已断开）: {}", ignored.getMessage());
                    }
                    return;
                }
                log.error("流式API调用IO异常: {}", e.getMessage(), e);
                if (attempt < RETRY_DELAYS.length) {
                    int delay = RETRY_DELAYS[attempt];
                    log.warn("[retry] {}，第{}次重试，等待{}s...", e.getMessage(), attempt + 1, delay);
                    try {
                        Thread.sleep(delay * 1000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    continue;
                }
                try {
                    callback.onError(e.getMessage());
                } catch (Exception ex) {
                    // SSE连接断开时忽略异常
                    log.debug("onError回调异常（可能SSE连接已断开）: {}", ex.getMessage());
                }
                return;
            } catch (InterruptedException e) {
                log.error("流式API调用被中断", e);
                Thread.currentThread().interrupt();
                try {
                    callback.onError("Interrupted during retry");
                } catch (Exception ex) {
                    // SSE连接断开时忽略异常
                    log.debug("onError回调异常（可能SSE连接已断开）: {}", ex.getMessage());
                }
                return;
            } catch (Exception e) {
                log.error("流式API调用异常: {}", e.getMessage(), e);
                // 非 IO 异常（如 JSON 解析错误），不重试
                try {
                    callback.onError(e.getMessage());
                } catch (Exception ex) {
                    // SSE连接断开时忽略异常
                    log.debug("onError回调异常（可能SSE连接已断开）: {}", ex.getMessage());
                }
                return;
            } finally {
                activeCall = null;
            }
        }
    }

    // ==================== 快速 JSON 字符串字段提取 ====================

    /**
     * 快路径 chunk 最大长度 — 超过此长度也走 ONode 解析（防御极端情况）
     */
    private static final int FAST_PATH_MAX_LEN = 1024;

    /**
     * 从 JSON 字符串中提取指定字段的字符串值（快路径，无 ONode 开销）。
     * 仅适用于字符串类型的字段，不处理嵌套对象/数组。
     * 支持转义字符（\\, \", \n, \t, \r, \/）。
     *
     * @param json      JSON 字符串
     * @param fieldName 字段名（不包含引号）
     * @return 字段值，不存在时返回 null
     */
    private static String extractJsonStringField(String json, String fieldName) {
        String key = "\"" + fieldName + "\":\"";
        int keyIdx = json.indexOf(key);
        if (keyIdx < 0) return null;

        int valStart = keyIdx + key.length();
        StringBuilder val = new StringBuilder();
        for (int i = valStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\') {
                if (i + 1 < json.length()) {
                    char next = json.charAt(i + 1);
                    switch (next) {
                        case '"': val.append('"'); break;
                        case '\\': val.append('\\'); break;
                        case 'n': val.append('\n'); break;
                        case 't': val.append('\t'); break;
                        case 'r': val.append('\r'); break;
                        case '/': val.append('/'); break;
                        case 'u':
                            if (i + 5 < json.length()) {
                                try {
                                    int hex = Integer.parseInt(json.substring(i + 2, i + 6), 16);
                                    val.append((char) hex);
                                } catch (NumberFormatException ignored) {
                                    val.append('?');
                                }
                                i += 5;
                            }
                            break;
                        default: val.append(next); break;
                    }
                    i++;
                }
            } else if (c == '"') {
                break;
            } else {
                val.append(c);
            }
        }
        return !val.isEmpty() ? val.toString() : "";
    }

    /**
     * 构建 API 请求体 JSON。
     * 包含 model、messages、tools 等字段，
     * 对 tool 消息做防御性检查（缺少 tool_call_id 时跳过）。
     */
    private String buildBody(List<ChatMessage> messages,
                             List<Map<String, Object>> tools) {
        ONode body = new ONode(ONode.ofJson("{}").options()).asObject();
        // 剥离模型名称中的上下文大小后缀，例如 "mimo-v2.5[512k]" → "mimo-v2.5"
        body.set("model", stripContextSizeSuffix(model));
        if (reasoningEffort != null && !reasoningEffort.isEmpty() && !Objects.equals(reasoningEffort, "none")) {
            body.set("reasoning_effort", reasoningEffort);
            body.set("chat_template_kwargs", ONode.ofJson("{}").set("enable_thinking", true));
            body.set("enable_thinking",true);
        }

        ONode msgs = body.getOrNew("messages").asArray();
        for (ChatMessage m : messages) {
            // 防御：tool 消息必须有 tool_call_id，缺少时跳过该消息
            if (m.isTool() && (m.getToolCallId() == null || m.getToolCallId().isEmpty())) {
                log.warn("buildBody: 跳过没有tool_call_id的tool消息");
                continue;
            }

            ONode msg = new ONode();
            msg.set("role", m.getRole());
            boolean skip = false;

            // 防御：assistant 消息必须有 content 或 tool_calls（OpenAI/DeepSeek API 要求）
            boolean isAssistant = m.isAssistant();
            boolean isUser = m.isUser();
            boolean hasTc = m.hasToolCalls();
            boolean hasContent = m.hasContent();
            boolean hasContentParts = m.getContentParts() != null && !m.getContentParts().isEmpty();
            boolean hasReasoning = m.getReasoningContent() != null && !m.getReasoningContent().isEmpty();

            if (isUser && !hasContent && !hasReasoning && !hasTc && !hasContentParts){
                continue;
            }
            // 多模态 contentParts 序列化为 JSON array
            if (hasContentParts) {
                ONode contentArray = msg.getOrNew(FIELD_CONTENT).asArray();
                for (ChatMessage.ContentPart part : m.getContentParts()) {
                    ONode partNode = contentArray.addNew();
                    partNode.set("type", part.getType());
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
                ONode tcArray = msg.getOrNew("tool_calls").asArray();
                for (ToolCallEntry tc : m.getToolCalls()) {
                    ONode tcNode = tcArray.addNew();
                    tcNode.set("id", tc.id());
                    tcNode.set("type", "function");
                    ONode funcNode = tcNode.getOrNew("function");
                    funcNode.set("name", tc.name());
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
                    funcNode.set("arguments", argsStr);
                }
            }
            if (m.getReasoningContent() != null) msg.set("reasoning_content", m.getReasoningContent());
            if (m.getToolCallId() != null) msg.set("tool_call_id", m.getToolCallId());
            if (!skip) {
                msgs.add(msg);
            }
        }

        if (tools != null && !tools.isEmpty()) {
            ONode toolArray = body.getOrNew("tools").asArray();
            for (Map<String, Object> t : tools) {
                ONode toolNode = toolArray.addNew();
                for (Map.Entry<String, Object> e : t.entrySet()) {
                    toolNode.set(e.getKey(), e.getValue());
                }
            }
        }

        String jsonBody = body.toJson();
        log.debug("构建请求体: 大小={} 字符, 工具数={}, 消息数={}",
                jsonBody.length(), tools != null ? tools.size() : 0, messages.size());
        return jsonBody;
    }
}
