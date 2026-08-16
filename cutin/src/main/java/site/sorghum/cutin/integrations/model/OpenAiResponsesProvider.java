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
 * OpenAI Responses 协议 Provider（/responses）。
 *
 * <p>支持同步调用、SSE 流式调用、system 指令提取、推理摘要、
 * 函数调用与用量统计；流式函数调用同样按 output_index 累加参数。</p>
 */
public final class OpenAiResponsesProvider implements ModelProvider {

    /** Provider 配置。 */
    private final ModelProviderConfig config;
    /** JSON 映射器。 */
    private final ObjectMapper mapper = new ObjectMapper();
    /** HTTP 传输层。 */
    private final HttpModelTransport transport;

    /** 按配置创建 Provider，endpoint 指向 /responses。 */
    public OpenAiResponsesProvider(ModelProviderConfig config) {
        this.config = config;
        this.transport = new HttpModelTransport(
            config.endpoint("/responses"),
            mapper,
            Map.of("Authorization", "Bearer " + config.apiKey())
        );
    }

    /** {@inheritDoc} */
    @Override
    public String id() {
        return config.id();
    }

    /** 同步调用：解析输出条目（消息、推理、函数调用）与用量。 */
    @Override
    public ModelResponse call(ModelCallRequest request) {
        JsonNode response = transport.post(buildBody(request, false));
        return new ModelResponse(
            parseMessage(response.path("output")),
            parseUsage(response.path("usage")),
            true
        );
    }

    /** 流式调用：按 Responses SSE 事件转换为 StreamChunk，并在流尾聚合工具调用。 */
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

