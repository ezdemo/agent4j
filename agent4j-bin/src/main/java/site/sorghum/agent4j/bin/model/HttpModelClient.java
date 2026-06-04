package site.sorghum.agent4j.bin.model;

import okhttp3.*;
import org.noear.snack4.ONode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import site.sorghum.agent4j.bin.agent.ChatMessage;
import site.sorghum.agent4j.bin.agent.ToolCallEntry;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
public class HttpModelClient implements ModelClient {

    private static final Logger logger = LoggerFactory.getLogger(HttpModelClient.class);
    private static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json; charset=utf-8");

    /**
     * 重试间隔（秒），共 10 次：1,1,1,2,2,2,3,3,6,10 — 总计约 31 秒。
     * 指数退避策略，应对 API 临时故障。
     */
    private static final int[] RETRY_DELAYS = {1, 1, 1, 2, 2, 2, 3, 3, 6, 10};

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
        return model != null && (
                model.contains("reasoner")
                        || model.equals("deepseek-v4-flash")
                        || model.equals("deepseek-v4-pro")
        );
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
            logger.debug("已取消当前 OkHttp Call");
        }
    }

    /**
     * 模型最大上下文窗口 token 数。
     * 优先级：环境变量 AGENT4J_MAX_CONTEXT_TOKENS > 模型名推断 > 默认 128K。
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
        return 128_000;
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

                logger.debug("发送API请求到 {}，模型: {}，消息数: {}，工具数: {}",
                        apiUrl, model, messages.size(), tools != null ? tools.size() : 0);
                logger.debug("收到API响应（完整响应）: {}", responseText);

                if (retryable(status) && attempt < RETRY_DELAYS.length) {
                    int delay = RETRY_DELAYS[attempt];
                    System.err.println("[retry] HTTP " + status + " (非流式)，第" + (attempt + 1) + "次重试，等待" + delay + "s...");
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
                logger.error("非流式API调用IO异常: {}", e.getMessage(), e);
                if (attempt < RETRY_DELAYS.length) {
                    int delay = RETRY_DELAYS[attempt];
                    System.err.println("[retry] " + e.getMessage() + "，第" + (attempt + 1) + "次重试，等待" + delay + "s...");
                    try {
                        Thread.sleep(delay * 1000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    continue;
                }
                throw e;
            } catch (InterruptedException e) {
                logger.error("非流式API调用被中断", e);
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted during retry", e);
            }
        }
    }

    /**
     * 流式调用 — 通过回调逐 token 推送，5xx / IO 异常自动重试最多 10 次。
     * <p>
     * 解析 OpenAI SSE 格式：
     * {@code data: {"choices":[{"delta":{"content":"..."}}]}}
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
                logger.debug("发送流式API请求到 {}，模型: {}，消息数: {}，工具数: {}",
                        apiUrl, model, messages.size(), tools != null ? tools.size() : 0);

                int status = response.code();
                if (retryable(status) && attempt < RETRY_DELAYS.length) {
                    int delay = RETRY_DELAYS[attempt];
                    System.err.println("[retry] HTTP " + status + "，第" + (attempt + 1) + "次重试，等待" + delay + "s...");
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
                        logger.debug("onError回调异常（可能SSE连接已断开）: {}", e.getMessage());
                    }
                    return;
                }

                ResponseBody body = response.body();
                if (body == null) {
                    try {
                        callback.onError("Empty response body");
                    } catch (Exception e) {
                        logger.debug("onError回调异常: {}", e.getMessage());
                    }
                    return;
                }

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8))) {
                    String line;
                    StringBuilder contentBuf = new StringBuilder();
                    StringBuilder reasoningBuf = new StringBuilder();
                    ONode toolCallsAccum = null;

                    while ((line = reader.readLine()) != null) {
                        if (abortRequested) {
                            logger.debug("流式请求被 ReasonBreaker 中断");
                            break;
                        }
                        if (!line.startsWith("data: ")) continue;
                        String data = line.substring(6).trim();
                        if ("[DONE]".equals(data)) {
                            logger.debug("收到SSE流结束标记");
                            break;
                        }

                        ONode chunk = ONode.ofJson(data);
                        logger.debug("收到SSE数据块，大小: {} 字符", data.length());

                        // 捕获 usage（兼容 DeepSeek / Mimo 等不同模型的缓存字段格式）
                        ONode usage = chunk.get("usage");
                        if (usage != null && !usage.isNull()) {
                            int pt = usage.get("prompt_tokens").isNull() ? 0 : usage.get("prompt_tokens").getInt();
                            int ct = usage.get("completion_tokens").isNull() ? 0 : usage.get("completion_tokens").getInt();
                            int tt = usage.get("total_tokens").isNull() ? 0 : usage.get("total_tokens").getInt();
                            // 缓存命中：DeepSeek 用 prompt_cache_hit_tokens，Mimo 用 prompt_tokens_details.cached_tokens
                            int ch = usage.get("prompt_cache_hit_tokens").isNull() ? 0 : usage.get("prompt_cache_hit_tokens").getInt();
                            int cm = usage.get("prompt_cache_miss_tokens").isNull() ? 0 : usage.get("prompt_cache_miss_tokens").getInt();
                            if (ch == 0 && cm == 0) {
                                ONode ptDetails = usage.get("prompt_tokens_details");
                                if (ptDetails != null && !ptDetails.isNull()) {
                                    ch = ptDetails.get("cached_tokens").isNull() ? 0 : ptDetails.get("cached_tokens").getInt();
                                    // Mimo: miss = prompt_tokens - cached_tokens
                                    cm = Math.max(0, pt - ch);
                                }
                            }
                            // reasoning_tokens（Mimo 等模型在 completion_tokens_details 中返回）
                            ONode ctDetails = usage.get("completion_tokens_details");
                            if (ctDetails != null && !ctDetails.isNull()) {
                                int reasoningTokens = ctDetails.get("reasoning_tokens").isNull() ? 0 : ctDetails.get("reasoning_tokens").getInt();
                                if (reasoningTokens > 0) {
                                    logger.debug("推理 token 消耗: {}", reasoningTokens);
                                }
                            }
                            logger.debug("收到usage数据（完整API响应）: {}", chunk.toJson());
                            try {
                                callback.onUsage(pt, ct, tt, ch, cm);
                            } catch (Exception e) {
                                // SSE连接断开时忽略异常，继续执行
                                logger.debug("onUsage回调异常（可能SSE连接已断开）: {}", e.getMessage());
                            }
                        }

                        ONode delta = chunk.select("$.choices[0].delta");
                        if (delta == null || delta.isNull()) continue;

                        ONode rd = delta.get("reasoning_content");
                        if (rd != null && rd.isString()) {
                            String tok = rd.getString();
                            if (tok != null && !tok.isEmpty()) {
                                reasoningBuf.append(tok);
                                logger.debug("收到reasoning_content: {}", tok);
                                try {
                                    callback.onReasoningDelta(tok);
                                } catch (Exception e) {
                                    // SSE连接断开时忽略异常，继续执行
                                    logger.debug("onReasoningDelta回调异常（可能SSE连接已断开）: {}", e.getMessage());
                                }
                            }
                        }

                        ONode cd = delta.get("content");
                        if (cd != null && cd.isString()) {
                            String tok = cd.getString();
                            if (tok != null && !tok.isEmpty()) {
                                contentBuf.append(tok);
                                logger.debug("收到content: {}", tok);
                                try {
                                    callback.onContentDelta(tok);
                                } catch (Exception e) {
                                    // SSE连接断开时忽略异常，继续执行
                                    logger.debug("onContentDelta回调异常（可能SSE连接已断开）: {}", e.getMessage());
                                }
                            }
                        }

                        ONode tcDelta = delta.get("tool_calls");
                        if (tcDelta != null && tcDelta.isArray()) {
                            logger.debug("收到tool_calls数据，数量: {}", tcDelta.getArray().size());
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
                                logger.debug("tool_calls索引: {}, 函数名: {}", idx,
                                        func.get("name").isNull() ? "null" : func.get("name").getString());
                            }
                        }
                    }

                    if (toolCallsAccum != null) {
                        // 过滤掉 name 为 null/empty 的 tool call（SSE 分块缺失导致）
                        List<ONode> valid = new ArrayList<>();
                        for (ONode tc : toolCallsAccum.getArray()) {
                            ONode fn = tc.get("function");
                            if (fn != null && !fn.isNull()) {
                                ONode nm = fn.get("name");
                                if (nm != null && nm.isString() && nm.getString() != null && !nm.getString().isEmpty()) {
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
                            logger.debug("完成tool_calls累积，共 {} 个有效调用", valid.size());
                            try {
                                callback.onToolCalls(filtered);
                            } catch (Exception e) {
                                // SSE连接断开时忽略异常，继续执行
                                logger.debug("onToolCalls回调异常（可能SSE连接已断开）: {}", e.getMessage());
                            }
                        }
                    }
                    try {
                        callback.onDone();
                    } catch (Exception e) {
                        // SSE连接断开时忽略异常，继续执行
                        logger.debug("onDone回调异常（可能SSE连接已断开）: {}", e.getMessage());
                    }
                }
                return; // success

            } catch (IOException e) {
                if (abortRequested) {
                    // 主动中断，不重试，不报错
                    logger.debug("流式请求已被中断，跳过重试");
                    abortRequested = false;
                    return;
                }
                logger.error("流式API调用IO异常: {}", e.getMessage(), e);
                if (attempt < RETRY_DELAYS.length) {
                    int delay = RETRY_DELAYS[attempt];
                    System.err.println("[retry] " + e.getMessage() + "，第" + (attempt + 1) + "次重试，等待" + delay + "s...");
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
                    logger.debug("onError回调异常（可能SSE连接已断开）: {}", ex.getMessage());
                }
                return;
            } catch (InterruptedException e) {
                logger.error("流式API调用被中断", e);
                Thread.currentThread().interrupt();
                try {
                    callback.onError("Interrupted during retry");
                } catch (Exception ex) {
                    // SSE连接断开时忽略异常
                    logger.debug("onError回调异常（可能SSE连接已断开）: {}", ex.getMessage());
                }
                return;
            } catch (Exception e) {
                logger.error("流式API调用异常: {}", e.getMessage(), e);
                // 非 IO 异常（如 JSON 解析错误），不重试
                try {
                    callback.onError(e.getMessage());
                } catch (Exception ex) {
                    // SSE连接断开时忽略异常
                    logger.debug("onError回调异常（可能SSE连接已断开）: {}", ex.getMessage());
                }
                return;
            } finally {
                activeCall = null;
            }
        }
    }

    /**
     * 构建 API 请求体 JSON。
     * 包含 model、messages、tools 等字段，
     * 对 tool 消息做防御性检查（缺少 tool_call_id 时跳过）。
     */
    private String buildBody(List<ChatMessage> messages,
                             List<Map<String, Object>> tools) {
        ONode body = new ONode(ONode.ofJson("{}").options()).asObject();
        body.set("model", model);
        if (reasoningEffort != null && !reasoningEffort.isEmpty()) {
            body.set("reasoning_effort", reasoningEffort);
        }

        ONode msgs = body.getOrNew("messages").asArray();
        for (ChatMessage m : messages) {
            // 防御：tool 消息必须有 tool_call_id，缺少时跳过该消息
            if (m.isTool() && (m.getToolCallId() == null || m.getToolCallId().isEmpty())) {
                logger.warn("buildBody: 跳过没有tool_call_id的tool消息");
                continue;
            }

            ONode msg = msgs.addNew();
            msg.set("role", m.getRole());

            // 防御：assistant 消息必须有 content 或 tool_calls（OpenAI/DeepSeek API 要求）
            boolean isAssistant = m.isAssistant();
            boolean hasTc = m.hasToolCalls();
            boolean hasContent = m.hasContent();
            boolean hasReasoning = m.getReasoningContent() != null && !m.getReasoningContent().isEmpty();

            if (isAssistant && !hasContent && !hasTc) {
                // 既无 content 也无 tool_calls → 强制补空 content 防止 API 400
                // 这种情况不应发生在正常流程中，但历史消息损坏或 Healer 遗漏时兜底
                logger.warn("buildBody: 检测到空 assistant 消息（无 content 且无 tool_calls），强制补空");
                msg.set("content", "");
            } else {
                if (hasContent) msg.set("content", m.getContent());
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
        logger.debug("构建请求体: 大小={} 字符, 工具数={}, 消息数={}",
                jsonBody.length(), tools != null ? tools.size() : 0, messages.size());
        return jsonBody;
    }
}
