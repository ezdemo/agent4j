package site.sorghum.loopra.bin.model;

import lombok.extern.slf4j.Slf4j;
import org.noear.snack4.ONode;
import site.sorghum.loopra.bin.agent.model.ChatMessage;
import site.sorghum.loopra.bin.agent.model.ToolCallEntry;

import java.io.IOException;
import java.util.Objects;

/**
 * OpenAI Chat Completions request and response mapping.
 */
@Slf4j
final class ChatCompletionsApiProtocol extends AbstractModelApiProtocol {

    static final String PROTOCOL_NAME = "chat_completions";

    @Override
    public String name() {
        return PROTOCOL_NAME;
    }

    @Override
    public ONode buildRequest(RequestContext context) {
        ONode body = new ONode(ONode.ofJson("{}").options()).asObject();
        String model = ModelContextUtils.stripContextSizeSuffix(context.model());
        body.set(MODEL, model);
        // 快速模式（service_tier=fast），仅 OpenAI 协议生效
        String serviceTier = context.serviceTier();
        if (serviceTier != null && !serviceTier.isBlank()) {
            body.set("service_tier", serviceTier);
        }
        String reasoningEffort = context.reasoningEffort();
        if (reasoningEffort != null && !reasoningEffort.isEmpty()
                && !Objects.equals(reasoningEffort, "none")) {
            body.set("reasoning_effort", reasoningEffort);
            body.set("chat_template_kwargs", ONode.ofJson("{}").set("enable_thinking", true));
            body.set("enable_thinking", true);
            if (model.contains("minimax")) {
                body.set("reasoning_split", true);
                body.set("stream_options", ONode.ofJson("{}").set("include_usage", true));
            }
            if (model.toLowerCase().contains("glm")) {
                body.remove("enable_thinking");
                body.remove("chat_template_kwargs");
            }
        }

        ONode messages = body.getOrNew("messages").asArray();
        for (ChatMessage message : context.messages()) {
            if (message.isTool() && (message.getToolCallId() == null || message.getToolCallId().isEmpty())) {
                log.warn("buildBody: 跳过没有tool_call_id的tool消息");
                continue;
            }
            String messageContent = message.getContent();
            if (message.isTool() && (messageContent == null || messageContent.isEmpty())) {
                messageContent = "ERROR 工具执行失败或者工具执行结果为空";
            }

            ONode node = new ONode();
            node.set("role", message.getRole());
            boolean skip = false;
            boolean assistant = message.isAssistant();
            boolean user = message.isUser();
            boolean hasToolCalls = message.hasToolCalls();
            boolean hasContent = messageContent != null && !messageContent.isEmpty();
            boolean hasParts = message.getContentParts() != null && !message.getContentParts().isEmpty();
            boolean hasReasoning = message.getReasoningContent() != null
                    && !message.getReasoningContent().isEmpty();
            if (user && !hasContent && !hasReasoning && !hasToolCalls && !hasParts) continue;

            if (hasParts) {
                ONode content = node.getOrNew(CONTENT).asArray();
                for (ChatMessage.ContentPart part : message.getContentParts()) {
                    ONode partNode = content.addNew();
                    partNode.set(TYPE, part.getType());
                    if ("text".equals(part.getType())) {
                        partNode.set("text", part.getText() != null ? part.getText() : "");
                    } else if ("image_url".equals(part.getType())) {
                        ChatMessage.ContentPart.ImageUrl image = part.getImageUrl();
                        if (image != null) {
                            ONode imageNode = partNode.getOrNew("image_url");
                            imageNode.set("url", image.getUrl() != null ? image.getUrl() : "");
                            if (image.getDetail() != null) imageNode.set("detail", image.getDetail());
                        }
                    }
                }
            } else if (assistant && !hasContent && !hasToolCalls && !hasReasoning) {
                log.warn("buildBody: 检测到空 assistant 消息（无 content 且无 tool_calls），强制删除");
                node.set(CONTENT, "");
                skip = true;
            } else if (hasContent) {
                node.set(CONTENT, messageContent);
            }

            if (hasToolCalls) {
                ONode toolCalls = node.getOrNew("tool_calls").asArray();
                for (ToolCallEntry toolCall : message.getToolCalls()) {
                    ONode toolCallNode = toolCalls.addNew();
                    toolCallNode.set(ID, toolCall.id());
                    toolCallNode.set(TYPE, FUNCTION);
                    ONode function = toolCallNode.getOrNew(FUNCTION);
                    function.set(NAME, toolCall.name());
                    Object arguments = toolCall.arguments();
                    function.set(ARGUMENTS, arguments instanceof String value
                            ? value : arguments == null ? "{}" : ONode.serialize(arguments));
                }
            }
            if (message.getReasoningContent() != null) {
                node.set(REASONING_CONTENT, message.getReasoningContent());
            }
            if (message.getToolCallId() != null) node.set("tool_call_id", message.getToolCallId());
            if (node.get(CONTENT).isNull()) node.set(CONTENT, "");
            if (!skip) {
                messages.add(node);
                if (message.isTool() && message.hasToolImage()) {
                    ONode imageMessage = messages.addNew().asObject();
                    imageMessage.set("role", "user");
                    ONode imageContent = imageMessage.getOrNew(CONTENT).asArray();
                    imageContent.addNew().set(TYPE, "text").set("text",
                            "The preceding read_image tool result includes the image below. Inspect it and continue the task.");
                    ONode imagePart = imageContent.addNew().asObject();
                    imagePart.set(TYPE, "image_url");
                    ONode imageNode = imagePart.getOrNew("image_url").asObject();
                    imageNode.set("url", message.getToolImageUrl());
                    if (message.getToolImageDetail() != null) {
                        imageNode.set("detail", message.getToolImageDetail());
                    }
                }
            }
        }

        if (context.tools() != null && !context.tools().isEmpty()) body.set(TOOLS, context.tools());
        if (context.userId() != null && !context.userId().isEmpty()) body.set("user", context.userId());
        if (context.sessionId() != null && !context.sessionId().isEmpty()) {
            body.set("prompt_cache_key", context.sessionId());
        }
        return body;
    }

