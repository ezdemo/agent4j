package site.sorghum.agent4j.bin.model;

import org.noear.snack4.ONode;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 API 的 HTTP 客户端 —— {@link ModelClient} 的 HTTP 实现。
 * <p>
 * 支持非流式 ({@link #chat}) 和流式 ({@link #chatStream}) 两种调用。
 * </p>
 *
 * @author Sorghum
 */
public class HttpModelClient implements ModelClient {

    /** reasoning_effort 取值: low / medium / high / max */
    private String reasoningEffort;

    private final String apiUrl;
    private final String apiKey;
    private final String model;

    /** 重试间隔（秒），共 10 次：1,1,1,2,2,2,3,3,6,10 — 总计约 31 秒 */
    private static final int[] RETRY_DELAYS = {1, 1, 1, 2, 2, 2, 3, 3, 6, 10};

    /** 判断 HTTP 状态码是否应重试（5xx 服务端错误 或 0=连接失败） */
    private static boolean retryable(int status) {
        return status >= 500 || status == 0;
    }

    public HttpModelClient(String apiUrl, String apiKey, String model) {
        this(apiUrl, apiKey, model, "high");
    }

    public HttpModelClient(String apiUrl, String apiKey, String model, String reasoningEffort) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.reasoningEffort = reasoningEffort;
    }

    /** 设置推理力度（运行时切换）。 */
    @Override
    public void setReasoningEffort(String effort) { this.reasoningEffort = effort; }

    @Override
    public String getReasoningEffort() { return reasoningEffort; }

    /** DeepSeek V4 系列推理模型 — reasoning_content 必须回传 */
    @Override
    public boolean isThinkingMode() {
        return model != null && (
                model.contains("reasoner")
                || model.equals("deepseek-v4-flash")
                || model.equals("deepseek-v4-pro")
        );
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
            try { return Integer.parseInt(env); } catch (NumberFormatException ignored) {}
        }
        if (model == null) return 128_000;
        String m = model.toLowerCase();
        // Gemini 系列支持 1M
        if (m.contains("gemini")) return 1_000_000;
        // Claude 系列 200K
        if (m.contains("claude")) return 200_000;
        // DeepSeek Reasoner 64K，V3/V4 系列 1000K（1M 上下文窗口）
        if (m.contains("reasoner")) return 64_000;
        if (m.contains("deepseek")) return 1_000_000;
        // GPT-4 / o1 系列 128K
        if (m.contains("gpt-4") || m.contains("o1") || m.contains("o3")) return 128_000;
        // 默认
        return 128_000;
    }

    /** 非流式调用（用于 fold 摘要、/compact 等后台操作），5xx 自动重试最多 10 次 */
    @Override
    public ONode chat(List<Map<String, Object>> messages,
                      List<Map<String, Object>> tools) throws IOException {
        String jsonBody = buildBody(messages, tools);
        ONode bodyWithStream = ONode.ofJson(jsonBody);
        bodyWithStream.set("stream", false);
        jsonBody = bodyWithStream.toJson();

        for (int attempt = 0; ; attempt++) {
            try {
                HttpURLConnection conn = (HttpURLConnection) URI.create(apiUrl).toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setDoOutput(true);
                conn.setConnectTimeout(30_000);
                conn.setReadTimeout(600_000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                }

                int status = conn.getResponseCode();
                InputStream is = status >= 200 && status < 300
                        ? conn.getInputStream()
                        : conn.getErrorStream();
                String responseText = readFully(is);
                conn.disconnect();

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
                if (attempt < RETRY_DELAYS.length) {
                    int delay = RETRY_DELAYS[attempt];
                    System.err.println("[retry] " + e.getMessage() + "，第" + (attempt + 1) + "次重试，等待" + delay + "s...");
                    try { Thread.sleep(delay * 1000L); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    continue;
                }
                throw e;
            } catch (InterruptedException e) {
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
    public void chatStream(List<Map<String, Object>> messages,
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
            try {
                HttpURLConnection conn = (HttpURLConnection) URI.create(apiUrl).toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setDoOutput(true);
                conn.setConnectTimeout(30_000);
                conn.setReadTimeout(600_000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                }

                int status = conn.getResponseCode();
                if (retryable(status) && attempt < RETRY_DELAYS.length) {
                    conn.disconnect();
                    int delay = RETRY_DELAYS[attempt];
                    System.err.println("[retry] HTTP " + status + "，第" + (attempt + 1) + "次重试，等待" + delay + "s...");
                    Thread.sleep(delay * 1000L);
                    continue;
                }
                if (status >= 400) {
                    String err = readFully(conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream());
                    callback.onError("API error " + status + ": " + err);
                    return;
                }

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    StringBuilder contentBuf = new StringBuilder();
                    StringBuilder reasoningBuf = new StringBuilder();
                    ONode toolCallsAccum = null;

                    while ((line = reader.readLine()) != null) {
                        if (!line.startsWith("data: ")) continue;
                        String data = line.substring(6).trim();
                        if ("[DONE]".equals(data)) break;

                        ONode chunk = ONode.ofJson(data);

                        // 捕获 usage（DeepSeek 在最后一个 chunk 返回）
                        ONode usage = chunk.get("usage");
                        if (usage != null && !usage.isNull()) {
                            int pt = usage.get("prompt_tokens").isNull() ? 0 : usage.get("prompt_tokens").getInt();
                            int ct = usage.get("completion_tokens").isNull() ? 0 : usage.get("completion_tokens").getInt();
                            int tt = usage.get("total_tokens").isNull() ? 0 : usage.get("total_tokens").getInt();
                            int ch = usage.get("prompt_cache_hit_tokens").isNull() ? 0 : usage.get("prompt_cache_hit_tokens").getInt();
                            int cm = usage.get("prompt_cache_miss_tokens").isNull() ? 0 : usage.get("prompt_cache_miss_tokens").getInt();
                            callback.onUsage(pt, ct, tt, ch, cm);
                        }

                        ONode delta = chunk.select("$.choices[0].delta");
                        if (delta == null || delta.isNull()) continue;

                        ONode rd = delta.get("reasoning_content");
                        if (rd != null && rd.isString()) {
                            String tok = rd.getString();
                            if (tok != null && !tok.isEmpty()) {
                                reasoningBuf.append(tok);
                                callback.onReasoningDelta(tok);
                            }
                        }

                        ONode cd = delta.get("content");
                        if (cd != null && cd.isString()) {
                            String tok = cd.getString();
                            if (tok != null && !tok.isEmpty()) {
                                contentBuf.append(tok);
                                callback.onContentDelta(tok);
                            }
                        }

                        ONode tcDelta = delta.get("tool_calls");
                        if (tcDelta != null && tcDelta.isArray()) {
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
                                if (!func.get("name").isNull()) existing.getOrNew("function").set("name", func.get("name").getString());
                                if (!func.get("arguments").isNull()) {
                                    String prev = existing.getOrNew("function").get("arguments").getString();
                                    String add = func.get("arguments").getString();
                                    existing.getOrNew("function").set("arguments",
                                            (prev != null ? prev : "") + (add != null ? add : ""));
                                }
                            }
                        }
                    }

                    if (toolCallsAccum != null) {
                        callback.onToolCalls(toolCallsAccum);
                    }
                    callback.onDone();
                }
                conn.disconnect();
                return; // success

            } catch (IOException e) {
                if (attempt < RETRY_DELAYS.length) {
                    int delay = RETRY_DELAYS[attempt];
                    System.err.println("[retry] " + e.getMessage() + "，第" + (attempt + 1) + "次重试，等待" + delay + "s...");
                    try { Thread.sleep(delay * 1000L); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    continue;
                }
                callback.onError(e.getMessage());
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                callback.onError("Interrupted during retry");
                return;
            } catch (Exception e) {
                // 非 IO 异常（如 JSON 解析错误），不重试
                callback.onError(e.getMessage());
                return;
            }
        }
    }

    /** 构建 HTTP 请求体 JSON */
    private String buildBody(List<Map<String, Object>> messages,
                              List<Map<String, Object>> tools) throws IOException {
        ONode body = new ONode(ONode.ofJson("{}").options()).asObject();
        body.set("model", model);
        if (reasoningEffort != null && !reasoningEffort.isEmpty()) {
            body.set("reasoning_effort", reasoningEffort);
        }

        ONode msgs = body.getOrNew("messages").asArray();
        for (Map<String, Object> m : messages) {
            // 防御：tool 消息必须有 tool_call_id，缺少时跳过该消息
            Object toolCallId = m.get("tool_call_id");
            if ("tool".equals(m.get("role")) && (toolCallId == null || String.valueOf(toolCallId).isEmpty())) {
                System.err.println("[WARN] buildBody: skipping tool message without tool_call_id");
                continue;
            }

            ONode msg = msgs.addNew();
            msg.set("role", String.valueOf(m.getOrDefault("role", "user")));
            Object content = m.get("content");
            if (content instanceof String) msg.set("content", (String) content);
            else if (content != null) msg.set("content", content.toString());

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) m.get("tool_calls");
            if (toolCalls != null) {
                ONode tcArray = msg.getOrNew("tool_calls").asArray();
                for (Map<String, Object> tc : toolCalls) {
                    ONode tcNode = tcArray.addNew();
                    tcNode.set("id", String.valueOf(tc.get("id")));
                    tcNode.set("type", "function");
                    ONode funcNode = tcNode.getOrNew("function");
                    funcNode.set("name", String.valueOf(tc.get("name")));
                    funcNode.set("arguments", String.valueOf(tc.getOrDefault("arguments", "{}")));
                }
            }
            Object reasoning = m.get("reasoning_content");
            if (reasoning instanceof String) msg.set("reasoning_content", (String) reasoning);
            if (toolCallId != null) msg.set("tool_call_id", String.valueOf(toolCallId));
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
        System.err.println("[DEBUG] request body size=" + jsonBody.length()
                + " chars, tools=" + (tools != null ? tools.size() : 0)
                + ", messages=" + messages.size());
        return jsonBody;
    }

    private static String readFully(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}
