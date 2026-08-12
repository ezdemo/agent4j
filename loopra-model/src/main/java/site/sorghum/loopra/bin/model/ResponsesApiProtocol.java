package site.sorghum.loopra.bin.model;

import lombok.extern.slf4j.Slf4j;
import org.noear.snack4.ONode;
import site.sorghum.loopra.bin.agent.model.ChatMessage;
import site.sorghum.loopra.bin.agent.model.ToolCallEntry;

import java.io.IOException;
import java.util.Objects;

/**
 * OpenAI Responses API request and response mapping.
 */
@Slf4j
final class ResponsesApiProtocol extends AbstractModelApiProtocol {

    static final String PROTOCOL_NAME = "responses";

    @Override
    public String name() {
        return PROTOCOL_NAME;
    }

    @Override
    public ONode buildRequest(RequestContext context) {
        ONode body = ONode.ofJson("{}");
        body.set(MODEL, ModelContextUtils.stripContextSizeSuffix(context.model()));
        // 快速模式（service_tier=fast），仅 OpenAI 协议生效
        String serviceTier = context.serviceTier();
        if (serviceTier != null && !serviceTier.isBlank()) {
            body.set("service_tier", serviceTier);
        }
        String reasoningEffort = context.reasoningEffort();
        if (reasoningEffort != null && !reasoningEffort.isEmpty() && !"none".equals(reasoningEffort)) {
            ONode reasoning = body.getOrNew("reasoning");
            reasoning.set("effort", reasoningEffort);
            reasoning.set("summary", "auto");
            body.getOrNew("include").asArray().add("reasoning.encrypted_content");
        }

        ONode input = body.getOrNew("input").asArray();
        for (ChatMessage message : context.messages()) {
            if (message.isTool()) {
                if (message.getToolCallId() == null || message.getToolCallId().isEmpty()) continue;
                ONode item = input.addNew();
                item.set(TYPE, "function_call_output");
                item.set("call_id", message.getToolCallId());
                String outputText = message.getContent() == null || message.getContent().isEmpty()
                        ? "ERROR 工具执行失败或者工具执行结果为空" : message.getContent();
                if (message.hasToolImage()) {
                    ONode output = item.getOrNew("output").asArray();
                    output.addNew().set(TYPE, "input_text").set("text", outputText);
                    ONode imagePart = output.addNew().asObject();
                    imagePart.set(TYPE, "input_image");
                    imagePart.set("image_url", message.getToolImageUrl());
                    if (message.getToolImageDetail() != null) {
                        imagePart.set("detail", message.getToolImageDetail());
                    }
                } else {
                    item.set("output", outputText);
                }
                continue;
            }

            String responseReasoning = message.getResponseReasoning();
            if (responseReasoning == null && message.hasToolCalls()) {
                responseReasoning = message.getToolCalls().stream()
                        .map(ToolCallEntry::responseReasoning)
                        .filter(Objects::nonNull)
                        .findFirst().orElse(null);
            }
            if (responseReasoning != null) {
                ONode reasoningItem = ONode.ofJson(responseReasoning);
                // `status` is output-only and rejected when the item is replayed through input.
                reasoningItem.remove("status");
                input.add(reasoningItem);
            }

            boolean hasParts = message.getContentParts() != null && !message.getContentParts().isEmpty();
            if (message.hasContent() || hasParts) {
                ONode item = input.addNew();
                item.set("role", message.getRole());
                if (hasParts) {
                    ONode content = item.getOrNew(CONTENT).asArray();
                    for (ChatMessage.ContentPart part : message.getContentParts()) {
                        ONode partNode = content.addNew();
                        if ("text".equals(part.getType())) {
                            partNode.set(TYPE, "input_text");
                            partNode.set("text", part.getText() == null ? "" : part.getText());
                        } else if ("image_url".equals(part.getType()) && part.getImageUrl() != null) {
                            partNode.set(TYPE, "input_image");
                            partNode.set("image_url", part.getImageUrl().getUrl());
                            if (part.getImageUrl().getDetail() != null) {
                                partNode.set("detail", part.getImageUrl().getDetail());
                            }
                        }
                    }
                } else {
                    item.set(CONTENT, message.getContent());
                }
            }

            if (message.hasToolCalls()) {
                for (ToolCallEntry toolCall : message.getToolCalls()) {
                    ONode item = input.addNew();
                    item.set(TYPE, "function_call");
                    item.set(ID, toolCall.id());
                    item.set("call_id", toolCall.id());
                    item.set(NAME, toolCall.name());
                    Object arguments = toolCall.arguments();
                    boolean isString = arguments instanceof String;
                    if (isString) {
                        item.set(ARGUMENTS, arguments);
                    } else {
                        item.set(ARGUMENTS, ONode.serialize(arguments));
                    }
                }
            }
        }

        if (context.tools() != null && !context.tools().isEmpty()) {
            ONode responseTools = body.getOrNew(TOOLS).asArray();
            for (ONode tool : context.tools().getArray()) {
                ONode function = tool.get(FUNCTION);
                if (function == null || function.isNull()) continue;
                ONode responseTool = responseTools.addNew();
                responseTool.set(TYPE, FUNCTION);
                responseTool.set(NAME, function.get(NAME));
                if (!function.get("description").isNull()) {
                    responseTool.set("description", function.get("description"));
                }
                if (!function.get("parameters").isNull()) {
                    responseTool.set("parameters", function.get("parameters"));
                }
                if (!function.get("strict").isNull()) responseTool.set("strict", function.get("strict"));
            }
        }

        if (context.userId() != null && !context.userId().isEmpty()) body.set("user", context.userId());
        if (context.sessionId() != null && !context.sessionId().isEmpty()) {
            body.set("prompt_cache_key", context.sessionId());
        }
        return body;
    }