    @Override
    public ONode parseResponse(ONode response, String responseText) throws IOException {
        ONode choices = response.get("choices");
        if (choices == null || !choices.isArray() || choices.getArray().isEmpty()) {
            throw new IOException("No choices in response: " + responseText);
        }
        return choices.get(0).get("message");
    }

    @Override
    public void processStreamChunk(ONode chunk, ModelClient.StreamCallback callback,
                                   ModelApiStreamState state) {
        ONode usage = chunk.get(USAGE);
        if (usage != null && !usage.isNull()) {
            handleUsage(usage, chunk.get(ID).getString(), callback, state.lastUsage);
        }

        ONode delta = chunk.select("$.choices[0].delta");
        if (delta == null || delta.isNull()) return;
        ONode reasoning = delta.get(REASONING_CONTENT).isNull()
                ? delta.get("reasoning") : delta.get(REASONING_CONTENT);
        delta.set(REASONING_CONTENT, reasoning);
        if (reasoning != null && reasoning.isString()) {
            String token = reasoning.getString();
            if (token != null && !token.isEmpty()) {
                log.debug("收到reasoning_content: {}", token);
                state.emittedOutput = true;
                safeCallback("onReasoningDelta", () -> callback.onReasoningDelta(token));
            }
        }

        ONode content = delta.get(CONTENT);
        if (content != null && content.isString()) {
            String token = content.getString();
            if (token != null && !token.isEmpty()) {
                log.debug("收到content: {}", token);
                state.emittedOutput = true;
                processContentToken(token, callback, state);
            }
        }

        ONode toolCalls = delta.get("tool_calls");
        if (toolCalls != null && toolCalls.isArray()) {
            log.debug("收到tool_calls数据，数量: {}", toolCalls.getArray().size());
            accumulateToolCalls(toolCalls, state);
        }
    }

    private void accumulateToolCalls(ONode deltas, ModelApiStreamState state) {
        for (ONode delta : deltas.getArray()) {
            if (state.toolCalls == null) state.toolCalls = ONode.ofJson("[]").asArray();
            int index = delta.get("index").isNull() ? 0 : delta.get("index").getInt();
            ONode function = delta.get(FUNCTION);
            while (state.toolCalls.getArray().size() <= index) {
                state.toolCalls.addNew().set(TYPE, FUNCTION);
            }
            ONode existing = state.toolCalls.get(index);
            if (function == null || function.isNull()) continue;
            if (existing.get(ID).isNull()) existing.set(ID, delta.get(ID).getString());
            if (existing.select("$.function.name").isNull()) {
                existing.getOrNew(FUNCTION).set(NAME, function.get(NAME).getString());
            }
            if (!function.get(ARGUMENTS).isNull()) {
                String previous = existing.getOrNew(FUNCTION).get(ARGUMENTS).getString();
                String addition = function.get(ARGUMENTS).getString();
                existing.getOrNew(FUNCTION).set(ARGUMENTS,
                        (previous != null ? previous : "") + (addition != null ? addition : ""));
            }
            log.debug("tool_calls索引: {}, 函数名: {}", index,
                    function.get(NAME).isNull() ? "null" : function.get(NAME).getString());
        }
    }
}
