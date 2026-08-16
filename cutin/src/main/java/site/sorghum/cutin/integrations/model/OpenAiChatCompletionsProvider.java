package site.sorghum.cutin.integrations.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import site.sorghum.cutin.core.context.Message;
import site.sorghum.cutin.core.context.Usage;
import site.sorghum.cutin.core.model.*;
import site.sorghum.cutin.core.tool.ToolCall;
import site.sorghum.cutin.core.tool.ToolDefinition;

import java.util.*;
import java.util.stream.Stream;

/**
 * OpenAI Chat Completions 协议 Provider（/chat/completions）。
 *
 * <p>支持同步调用、SSE 流式调用、工具调用、推理内容与用量统计；
 * 流式工具调用通过按 index 累加增量片段的方式还原完整参数。</p>
 */
public final class OpenAiChatCompletionsProvider implements ModelProvider {

    /** Provider 配置。 */
    private final ModelProviderConfig config;
    /** JSON 映射器。 */
    private final ObjectMapper mapper = new ObjectMapper();
    /** HTTP 传输层。 */
    private final HttpModelTransport transport;

    /** 按配置创建 Provider，endpoint 指向 /chat/completions。 */
    public OpenAiChatCompletionsProvider(ModelProviderConfig config) {
        this.config = config;
        this.transport = new HttpModelTransport(
            config.endpoint("/chat/completions"),
            mapper,
            Map.of("Authorization", "Bearer " + config.apiKey())
        );
    }

    /** {@inheritDoc} */
    @Override
    public String id() {
        return config.id();
    }

    /** 同步调用：POST 请求后解析消息、工具调用与用量。 */
    @Override
    public ModelResponse call(ModelCallRequest request) {
        JsonNode response = transport.post(buildBody(request, false));
        Message message = parseMessage(response.path("choices").path(0).path("message"));
        Usage usage = parseUsage(response.path("usage"));
        return new ModelResponse(message, usage, true);
    }

    /** 流式调用：解析 SSE 增量块，并在流尾追加聚合出的工具调用与用量。 */
    @Override
    public Stream<StreamChunk> stream(ModelCallRequest request) {
        Accumulator accumulator = new Accumulator();
        Stream<StreamChunk> chunks = transport.postSse(buildBody(request, true))
            .flatMap(chunk -> parseChunk(chunk, accumulator));
        return Stream.concat(chunks, Stream.of(accumulator).flatMap(Accumulator::finalStream))
            .onClose(chunks::close);
    }

    /** {@inheritDoc} */
    @Override
    public ModelCapabilities capabilities() {
        return new ModelCapabilities(Set.of(config.model()), true, true);
    }

