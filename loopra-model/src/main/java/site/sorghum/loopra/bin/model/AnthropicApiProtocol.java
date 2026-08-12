package site.sorghum.loopra.bin.model;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import org.noear.snack4.ONode;
import site.sorghum.loopra.bin.agent.model.ChatMessage;
import site.sorghum.loopra.bin.agent.model.ToolCallEntry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Anthropic Messages API request and response mapping.
 *
 * <p>协议差异点（相对 OpenAI 兼容协议）：</p>
 * <ul>
 *   <li>认证头为 {@code x-api-key} + {@code anthropic-version}，而非 Bearer</li>
 *   <li>请求体必填 {@code max_tokens}；system 消息抽取到顶层 {@code system} 字段</li>
 *   <li>工具结果以 user 角色的 {@code tool_result} 内容块回传，且相邻同角色消息必须合并</li>
 *   <li>工具定义使用 {@code input_schema}，工具调用参数是对象 {@code input} 而非字符串</li>
 *   <li>流式事件由 SSE {@code data} 中的 {@code type} 字段区分</li>
 * </ul>
 */
@Slf4j
final class AnthropicApiProtocol extends AbstractModelApiProtocol {

    static final String PROTOCOL_NAME = "anthropic";

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    /** Anthropic 必填的 max_tokens 默认值。 */
    private static final int DEFAULT_MAX_TOKENS = 8192;

    private static final ONode CACHE_CONTROL = ONode.ofJson("{\"type\":\"ephemeral\"}");

    @Override
    public String name() {
        return PROTOCOL_NAME;
    }

    @Override
    public void applyAuthHeaders(Request.Builder builder, String apiKey) {
        // 官方 Anthropic 与 DeepSeek 兼容网关认 x-api-key；小米 MiMo 兼容网关认 api-key。
        // 同时发送两个头，各网关只读取自己认识的那个，保证跨平台可用。
        builder.addHeader("x-api-key", apiKey == null ? "" : apiKey);
        builder.addHeader("api-key", apiKey == null ? "" : apiKey);
        builder.addHeader("anthropic-version", ANTHROPIC_VERSION);
    }

    @Override
    public ONode buildRequest(RequestContext context) {
        ONode body = ONode.ofJson("{}");
        body.set(MODEL, ModelContextUtils.stripContextSizeSuffix(context.model()));
        // 不映射 reasoningEffort 到 thinking：Claude 4.7+ 拒绝 thinking.type=enabled（返回 400），
        // 且 sampling 参数（temperature 等）在新模型同样被拒。不发送即使用官方默认行为
        // （Claude 4.7+ 默认 adaptive thinking，旧模型默认关闭），任何网关均安全。
        body.set("max_tokens", DEFAULT_MAX_TOKENS);

        StringBuilder systemText = new StringBuilder();
        List<ONode> messages = new ArrayList<>();
        for (ChatMessage message : context.messages()) {
            if (message.isSystem()) {
                if (message.getContent() != null && !message.getContent().isEmpty()) {
                    if (!systemText.isEmpty()) systemText.append("\n\n");
                    systemText.append(message.getContent());
                }
                continue;
            }
            ONode converted = toAnthropicMessage(message);
            if (converted != null) messages.add(converted);
        }

        if (systemText.length() > 0) {
            if (context.sessionId() != null && !context.sessionId().isEmpty()) {
                ONode systemBlock = ONode.ofJson("{}");
                systemBlock.set(TYPE, "text");
                systemBlock.set("text", systemText.toString());
                systemBlock.set("cache_control", CACHE_CONTROL);
                body.getOrNew("system").asArray().add(systemBlock);
            } else {
                body.set("system", systemText.toString());
            }
        }

        // Anthropic 要求 user/assistant 角色交替：将连续的同角色 user（含工具结果）合并为一个消息。
        ONode bodyMessages = body.getOrNew("messages").asArray();
        ONode pendingUser = null;
        for (ONode message : messages) {
            if ("user".equals(message.get("role").getString())) {
                if (pendingUser == null) {
                    pendingUser = message;
                } else {
                    ONode merged = ONode.ofJson("[]").asArray();
                    for (ONode block : pendingUser.get(CONTENT).getArray()) merged.add(block);
                    for (ONode block : message.get(CONTENT).getArray()) merged.add(block);
                    pendingUser.set(CONTENT, merged);
                }
                continue;
            }
            if (pendingUser != null) {
                finalizeContent(pendingUser);
                bodyMessages.add(pendingUser);
                pendingUser = null;
            }
            finalizeContent(message);
            bodyMessages.add(message);
        }
        if (pendingUser != null) {
            finalizeContent(pendingUser);
            bodyMessages.add(pendingUser);
        }

        if (context.tools() != null && !context.tools().isEmpty()) {
            ONode tools = body.getOrNew(TOOLS).asArray();
            for (ONode tool : context.tools().getArray()) {
                ONode function = tool.get(FUNCTION);
                if (function == null || function.isNull()) continue;
                String name = function.get(NAME).getString();
                if (name == null || name.isEmpty()) continue;
                ONode converted = ONode.ofJson("{}");
                converted.set(NAME, name);
                if (!function.get("description").isNull()) {
                    converted.set("description", function.get("description"));
                }
                ONode parameters = function.get("parameters");
                converted.set("input_schema", parameters == null || parameters.isNull()
                        ? ONode.ofJson("{\"type\":\"object\"}") : parameters);
                if (context.sessionId() != null && !context.sessionId().isEmpty()) {
                    converted.set("cache_control", CACHE_CONTROL);
                }
                tools.add(converted);
            }
        }

        if (context.userId() != null && !context.userId().isEmpty()) {
            // DeepSeek 等网关用 metadata.user_id 做限流隔离；官方 Anthropic 亦支持该字段。
            body.getOrNew("metadata").set("user_id", context.userId());
        }
        if (context.sessionId() != null && !context.sessionId().isEmpty()) {
            applyLastUserCacheControl(bodyMessages);
        }
        return body;
    }

