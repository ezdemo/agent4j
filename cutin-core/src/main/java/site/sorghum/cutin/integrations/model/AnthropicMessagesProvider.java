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
 * Anthropic Messages 协议 Provider（/v1/messages）。
 *
 * <p>支持同步调用、SSE 流式调用、system 提取、thinking 块、
 * tool_use 与 tool_result、用量统计；消息转换时会合并连续的 user 内容块，
 * 以满足 Anthropic 相邻 user 消息需要合并的协议要求。</p>
 */
public final class AnthropicMessagesProvider implements ModelProvider {

    /** Anthropic API 版本头。 */
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    /** 默认最大输出 token。 */
    private static final int DEFAULT_MAX_TOKENS = 8192;

    /** Provider 配置。 */
    private final ModelProviderConfig config;
    /** JSON 映射器。 */
    private final ObjectMapper mapper = new ObjectMapper();
    /** HTTP 传输层。 */
    private final HttpModelTransport transport;

    /** 按配置创建 Provider，endpoint 指向 /v1/messages。 */
    public AnthropicMessagesProvider(ModelProviderConfig config) {
        this.config = config;
        this.transport = new HttpModelTransport(
            config.endpoint("/v1/messages"),
            mapper,
            Map.of(
                "x-api-key", config.apiKey(),
                "api-key", config.apiKey(),
                "anthropic-version", ANTHROPIC_VERSION
            )
        );
    }

    /** {@inheritDoc} */
    @Override
    public String id() {
        return config.id();
    }

    /** 同步调用：解析内容块（text、thinking、tool_use）与用量。 */
    @Override
    public ModelResponse call(ModelCallRequest request) {
        JsonNode response = transport.post(buildBody(request, false));
        return new ModelResponse(
            parseMessage(response.path("content")),
            parseUsage(response.path("usage")),
            true
        );
    }

    /** 流式调用：按 Anthropic SSE 事件转换为 StreamChunk，并在流尾聚合工具与 thinking。 */
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