    /** 构建 Chat Completions 请求体：模型、流标记、消息、工具与扩展选项。 */
    private ObjectNode buildBody(ModelCallRequest request, boolean stream) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model(request));
        body.put("stream", stream);

        String serviceTier = option(request, "serviceTier");
        if (serviceTier != null && !serviceTier.isBlank()) {
            body.put("service_tier", serviceTier);
        }
        String reasoningEffort = reasoningEffort(request);
        if (reasoningEffort != null && !reasoningEffort.isBlank() && !"none".equals(reasoningEffort)) {
            body.put("reasoning_effort", reasoningEffort);
        }

        ArrayNode messages = body.putArray("messages");
        for (Message message : request.messages()) {
            messages.add(toOpenAiMessage(message));
        }

        addTools(body, request.tools());
        String userId = option(request, "userId");
        if (userId != null && !userId.isBlank()) {
            body.put("user", userId);
        }
        String sessionAffinity = option(request, "sessionAffinity");
        if (sessionAffinity != null && !sessionAffinity.isBlank()) {
            body.put("prompt_cache_key", sessionAffinity);
        }
        return body;
    }

    /** 把通用 Message 转换为 OpenAI 消息格式（含 tool_calls 与 reasoning_content）。 */
    private ObjectNode toOpenAiMessage(Message message) {
        ObjectNode node = mapper.createObjectNode();
        node.put("role", message.role());
        if (message.toolCallId() != null) {
            node.put("tool_call_id", message.toolCallId());
        }
        if ("tool".equals(message.role())
            && (message.content() == null || message.content().isEmpty())) {
            node.put("content", "ERROR tool execution failed or returned empty");
        } else if (message.content() != null) {
            node.put("content", message.content());
        }
        Object reasoning = message.metadata("reasoning_content");
        if (reasoning != null) {
            node.put("reasoning_content", String.valueOf(reasoning));
        }
        if (message.hasToolCalls()) {
            ArrayNode toolCalls = node.putArray("tool_calls");
            for (ToolCall toolCall : message.toolCalls()) {
                ObjectNode call = toolCalls.addObject();
                call.put("id", toolCall.id());
                call.put("type", "function");
                ObjectNode function = call.putObject("function");
                function.put("name", toolCall.toolId());
                function.put("arguments", writeArguments(toolCall.arguments()));
            }
        }
        return node;
    }

    /** 把工具定义转换为 OpenAI function 声明。 */
    private void addTools(ObjectNode body, List<ToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return;
        }
        ArrayNode node = body.putArray("tools");
        for (ToolDefinition tool : tools) {
            ObjectNode entry = node.addObject();
            entry.put("type", "function");
            ObjectNode function = entry.putObject("function");
            function.put("name", tool.id());
            function.put("description", tool.description());
            function.set("parameters", mapper.valueToTree(tool.inputSchema()));
        }
    }

    /** 解析响应的 assistant 消息，包括正文、推理与工具调用。 */
    private Message parseMessage(JsonNode message) {
        String content = message.path("content").asText("");
        String reasoning = text(message, "reasoning_content");
        Map<String, Object> metadata = new HashMap<>();
        if (reasoning != null && !reasoning.isEmpty()) {
            metadata.put("reasoning_content", reasoning);
        }
        return new Message(
            "assistant",
            content,
            null,
            parseToolCalls(message.path("tool_calls")),
            metadata
        );
    }

    /** 解析工具调用数组为 ToolCall 列表。 */
    private List<ToolCall> parseToolCalls(JsonNode toolCalls) {
        if (toolCalls == null || toolCalls.isNull() || !toolCalls.isArray()) {
            return List.of();
        }
        List<ToolCall> calls = new ArrayList<>();
        for (JsonNode call : toolCalls) {
            String callId = call.path("id").asText("");
            String name = call.path("function").path("name").asText("");
            String argumentsJson = call.path("function").path("arguments").asText("{}");
            calls.add(new ToolCall(callId, name, parseArguments(argumentsJson), callId));
        }
        return calls;
    }

    /** 解析用量节点；缺失时返回零用量。 */
    private Usage parseUsage(JsonNode usage) {
        if (usage == null || usage.isNull()) {
            return Usage.ZERO;
        }
        long promptTokens = usage.path("prompt_tokens").asLong(0);
        long cacheRead = usage.path("prompt_tokens_details").path("cached_tokens").asLong(0);
        if (cacheRead == 0) {
            // DeepSeek 等兼容协议使用顶层字段上报缓存命中
            cacheRead = usage.path("prompt_cache_hit_tokens").asLong(0);
        }
        return new Usage(
            promptTokens,
            usage.path("completion_tokens").asLong(0),
            usage.path("cost_micros").asLong(0),
            cacheRead,
            0
        );
    }

    /** 把单个 SSE 增量块转换为若干 StreamChunk，并累计工具调用与用量。 */
    private Stream<StreamChunk> parseChunk(JsonNode chunk, Accumulator state) {
        Stream.Builder<StreamChunk> builder = Stream.builder();
        JsonNode error = chunk.get("error");
        if (error != null && !error.isMissingNode() && !error.isNull()) {
            state.error = chunk.toString();
            builder.add(state.errorChunk());
            return builder.build();
        }

        JsonNode usage = chunk.path("usage");
        if (usage != null && !usage.isNull()) {
            Usage delta = parseUsage(usage);
            state.usage = state.usage.add(delta);
            state.usageDelivered = true;
            builder.add(new StreamChunk("", delta));
        }

        JsonNode delta = chunk.path("choices").path(0).path("delta");
        if (delta == null || delta.isNull()) {
            return builder.build();
        }
        String reasoning = text(delta, "reasoning_content");
        if (reasoning == null || reasoning.isEmpty()) {
            reasoning = text(delta, "reasoning");
        }
        if (reasoning != null && !reasoning.isEmpty()) {
            builder.add(new StreamChunk("", reasoning, List.of(), List.of(), Usage.ZERO, Map.of(), false));
        }
        String content = text(delta, "content");
        if (content != null && !content.isEmpty()) {
            builder.add(new StreamChunk(content, Usage.ZERO));
        }
        JsonNode toolCalls = delta.path("tool_calls");
        if (toolCalls != null && toolCalls.isArray()) {
            state.accumulateToolCalls(toolCalls);
        }
        return builder.build();
    }

    /** 取请求模型 id，为空时回退到配置中的默认模型。 */
    private String model(ModelCallRequest request) {
        return request.modelId() == null || request.modelId().isBlank()
            ? config.model()
            : request.modelId();
    }

    /** 取请求级推理力度，未设置时回退到配置。 */
    private String reasoningEffort(ModelCallRequest request) {
        String value = option(request, "reasoningEffort");
        return value == null ? config.reasoningEffort() : value;
    }

    /** 读取请求选项中的字符串值。 */
    private String option(ModelCallRequest request, String key) {
        Object value = request.options().get(key);
        return value == null ? null : String.valueOf(value);
    }

    /** 从节点中取字符串字段，缺失或 null 时返回 null。 */
    private static String text(JsonNode node, String key) {
        JsonNode value = node.get(key);
        return value == null || value.isNull() ? null : value.asText();
    }

    /** 解析 JSON 参数串；解析失败时返回空 Map。 */
    private Map<String, Object> parseArguments(String json) {
        try {
            return mapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception exception) {
            return Map.of();
        }
    }

    /** 序列化参数为 JSON 字符串；失败时返回空对象串。 */
    private String writeArguments(Map<String, Object> arguments) {
        try {
            return mapper.writeValueAsString(arguments);
        } catch (Exception exception) {
            return "{}";
        }
    }

    /**
     * 流式累加器：按工具调用 index 累计片段，并在流尾生成完整工具调用。
     */
    private final class Accumulator {

        /** 流中出现的错误原文。 */
        private String error;
        /** 已累计的用量。 */
        private Usage usage = Usage.ZERO;
        /** 用量是否已通过增量块交付过。 */
        private boolean usageDelivered;
        /** 工具调用 index 到构建器的映射。 */
        private final Map<Integer, ToolCallBuilder> toolCalls = new LinkedHashMap<>();

        /** 按 index 累加流式工具调用增量片段。 */
        private void accumulateToolCalls(JsonNode deltas) {
            for (JsonNode delta : deltas) {
                int index = delta.path("index").asInt(0);
                ToolCallBuilder builder = toolCalls.computeIfAbsent(index, ignored -> new ToolCallBuilder());
                JsonNode function = delta.path("function");
                if (function.isNull()) {
                    continue;
                }
                String id = text(delta, "id");
                if (id != null && !id.isEmpty()) {
                    builder.id = id;
                }
                String name = text(function, "name");
                if (name != null && !name.isEmpty()) {
                    builder.name = name;
                }
                String arguments = text(function, "arguments");
                if (arguments != null) {
                    builder.arguments.append(arguments);
                }
            }
        }

        /** 流结束时输出聚合出的工具调用与未交付的用量。 */
        private Stream<StreamChunk> finalStream() {
            if (error != null) {
                return Stream.empty();
            }
            List<ToolCall> calls = new ArrayList<>();
            for (ToolCallBuilder builder : toolCalls.values()) {
                calls.add(builder.toToolCall());
            }
            return Stream.of(new StreamChunk(
                "",
                null,
                calls,
                List.of(),
                usageDelivered ? Usage.ZERO : usage,
                Map.of(),
                true
            ));
        }

        /** 构造携带错误原文的终止块。 */
        private StreamChunk errorChunk() {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("error", error);
            return new StreamChunk("", null, List.of(), List.of(), usage, metadata, true);
        }

        /** 单个流式工具调用的增量构建器。 */
        private final class ToolCallBuilder {

            /** 工具调用 id。 */
            private String id = "";
            /** 工具名称。 */
            private String name = "";
            /** 参数 JSON 增量片段。 */
            private final StringBuilder arguments = new StringBuilder();

            /** 把累计片段解析为完整 ToolCall。 */
            private ToolCall toToolCall() {
                return new ToolCall(id, name, parseArguments(arguments.toString()), id);
            }
        }
    }
}