    /** 构建 Responses 请求体：模型、指令、输入条目、工具与推理选项。 */
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
            ObjectNode reasoning = body.putObject("reasoning");
            reasoning.put("effort", reasoningEffort);
            reasoning.put("summary", "auto");
            body.putArray("include").add("reasoning.encrypted_content");
        }

        StringBuilder instructions = new StringBuilder();
        ArrayNode input = body.putArray("input");
        for (Message message : request.messages()) {
            if ("system".equals(message.role())) {
                if (message.content() != null && !message.content().isEmpty()) {
                    if (instructions.length() > 0) {
                        instructions.append("\n\n");
                    }
                    instructions.append(message.content());
                }
                continue;
            }
            toResponsesInput(message, input);
        }
        if (instructions.length() > 0) {
            body.put("instructions", instructions.toString());
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

    /** 把通用 Message 转换为 Responses input 条目，特殊处理 tool 结果与历史推理。 */
    private void toResponsesInput(Message message, ArrayNode input) {
        if ("tool".equals(message.role())) {
            ObjectNode item = input.addObject();
            item.put("type", "function_call_output");
            item.put("call_id", message.toolCallId() == null ? "" : message.toolCallId());
            item.put("output", message.content() == null || message.content().isEmpty()
                ? "ERROR tool execution failed or returned empty"
                : message.content());
            return;
        }

        Object responseReasoning = message.metadata("response_reasoning");
        if (responseReasoning != null) {
            try {
                JsonNode reasoningItem = mapper.readTree(String.valueOf(responseReasoning));
                if (reasoningItem instanceof ObjectNode reasoningObject) {
                    reasoningObject.remove("status");
                    input.add(reasoningObject);
                }
            } catch (Exception ignored) {
                // 无效的历史推理条目直接丢弃
            }
        }

        if (message.content() != null && !message.content().isEmpty()) {
            ObjectNode item = input.addObject();
            item.put("role", message.role());
            item.put("content", message.content());
        }
        if (message.hasToolCalls()) {
            for (ToolCall toolCall : message.toolCalls()) {
                ObjectNode item = input.addObject();
                item.put("type", "function_call");
                item.put("id", toolCall.id());
                item.put("call_id", toolCall.id());
                item.put("name", toolCall.toolId());
                item.put("arguments", writeArguments(toolCall.arguments()));
            }
        }
    }

    /** 把工具定义转换为 Responses 工具声明。 */
    private void addTools(ObjectNode body, List<ToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return;
        }
        ArrayNode node = body.putArray("tools");
        for (ToolDefinition tool : tools) {
            ObjectNode entry = node.addObject();
            entry.put("type", "function");
            entry.put("name", tool.id());
            entry.put("description", tool.description());
            entry.set("parameters", mapper.valueToTree(tool.inputSchema()));
        }
    }

    /** 解析 output 数组为最终 assistant 消息，合并正文、推理与函数调用。 */
    private Message parseMessage(JsonNode output) {
        StringBuilder content = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        List<ToolCall> calls = new ArrayList<>();
        JsonNode reasoningItem = null;
        for (JsonNode item : output) {
            String type = item.path("type").asText("");
            if ("reasoning".equals(type)) {
                reasoningItem = item;
                for (JsonNode part : item.path("summary")) {
                    reasoning.append(part.path("text").asText(""));
                }
            } else if ("message".equals(type)) {
                for (JsonNode part : item.path("content")) {
                    String partType = part.path("type").asText("");
                    if ("output_text".equals(partType) || "refusal".equals(partType)) {
                        content.append(part.path("text").asText(""));
                    }
                }
            } else if ("function_call".equals(type)) {
                String callId = item.path("call_id").asText("");
                if (callId.isEmpty()) {
                    callId = item.path("id").asText("");
                }
                String name = item.path("name").asText("");
                String arguments = item.path("arguments").asText("{}");
                calls.add(new ToolCall(callId, name, parseArguments(arguments), callId));
            }
        }
        Map<String, Object> metadata = new HashMap<>();
        if (reasoning.length() > 0) {
            metadata.put("reasoning_content", reasoning.toString());
        }
        if (reasoningItem != null) {
            metadata.put("response_reasoning", reasoningItem.toString());
        }
        return new Message("assistant", content.toString(), null, calls, metadata);
    }

    /** 解析 Responses 用量节点；缺失时返回零用量。 */
    private Usage parseUsage(JsonNode usage) {
        if (usage == null || usage.isNull()) {
            return Usage.ZERO;
        }
        long cacheRead = usage.path("input_tokens_details").path("cached_tokens").asLong(0);
        if (cacheRead == 0) {
            // 兼容旧版 Responses 用量中的顶层字段
            cacheRead = usage.path("cached_input_tokens").asLong(0);
        }
        return new Usage(
            usage.path("input_tokens").asLong(0),
            usage.path("output_tokens").asLong(0),
            usage.path("cost_micros").asLong(0),
            cacheRead,
            0
        );
    }

    /** 把单个 SSE 事件转换为 StreamChunk，并按事件类型累加状态。 */
    private Stream<StreamChunk> parseChunk(JsonNode chunk, Accumulator state) {
        Stream.Builder<StreamChunk> builder = Stream.builder();
        String type = chunk.path("type").asText("");
        switch (type) {
            case "response.output_text.delta", "response.refusal.delta" -> {
                String delta = chunk.path("delta").asText("");
                if (!delta.isEmpty()) {
                    builder.add(new StreamChunk(delta, Usage.ZERO));
                }
            }
            case "response.reasoning_summary_text.delta", "response.reasoning_text.delta" -> {
                String delta = chunk.path("delta").asText("");
                if (!delta.isEmpty()) {
                    builder.add(new StreamChunk("", delta, List.of(), List.of(), Usage.ZERO, Map.of(), false));
                }
            }
            case "response.output_item.added", "response.output_item.done" -> {
                JsonNode item = chunk.path("item");
                if (item != null && item.isObject()) {
                    state.setOutputItem(
                        chunk.path("output_index").asInt(0),
                        item,
                        "response.output_item.done".equals(type)
                    );
                }
            }
            case "response.function_call_arguments.delta" -> state.appendArguments(
                chunk.path("output_index").asInt(0),
                chunk.path("delta").asText("")
            );
            case "response.function_call_arguments.done" -> state.replaceArguments(
                chunk.path("output_index").asInt(0),
                chunk.path("arguments").asText("")
            );
            case "response.completed" -> {
                state.completed = true;
                JsonNode usage = chunk.path("response").path("usage");
                if (usage != null && !usage.isNull()) {
                    Usage delta = parseUsage(usage);
                    state.usage = state.usage.add(delta);
                    state.usageDelivered = true;
                    builder.add(new StreamChunk("", delta));
                }
            }
            case "response.failed", "response.incomplete", "error" -> {
                state.error = chunk.toString();
                builder.add(state.errorChunk());
            }
            default -> {
                // 生命周期事件不产生用户可见增量
            }
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
     * 流式累加器：按 output_index 记录函数调用，并在流尾生成完整调用。
     */
    private final class Accumulator {

        /** 流中出现的错误原文。 */
        private String error;
        /** 已累计的用量。 */
        private Usage usage = Usage.ZERO;
        /** 响应是否已完成。 */
        private boolean completed;
        /** 用量是否已通过增量块交付过。 */
        private boolean usageDelivered;
        /** 函数调用 output_index 到构建器的映射。 */
        private final Map<Integer, ToolCallBuilder> toolCalls = new LinkedHashMap<>();

        /** 从输出条目事件中登记或更新函数调用。 */
        private void setOutputItem(int index, JsonNode item, boolean replaceArguments) {
            if ("function_call".equals(item.path("type").asText(""))) {
                ToolCallBuilder builder = toolCalls.computeIfAbsent(index, ignored -> new ToolCallBuilder());
                String callId = item.path("call_id").asText("");
                if (callId.isEmpty()) {
                    callId = item.path("id").asText("");
                }
                if (!callId.isEmpty()) {
                    builder.id = callId;
                }
                String name = item.path("name").asText("");
                if (!name.isEmpty()) {
                    builder.name = name;
                }
                String arguments = item.path("arguments").asText(null);
                if (arguments != null) {
                    if (replaceArguments) {
                        builder.arguments = new StringBuilder(arguments);
                    } else {
                        builder.arguments.append(arguments);
                    }
                }
            }
        }

        /** 追加一段参数增量。 */
        private void appendArguments(int index, String arguments) {
            if (arguments == null) {
                return;
            }
            toolCalls.computeIfAbsent(index, ignored -> new ToolCallBuilder()).arguments.append(arguments);
        }

        /** 用完整参数覆盖增量参数。 */
        private void replaceArguments(int index, String arguments) {
            if (arguments == null) {
                return;
            }
            toolCalls.computeIfAbsent(index, ignored -> new ToolCallBuilder())
                .arguments = new StringBuilder(arguments);
        }

        /** 流结束时输出聚合出的函数调用与未交付的用量。 */
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

        /** 单个函数调用的增量构建器。 */
        private final class ToolCallBuilder {

            /** 调用 id。 */
            private String id = "";
            /** 函数名称。 */
            private String name = "";
            /** 参数 JSON 增量片段。 */
            private StringBuilder arguments = new StringBuilder();

            /** 把累计片段解析为完整 ToolCall。 */
            private ToolCall toToolCall() {
                return new ToolCall(id, name, parseArguments(arguments.toString()), id);
            }
        }
    }
}