    /** 构建 Messages 请求体：模型、max_tokens、system、消息、工具与流标记。 */
    private ObjectNode buildBody(ModelCallRequest request, boolean stream) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model(request));
        body.put("max_tokens", maxTokens(request));
        body.put("stream", stream);

        StringBuilder system = new StringBuilder();
        List<ObjectNode> messages = new ArrayList<>();
        for (Message message : request.messages()) {
            if ("system".equals(message.role())) {
                if (message.content() != null && !message.content().isEmpty()) {
                    if (system.length() > 0) {
                        system.append("\n\n");
                    }
                    system.append(message.content());
                }
                continue;
            }
            ObjectNode converted = toAnthropicMessage(message);
            if (converted != null) {
                messages.add(converted);
            }
        }
        if (system.length() > 0) {
            body.put("system", system.toString());
        }

        ArrayNode bodyMessages = body.putArray("messages");
        ObjectNode pendingUser = null;
        for (ObjectNode message : messages) {
            if ("user".equals(message.path("role").asText())) {
                if (pendingUser == null) {
                    pendingUser = message;
                } else {
                    ArrayNode merged = mapper.createArrayNode();
                    merged.addAll(pendingUser.withArray("content"));
                    merged.addAll(message.withArray("content"));
                    pendingUser.set("content", merged);
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

        addTools(body, request.tools());
        String userId = option(request, "userId");
        if (userId != null && !userId.isBlank()) {
            body.putObject("metadata").put("user_id", userId);
        }
        return body;
    }

    /** 把通用 Message 转换为 Anthropic 消息内容块，特殊处理 tool_result 与 thinking。 */
    private ObjectNode toAnthropicMessage(Message message) {
        if ("tool".equals(message.role())) {
            ObjectNode node = mapper.createObjectNode();
            node.put("role", "user");
            ObjectNode block = node.putArray("content").addObject();
            block.put("type", "tool_result");
            block.put("tool_use_id", message.toolCallId() == null ? "" : message.toolCallId());
            block.put("content", message.content() == null || message.content().isEmpty()
                ? "ERROR tool execution failed or returned empty"
                : message.content());
            return node;
        }
        if ("user".equals(message.role())) {
            ObjectNode node = mapper.createObjectNode();
            node.put("role", "user");
            ArrayNode content = node.putArray("content");
            if (message.content() != null && !message.content().isEmpty()) {
                ObjectNode block = content.addObject();
                block.put("type", "text");
                block.put("text", message.content());
            }
            if (content.isEmpty()) {
                return null;
            }
            return node;
        }
        if ("assistant".equals(message.role())) {
            ObjectNode node = mapper.createObjectNode();
            node.put("role", "assistant");
            ArrayNode content = node.putArray("content");
            Object thinkingBlocks = message.metadata("thinking_blocks");
            if (thinkingBlocks instanceof List<?> blocks) {
                for (Object blockValue : blocks) {
                    try {
                        JsonNode block = mapper.readTree(String.valueOf(blockValue));
                        String type = block.path("type").asText("");
                        if ("thinking".equals(type) || "redacted_thinking".equals(type)) {
                            content.add(block);
                        }
                    } catch (Exception ignored) {
                        // 损坏的历史思考块直接跳过
                    }
                }
            }
            if (message.content() != null && !message.content().isEmpty()) {
                ObjectNode block = content.addObject();
                block.put("type", "text");
                block.put("text", message.content());
            }
            for (ToolCall toolCall : message.toolCalls()) {
                ObjectNode block = content.addObject();
                block.put("type", "tool_use");
                block.put("id", toolCall.id());
                block.put("name", toolCall.toolId());
                block.set("input", mapper.valueToTree(toolCall.arguments()));
            }
            if (content.isEmpty()) {
                return null;
            }
            return node;
        }
        return null;
    }

    /** 把工具定义转换为 Anthropic tool 声明。 */
    private void addTools(ObjectNode body, List<ToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return;
        }
        ArrayNode node = body.putArray("tools");
        for (ToolDefinition tool : tools) {
            ObjectNode entry = node.addObject();
            entry.put("name", tool.id());
            entry.put("description", tool.description());
            entry.set("input_schema", mapper.valueToTree(tool.inputSchema()));
        }
    }

    /** 解析响应内容块为最终 assistant 消息，合并 text、thinking 与 tool_use。 */
    private Message parseMessage(JsonNode content) {
        StringBuilder text = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        List<ToolCall> calls = new ArrayList<>();
        List<String> thinkingBlocks = new ArrayList<>();
        for (JsonNode block : content) {
            String type = block.path("type").asText("");
            if ("text".equals(type)) {
                text.append(block.path("text").asText(""));
            } else if ("thinking".equals(type)) {
                reasoning.append(block.path("thinking").asText(""));
                thinkingBlocks.add(block.toString());
            } else if ("redacted_thinking".equals(type)) {
                thinkingBlocks.add(block.toString());
            } else if ("tool_use".equals(type)) {
                String callId = block.path("id").asText("");
                String name = block.path("name").asText("");
                Map<String, Object> input = mapper.convertValue(
                    block.path("input"),
                    new TypeReference<>() {
                    }
                );
                calls.add(new ToolCall(callId, name, input, callId));
            }
        }
        Map<String, Object> metadata = new HashMap<>();
        if (reasoning.length() > 0) {
            metadata.put("reasoning_content", reasoning.toString());
        }
        if (!thinkingBlocks.isEmpty()) {
            metadata.put("thinking_blocks", List.copyOf(thinkingBlocks));
        }
        return new Message("assistant", text.toString(), null, calls, metadata);
    }

    /** 解析用量节点；缺失时返回零用量。 */
    private Usage parseUsage(JsonNode usage) {
        if (usage == null || usage.isNull()) {
            return Usage.ZERO;
        }
        long inputTokens = usage.path("input_tokens").asLong(0);
        long cacheRead = usage.path("cache_read_input_tokens").asLong(0);
        long cacheCreation = usage.path("cache_creation_input_tokens").asLong(0);
        return new Usage(
            // Anthropic 的 input_tokens 不含缓存读写，这里合并为全部输入
            inputTokens + cacheRead + cacheCreation,
            usage.path("output_tokens").asLong(0),
            usage.path("cost_micros").asLong(0),
            cacheRead,
            cacheCreation
        );
    }

    /** 把单个 SSE 事件转换为 StreamChunk，并按事件类型累加 thinking、工具与用量。 */
    private Stream<StreamChunk> parseChunk(JsonNode chunk, Accumulator state) {
        Stream.Builder<StreamChunk> builder = Stream.builder();
        String type = chunk.path("type").asText("");
        switch (type) {
            case "message_start" -> {
                JsonNode usage = chunk.path("message").path("usage");
                if (usage != null && !usage.isNull()) {
                    state.inputUsage = usage;
                }
            }
            case "content_block_start" -> state.contentBlockStart(
                chunk.path("content_block"),
                chunk.path("index").asInt(0)
            );
            case "content_block_delta" -> {
                JsonNode delta = chunk.path("delta");
                String deltaType = delta.path("type").asText("");
                switch (deltaType) {
                    case "text_delta" -> {
                        String text = delta.path("text").asText("");
                        if (!text.isEmpty()) {
                            builder.add(new StreamChunk(text, Usage.ZERO));
                        }
                    }
                    case "thinking_delta" -> {
                        String thinking = delta.path("thinking").asText("");
                        if (!thinking.isEmpty()) {
                            state.appendThinking(thinking);
                            builder.add(new StreamChunk(
                                "",
                                thinking,
                                List.of(),
                                List.of(),
                                Usage.ZERO,
                                Map.of(),
                                false
                            ));
                        }
                    }
                    case "signature_delta" -> state.appendSignature(delta.path("signature").asText(""));
                    case "redacted_thinking_delta" -> state.appendRedacted(delta.path("data").asText(""));
                    case "input_json_delta" -> state.appendArguments(
                        chunk.path("index").asInt(0),
                        delta.path("partial_json").asText("")
                    );
                    default -> {
                        // 无用户可见增量
                    }
                }
            }
            case "content_block_stop" -> state.finishThinkingBlock();
            case "message_delta" -> {
                JsonNode usage = chunk.path("usage");
                if (usage != null && !usage.isNull()) {
                    Usage delta = state.combinedUsage(usage);
                    state.usageDelivered = true;
                    builder.add(new StreamChunk("", delta));
                }
            }
            case "message_stop" -> state.completed = true;
            case "error" -> {
                state.error = chunk.toString();
                builder.add(state.errorChunk());
            }
            default -> {
                // ping 等生命周期事件忽略
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

    /** 取请求级最大输出 token，未设置时回退到配置与默认值。 */
    private int maxTokens(ModelCallRequest request) {
        Object value = request.options().get("maxTokens");
        if (value instanceof Number number) {
            return number.intValue();
        }
        return config.maxTokens(DEFAULT_MAX_TOKENS);
    }

    /** 读取请求选项中的字符串值。 */
    private String option(ModelCallRequest request, String key) {
        Object value = request.options().get(key);
        return value == null ? null : String.valueOf(value);
    }

    /** 若内容数组只有一个 text 块，则简化为纯文本字符串，符合 Anthropic 格式。 */
    private static void finalizeContent(ObjectNode message) {
        JsonNode content = message.get("content");
        if (content != null && content.isArray() && content.size() == 1
            && "text".equals(content.get(0).path("type").asText(""))) {
            message.put("content", content.get(0).path("text").asText(""));
        }
    }

    /**
     * 流式累加器：按内容块 index 记录 thinking 与 tool_use，并在流尾生成完整结果。
     */
    private final class Accumulator {

        /** 流中出现的错误原文。 */
        private String error;
        /** 已累计的用量。 */
        private Usage usage = Usage.ZERO;
        /** 消息是否已完成。 */
        private boolean completed;
        /** 用量是否已通过增量块交付过。 */
        private boolean usageDelivered;
        /** message_start 中的输入用量。 */
        private JsonNode inputUsage;
        /** 正在累积的 thinking 块。 */
        private ObjectNode activeThinkingBlock;
        /** 已完成的 thinking 块列表。 */
        private final List<String> thinkingBlocks = new ArrayList<>();
        /** 内容块 index 到工具调用构建器的映射。 */
        private final Map<Integer, ToolCallBuilder> toolCalls = new LinkedHashMap<>();

        /** 内容块开始时登记 thinking 或 tool_use 的元信息。 */
        private void contentBlockStart(JsonNode block, int index) {
            if (block == null || block.isNull()) {
                return;
            }
            String type = block.path("type").asText("");
            if ("thinking".equals(type) || "redacted_thinking".equals(type)) {
                ObjectNode active = mapper.createObjectNode();
                active.put("type", type);
                String thinking = block.path("thinking").asText(null);
                if (thinking != null) {
                    active.put("thinking", thinking);
                }
                String signature = block.path("signature").asText(null);
                if (signature != null) {
                    active.put("signature", signature);
                }
                String data = block.path("data").asText(null);
                if (data != null) {
                    active.put("data", data);
                }
                activeThinkingBlock = active;
                return;
            }
            if (!"tool_use".equals(type)) {
                return;
            }
            ToolCallBuilder builder = toolCalls.computeIfAbsent(index, ignored -> new ToolCallBuilder());
            String id = block.path("id").asText("");
            if (!id.isEmpty()) {
                builder.id = id;
            }
            String name = block.path("name").asText("");
            if (!name.isEmpty()) {
                builder.name = name;
            }
        }

        /** 追加 thinking 增量文本。 */
        private void appendThinking(String delta) {
            if (activeThinkingBlock == null) {
                return;
            }
            String previous = activeThinkingBlock.path("thinking").asText("");
            activeThinkingBlock.put("thinking", previous + delta);
        }

        /** 追加 thinking 签名。 */
        private void appendSignature(String signature) {
            if (activeThinkingBlock != null && !signature.isEmpty()) {
                activeThinkingBlock.put("signature", signature);
            }
        }

        /** 追加 redacted thinking 数据。 */
        private void appendRedacted(String data) {
            if (activeThinkingBlock != null && !data.isEmpty()) {
                activeThinkingBlock.put("data", data);
            }
        }

        /** 内容块结束时归档当前 thinking 块。 */
        private void finishThinkingBlock() {
            if (activeThinkingBlock == null) {
                return;
            }
            thinkingBlocks.add(activeThinkingBlock.toString());
            activeThinkingBlock = null;
        }

        /** 追加工具参数 JSON 增量。 */
        private void appendArguments(int index, String arguments) {
            if (arguments == null) {
                return;
            }
            ToolCallBuilder builder = toolCalls.computeIfAbsent(index, ignored -> new ToolCallBuilder());
            builder.arguments.append(arguments);
        }

        /** 合并 message_start 的输入用量与 message_delta 的输出用量，返回本块增量。 */
        private Usage combinedUsage(JsonNode outputUsage) {
            ObjectNode combined = mapper.createObjectNode();
            long inputTokens = inputUsage == null ? 0 : inputUsage.path("input_tokens").asLong(0);
            long outputTokens = outputUsage.path("output_tokens").asLong(0);
            long cacheRead = inputUsage == null ? 0 : inputUsage.path("cache_read_input_tokens").asLong(0);
            long cacheCreation = inputUsage == null ? 0 : inputUsage.path("cache_creation_input_tokens").asLong(0);
            combined.put("input_tokens", inputTokens);
            combined.put("output_tokens", outputTokens);
            combined.put("cache_read_input_tokens", cacheRead);
            combined.put("cache_creation_input_tokens", cacheCreation);
            combined.put("total_tokens", inputTokens + outputTokens);
            Usage delta = parseUsage(combined);
            usage = usage.add(delta);
            return delta;
        }

        /** 流结束时输出聚合出的工具调用、thinking 块与未交付的用量。 */
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
                List.copyOf(thinkingBlocks),
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

        /** 单个 tool_use 调用的增量构建器。 */
        private final class ToolCallBuilder {

            /** 工具调用 id。 */
            private String id = "";
            /** 工具名称。 */
            private String name = "";
            /** 参数 JSON 增量片段。 */
            private final StringBuilder arguments = new StringBuilder();

            /** 把累计片段解析为完整 ToolCall。 */
            private ToolCall toToolCall() {
                Map<String, Object> input;
                try {
                    input = mapper.readValue(arguments.toString(), new TypeReference<>() {
                    });
                } catch (Exception exception) {
                    input = Map.of();
                }
                return new ToolCall(id, name, input, id);
            }
        }
    }
}