    @Override
    public ONode parseResponse(ONode response, String responseText) throws IOException {
        ONode output = response.get("output");
        if (output == null || !output.isArray()) {
            throw new IOException("No output in response: " + responseText);
        }

        ONode message = ONode.ofJson("{}");
        message.set("role", "assistant");
        StringBuilder content = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        ONode toolCalls = ONode.ofJson("[]").asArray();
        ONode reasoningItem = null;
        for (ONode item : output.getArray()) {
            if ("reasoning".equals(item.get(TYPE).getString())) {
                reasoningItem = item;
                ONode summary = item.get("summary");
                if (summary != null && summary.isArray()) {
                    for (ONode part : summary.getArray()) {
                        String text = part.get("text").getString();
                        if (text != null) reasoning.append(text);
                    }
                }
            }
        }
        for (ONode item : output.getArray()) {
            String type = item.get(TYPE).getString();
            if ("message".equals(type)) {
                ONode parts = item.get(CONTENT);
                if (parts != null && parts.isArray()) {
                    for (ONode part : parts.getArray()) {
                        String partType = part.get(TYPE).getString();
                        String text = "refusal".equals(partType)
                                ? part.get("refusal").getString() : part.get("text").getString();
                        if (text != null && ("output_text".equals(partType) || "refusal".equals(partType))) {
                            content.append(text);
                        }
                    }
                }
            } else if ("function_call".equals(type)) {
                ONode call = toolCalls.addNew();
                String callId = item.get("call_id").getString();
                if (callId == null || callId.isEmpty()) callId = item.get(ID).getString();
                call.set(ID, callId == null ? "" : callId);
                call.set(TYPE, FUNCTION);
                ONode function = call.getOrNew(FUNCTION);
                function.set(NAME, item.get(NAME).getString());
                function.set(ARGUMENTS, item.get(ARGUMENTS).getString());
            }
        }
        message.set(CONTENT, content.toString());
        if (reasoningItem != null) message.set("response_reasoning", reasoningItem.toJson());
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
            case "response.output_text.delta", "response.refusal.delta" -> {
                String delta = chunk.get("delta").getString();
                if (delta != null && !delta.isEmpty()) {
                    state.emittedOutput = true;
                    processContentToken(delta, callback, state);
                }
            }
            case "response.reasoning_summary_text.delta", "response.reasoning_text.delta" -> {
                String delta = chunk.get("delta").getString();
                if (delta != null && !delta.isEmpty()) {
                    state.emittedOutput = true;
                    safeCallback("onReasoningDelta", () -> callback.onReasoningDelta(delta));
                }
            }
            case "response.output_item.added" -> accumulateOutputItem(
                    chunk.get("item"), chunk.get("output_index").getInt(), state, false);
            case "response.output_item.done" -> accumulateOutputItem(
                    chunk.get("item"), chunk.get("output_index").getInt(), state, true);
            case "response.function_call_arguments.delta" -> accumulateFunctionArguments(
                    chunk.get("output_index").getInt(), chunk.get("delta").getString(), state, false);
            case "response.function_call_arguments.done" -> accumulateFunctionArguments(
                    chunk.get("output_index").getInt(), chunk.get(ARGUMENTS).getString(), state, true);
            case "response.completed" -> {
                state.completed = true;
                ONode response = chunk.get("response");
                ONode usage = response.get(USAGE);
                if (usage != null && !usage.isNull()) {
                    handleUsage(usage, response.get(ID).getString(), callback, state.lastUsage);
                }
            }
            case "response.failed", "response.incomplete" -> {
                state.errorData = chunk.toJson();
                String errorCode = chunk.select("$.response.error.code").getString();
                state.contextLengthExceeded = ModelApiError.isContextLengthExceeded(state.errorData);
                state.invalidRequestError = ModelApiError.isInvalidRequestError(state.errorData);
                state.retryableError = state.contextLengthExceeded
                        || Objects.equals(errorCode, "rate_limit_exceeded")
                        || Objects.equals(errorCode, "upstream_error")
                        || Objects.equals(errorCode, "server_error")
                        || Objects.equals(errorCode, "server_is_overloaded");
                log.warn("收到 Responses API 终止错误: {}", state.errorData);
            }
            case "error" -> {
                state.errorData = chunk.toJson();
                state.contextLengthExceeded = ModelApiError.isContextLengthExceeded(state.errorData);
                state.invalidRequestError = ModelApiError.isInvalidRequestError(state.errorData);
                state.retryableError = true;
                log.warn("收到 Responses API 流错误: {}", state.errorData);
            }
            default -> {
                // Lifecycle events do not contain user-visible deltas.
            }
        }
    }

    @Override
    public String streamCompletionError(ModelApiStreamState state) {
        return state.completed ? null : "Responses API stream ended before response.completed";
    }

    private void accumulateOutputItem(ONode item, int index, ModelApiStreamState state,
                                      boolean replaceArguments) {
        if (item == null || item.isNull()) return;
        if ("reasoning".equals(item.get(TYPE).getString())) {
            if (replaceArguments) state.responseReasoning = item.toJson();
            return;
        }
        if (!"function_call".equals(item.get(TYPE).getString())) return;
        ONode call = responseToolCall(index, state);
        String callId = item.get("call_id").getString();
        if (callId == null || callId.isEmpty()) callId = item.get(ID).getString();
        if (callId != null && !callId.isEmpty()) call.set(ID, callId);
        String name = item.get(NAME).getString();
        if (name != null && !name.isEmpty()) call.getOrNew(FUNCTION).set(NAME, name);
        String arguments = item.get(ARGUMENTS).getString();
        if (arguments != null) accumulateFunctionArguments(index, arguments, state, replaceArguments);
    }

    private void accumulateFunctionArguments(int index, String arguments, ModelApiStreamState state,
                                             boolean replace) {
        if (arguments == null) return;
        ONode function = responseToolCall(index, state).getOrNew(FUNCTION);
        String previous = function.get(ARGUMENTS).getString();
        function.set(ARGUMENTS, replace ? arguments : (previous == null ? "" : previous) + arguments);
    }

    private ONode responseToolCall(int index, ModelApiStreamState state) {
        if (state.toolCalls == null) state.toolCalls = ONode.ofJson("[]").asArray();
        while (state.toolCalls.size() <= index) {
            ONode call = state.toolCalls.addNew();
            call.set(TYPE, FUNCTION);
            call.getOrNew(FUNCTION).set(ARGUMENTS, "");
        }
        return state.toolCalls.get(index);
    }
}