    /** 会话亲和时在最后一个 user 消息上打缓存标记，与 system/tools 一起形成完整 prompt cache。 */
    private static void applyLastUserCacheControl(ONode messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ONode message = messages.get(i);
            if (!"user".equals(message.get("role").getString())) continue;
            ONode content = message.get(CONTENT);
            if (content == null || content.isNull()) return;
            if (content.isString()) {
                ONode blocks = ONode.ofJson("[]").asArray();
                ONode block = blocks.addNew().asObject();
                block.set(TYPE, "text");
                block.set("text", content.getString());
                block.set("cache_control", CACHE_CONTROL);
                message.set(CONTENT, blocks);
            } else if (content.isArray() && !content.isEmpty()) {
                content.get(content.size() - 1).set("cache_control", CACHE_CONTROL);
            }
            return;
        }
    }

    private static ONode toAnthropicMessage(ChatMessage message) {
        if (message.isTool()) {
            if (message.getToolCallId() == null || message.getToolCallId().isEmpty()) return null;
            ONode block = ONode.ofJson("{}");
            block.set(TYPE, "tool_result");
            block.set("tool_use_id", message.getToolCallId());
            String contentText = message.getContent() == null || message.getContent().isEmpty()
                    ? "ERROR 工具执行失败或者工具执行结果为空" : message.getContent();
            if (message.hasToolImage()) {
                ONode content = block.getOrNew(CONTENT).asArray();
                content.addNew().set(TYPE, "text").set("text", contentText);
                content.add(imageBlock(message.getToolImageUrl()));
            } else {
                block.set(CONTENT, contentText);
            }
            ONode node = ONode.ofJson("{}");
            node.set("role", "user");
            node.set(CONTENT, ONode.ofJson("[]").asArray().add(block));
            return node;
        }
        if (message.isUser()) {
            ONode content = ONode.ofJson("[]").asArray();
            if (message.getContentParts() != null && !message.getContentParts().isEmpty()) {
                for (ChatMessage.ContentPart part : message.getContentParts()) {
                    if ("text".equals(part.getType())) {
                        content.addNew().set(TYPE, "text").set("text", part.getText() == null ? "" : part.getText());
                    } else if ("image_url".equals(part.getType()) && part.getImageUrl() != null) {
                        content.add(imageBlock(part.getImageUrl().getUrl()));
                    }
                }
            } else if (message.getContent() != null && !message.getContent().isEmpty()) {
                content.addNew().set(TYPE, "text").set("text", message.getContent());
            }
            if (content.isEmpty()) return null;
            ONode node = ONode.ofJson("{}");
            node.set("role", "user");
            node.set(CONTENT, content);
            return node;
        }
        if (message.isAssistant()) {
            ONode content = ONode.ofJson("[]").asArray();
            if (message.getContent() != null && !message.getContent().isEmpty()) {
                content.addNew().set(TYPE, "text").set("text", message.getContent());
            }
            if (message.hasToolCalls()) {
                for (ToolCallEntry toolCall : message.getToolCalls()) {
                    ONode block = content.addNew().asObject();
                    block.set(TYPE, "tool_use");
                    block.set(ID, toolCall.id());
                    block.set(NAME, toolCall.name());
                    block.set("input", argumentsNode(toolCall.arguments()));
                }
            }
            if (content.isEmpty()) return null;
            ONode node = ONode.ofJson("{}");
            node.set("role", "assistant");
            node.set(CONTENT, content);
            return node;
        }
        return null;
    }

    /** 工具参数在 Anthropic 中必须是 JSON 对象，而非字符串。 */
    private static ONode argumentsNode(Object arguments) {
        if (arguments instanceof String json) {
            try {
                return ONode.ofJson(json);
            } catch (Exception e) {
                return ONode.ofJson("{}");
            }
        }
        return arguments == null ? ONode.ofJson("{}") : ONode.ofJson(ONode.serialize(arguments));
    }

    private static ONode imageBlock(String url) {
        ONode image = ONode.ofJson("{}");
        image.set(TYPE, "image");
        ONode source = image.getOrNew("source");
        if (url != null && url.startsWith("data:")) {
            int comma = url.indexOf(',');
            if (comma > 0) {
                String mediaType = url.substring(5, comma).split(";")[0];
                source.set(TYPE, "base64");
                source.set("media_type", mediaType.isBlank() ? "image/png" : mediaType);
                source.set("data", url.substring(comma + 1));
                return image;
            }
        }
        source.set(TYPE, "url");
        source.set("url", url == null ? "" : url);
        return image;
    }

    /** 单个纯文本块收敛为字符串，其余保持数组（Anthropic 两种形式都接受）。 */
    private static void finalizeContent(ONode message) {
        ONode content = message.get(CONTENT);
        if (content == null || !content.isArray() || content.size() != 1) return;
        ONode block = content.get(0);
        if ("text".equals(block.get(TYPE).getString())) {
            message.set(CONTENT, block.get("text").getString());
        }
    }

    @Override
    public ONode parseResponse(ONode response, String responseText) throws IOException {
        ONode content = response.get(CONTENT);
        if (content == null || !content.isArray()) {
            throw new IOException("No content in response: " + responseText);
        }

        ONode message = ONode.ofJson("{}");
        message.set("role", "assistant");
        StringBuilder text = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        ONode toolCalls = ONode.ofJson("[]").asArray();
        for (ONode block : content.getArray()) {
            String type = block.get(TYPE).getString();
            if ("text".equals(type)) {
                String token = block.get("text").getString();
                if (token != null) text.append(token);
            } else if ("thinking".equals(type)) {
                String token = block.get("thinking").getString();
                if (token != null) reasoning.append(token);
            } else if ("tool_use".equals(type)) {
                ONode call = toolCalls.addNew();
                call.set(ID, block.get(ID).getString());
                call.set(TYPE, FUNCTION);
                ONode function = call.getOrNew(FUNCTION);
                function.set(NAME, block.get(NAME).getString());
                ONode input = block.get("input");
                function.set(ARGUMENTS, input == null || input.isNull() ? "{}" : input.toJson());
            }
        }
        message.set(CONTENT, text.toString());
        if (!reasoning.isEmpty()) message.set(REASONING_CONTENT, reasoning.toString());
        if (!toolCalls.isEmpty()) message.set("tool_calls", toolCalls);
        return message;
    }

    @Override
    public void processStreamChunk(ONode chunk, ModelClient.StreamCallback callback,
                                   ModelApiStreamState state) {
        if (state.completed) return;
        String eventType = chunk.get(TYPE).getString();
        if (eventType == null) return;
        switch (eventType) {
            case "message_start" -> {
                ONode message = chunk.get("message");
                ONode usage = message.get(USAGE);
                if (usage != null && !usage.isNull()) state.anthropicInputUsage = usage;
            }
            case "content_block_start" -> contentBlockStart(
                    chunk.get("content_block"), chunk.get("index").getInt(), state);
            case "content_block_delta" -> contentBlockDelta(
                    chunk.get("delta"), chunk.get("index").getInt(), callback, state);
            case "message_delta" -> {
                ONode usage = chunk.get(USAGE);
                if (usage != null && !usage.isNull()) emitUsage(usage, callback, state);
            }
            case "message_stop" -> state.completed = true;
            case "error" -> {
                state.errorData = chunk.toJson();
                String errorType = chunk.select("$.error.type").getString();
                state.contextLengthExceeded = ModelApiError.isContextLengthExceeded(state.errorData)
                        || "context_length_exceeded".equals(errorType);
                state.invalidRequestError = ModelApiError.isInvalidRequestError(state.errorData)
                        || "invalid_request_error".equals(errorType);
                state.retryableError = "overloaded_error".equals(errorType)
                        || "rate_limit_error".equals(errorType)
                        || "api_error".equals(errorType)
                        || "internal_server_error".equals(errorType);
                log.warn("收到 Anthropic 流错误: {}", state.errorData);
            }
            default -> {
                // ping 等生命周期事件不包含可见增量。
            }
        }
    }

    @Override
    public String streamCompletionError(ModelApiStreamState state) {
        return state.completed ? null : "Anthropic Messages stream ended before message_stop";
    }

    private void contentBlockStart(ONode block, int index, ModelApiStreamState state) {
        if (block == null || block.isNull()) return;
        if (!"tool_use".equals(block.get(TYPE).getString())) return;
        ONode call = toolCall(index, state);
        String id = block.get(ID).getString();
        if (id != null && !id.isEmpty()) call.set(ID, id);
        String name = block.get(NAME).getString();
        if (name != null && !name.isEmpty()) call.getOrNew(FUNCTION).set(NAME, name);
    }

    private void contentBlockDelta(ONode delta, int index, ModelClient.StreamCallback callback,
                                   ModelApiStreamState state) {
        if (delta == null || delta.isNull()) return;
        String deltaType = delta.get(TYPE).getString();
        if ("text_delta".equals(deltaType)) {
            String text = delta.get("text").getString();
            if (text != null && !text.isEmpty()) {
                state.emittedOutput = true;
                processContentToken(text, callback, state);
            }
        } else if ("thinking_delta".equals(deltaType)) {
            String thinking = delta.get("thinking").getString();
            if (thinking != null && !thinking.isEmpty()) {
                state.emittedOutput = true;
                state.emittedReasoning = true;
                safeCallback("onReasoningDelta", () -> callback.onReasoningDelta(thinking));
            }
        } else if ("input_json_delta".equals(deltaType)) {
            String partial = delta.get("partial_json").getString();
            if (partial != null) {
                ONode function = toolCall(index, state).getOrNew(FUNCTION);
                String previous = function.get(ARGUMENTS).getString();
                function.set(ARGUMENTS, (previous == null ? "" : previous) + partial);
            }
        }
        // signature_delta 等其他增量类型不包含用户可见内容，忽略。
    }

    /** Anthropic 将输入/输出用量分开发送：message_start 带输入侧，message_delta 带输出侧。 */
    private void emitUsage(ONode outputUsage, ModelClient.StreamCallback callback,
                           ModelApiStreamState state) {
        ONode combined = ONode.ofJson("{}");
        ONode inputUsage = state.anthropicInputUsage;
        combined.set("input_tokens", inputUsage == null || inputUsage.get("input_tokens").isNull()
                ? 0 : inputUsage.get("input_tokens").getInt());
        combined.set("output_tokens", outputUsage.get("output_tokens").isNull()
                ? 0 : outputUsage.get("output_tokens").getInt());
        combined.set("cache_read_input_tokens", inputUsage == null
                || inputUsage.get("cache_read_input_tokens").isNull()
                ? 0 : inputUsage.get("cache_read_input_tokens").getInt());
        combined.set("cache_creation_input_tokens", inputUsage == null
                || inputUsage.get("cache_creation_input_tokens").isNull()
                ? 0 : inputUsage.get("cache_creation_input_tokens").getInt());
        // 官方文档确认 message_delta 的 usage 是累计值，且一个流可能多次发送 message_delta
        // （如 server-side fallback）。固定 requestId 让 handleUsage 首次回调完整值、
        // 后续只回调与上一次的差值（input/cache 不变，差值集中在 output），避免上游重复累计。
        handleUsage(combined, USAGE_REQUEST_ID, callback, state.lastUsage);
    }

    /** Anthropic 流内固定用量请求标识（每个 ModelApiStreamState 独立，重试互不影响）。 */
    private static final String USAGE_REQUEST_ID = "anthropic";

    private ONode toolCall(int index, ModelApiStreamState state) {
        if (state.toolCalls == null) state.toolCalls = ONode.ofJson("[]").asArray();
        while (state.toolCalls.size() <= index) {
            ONode call = state.toolCalls.addNew();
            call.set(TYPE, FUNCTION);
            call.getOrNew(FUNCTION).set(ARGUMENTS, "");
        }
        return state.toolCalls.get(index);
    }
}
