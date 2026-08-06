package site.sorghum.loopra.bin.agent.bridge;

import org.noear.snack4.ONode;
import org.noear.solon.ai.chat.content.Contents;
import org.noear.solon.ai.chat.content.ImageBlock;
import org.noear.solon.ai.chat.message.AssistantMessage;
import org.noear.solon.ai.chat.message.ChatMessage;
import org.noear.solon.ai.chat.message.SystemMessage;
import org.noear.solon.ai.chat.message.ToolMessage;
import org.noear.solon.ai.chat.message.UserMessage;
import org.noear.solon.ai.chat.prompt.Prompt;
import org.noear.solon.ai.chat.tool.ToolCall;
import org.noear.solon.ai.chat.tool.ToolResult;
import site.sorghum.loopra.bin.agent.model.LoopraChatMessage;
import site.sorghum.loopra.bin.agent.model.ToolCallEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LoopraChatMessage → solon-ai ChatMessage 转换器。
 * <p>
 * 映射规则：
 * <ul>
 *   <li>system → {@link SystemMessage}</li>
 *   <li>user（含多模态内容段）→ {@link UserMessage}：text 段转 {@link org.noear.solon.ai.chat.content.TextBlock}，
 *       image_url 段转 {@link ImageBlock}（detail 存入 metas，避免污染协议输出）</li>
 *   <li>assistant → {@link AssistantMessage}：{@code reasoning_content} 以 {@code <think>} 标签拼入 content
 *       并设置 {@code reasoningFieldName="reasoning_content"}，使 solon-ai 序列化时输出原生 reasoning 字段；
 *       工具调用同时生成 {@link ToolCall} 列表与 OpenAI 兼容的 toolCallsRaw（solon-ai 方言回传 tool_calls 仅认 raw）</li>
 *   <li>tool → {@link ToolMessage}：文本走单模态 content；{@code tool_image_url} 作为 {@link ImageBlock}
 *       追加到 {@link ToolResult}（多模态 content 数组）</li>
 * </ul>
 * 注意：
 * <ul>
 *   <li>{@code responseReasoning}（Responses API 推理 item 原始 JSON）挂到 {@link AssistantMessage#responseReasoning(String)}，需 solon-ai 4.0.5+。</li>
 *   <li>solon-ai 方言默认过滤纯思考消息（isThinking 且无 tool_calls），仅服务端协议回传，不影响本地消息模型。</li>
 *   <li>{@code timestamp} 由 solon-ai 消息自身的 createdAt 承载，不额外传递。</li>
 * </ul>
 *
 * @author Sorghum
 */
public final class LoopraChatMessageConverter {

    private LoopraChatMessageConverter() {
    }

    /**
     * 单条消息转换。
     *
     * @param msg Loopra 消息，不允许为 null
     * @return solon-ai ChatMessage
     * @throws IllegalArgumentException 未知角色
     */
    public static ChatMessage convert(LoopraChatMessage msg) {
        if (msg.isSystem()) {
            return new SystemMessage(msg.getContent());
        }
        if (msg.isUser()) {
            return toUserMessage(msg);
        }
        if (msg.isAssistant()) {
            return toAssistantMessage(msg);
        }
        if (msg.isTool()) {
            return toToolMessage(msg);
        }
        throw new IllegalArgumentException("Unsupported loopra message role: " + msg.getRole());
    }

    /**
     * 批量转换，null 元素跳过。
     */
    public static List<ChatMessage> convertAll(List<LoopraChatMessage> messages) {
        List<ChatMessage> result = new ArrayList<>(messages.size());
        for (LoopraChatMessage msg : messages) {
            if (msg != null) {
                result.add(convert(msg));
            }
        }
        return result;
    }

    /**
     * 将消息列表转换为 solon-ai 的 {@link Prompt}（内部先经 {@link #convertAll(List)} 转换）。
     */
    public static Prompt convertToPrompt(List<LoopraChatMessage> messages) {
        return Prompt.of(convertAll(messages));
    }

    // ==================== user ====================

    private static UserMessage toUserMessage(LoopraChatMessage msg) {
        List<LoopraChatMessage.ContentPart> parts = msg.getContentParts();
        if (parts == null || parts.isEmpty()) {
            return new UserMessage(new Contents(msg.getContent() == null ? "" : msg.getContent()));
        }
        Contents contents = new Contents();
        for (LoopraChatMessage.ContentPart part : parts) {
            if (part == null) {
                continue;
            }
            if ("text".equals(part.getType())) {
                if (part.getText() != null && !part.getText().isEmpty()) {
                    contents.addText(part.getText());
                }
            } else if ("image_url".equals(part.getType()) && part.getImageUrl() != null) {
                ImageBlock image = ImageBlock.ofUrl(part.getImageUrl().getUrl());
                if (part.getImageUrl().getDetail() != null) {
                    image.metaAdd("detail", part.getImageUrl().getDetail());
                }
                contents.addBlock(image);
            }
        }
        return new UserMessage(contents);
    }

    // ==================== assistant ====================

    private static AssistantMessage toAssistantMessage(LoopraChatMessage msg) {
        String content = msg.getContent() == null ? "" : msg.getContent();
        String reasoning = msg.getReasoningContent();
        boolean hasReasoning = reasoning != null && !reasoning.isEmpty();

        // solon-ai 通过 <think> 标签承载思考：getReasoning() 提取、getResultContent() 剥离
        String combined = content;
        boolean isThinking = false;
        if (hasReasoning) {
            String think = "<think>" + reasoning + "</think>";
            if (combined.isEmpty()) {
                combined = think;
                isThinking = true;
            } else {
                combined = think + combined;
            }
        }

        List<ToolCall> toolCalls = null;
        List<Map> toolCallsRaw = null;
        if (msg.hasToolCalls()) {
            toolCalls = new ArrayList<>(msg.getToolCalls().size());
            toolCallsRaw = new ArrayList<>(msg.getToolCalls().size());
            int index = 0;
            for (ToolCallEntry entry : msg.getToolCalls()) {
                toolCalls.add(toToolCall(index, entry));
                toolCallsRaw.add(toToolCallRaw(entry));
                index++;
            }
        }

        AssistantMessage assistant = new AssistantMessage(combined, isThinking, null, toolCallsRaw, toolCalls, null);
        if (hasReasoning) {
            // 序列化时以 reasoning_content 字段原样回传思考（对齐 LoopraChatMessage 的 JSON 形态）
            assistant.reasoningFieldName("reasoning_content");
        }
        if (msg.getResponseReasoning() != null) {
            // Responses 协议完整推理 item 的原始 JSON（含 encrypted_content 等加密字段），仅保存与回传用
            assistant.responseReasoning(msg.getResponseReasoning());
        }
        return assistant;
    }

    private static ToolCall toToolCall(int index, ToolCallEntry entry) {
        return new ToolCall(String.valueOf(index), entry.id(), entry.name(),
                toArgumentsStr(entry.arguments()), toArgumentsMap(entry.arguments()));
    }

    /**
     * OpenAI 兼容 tool_calls raw（solon-ai 方言回传 tool_calls 仅认 toolCallsRaw）。
     */
    private static Map<String, Object> toToolCallRaw(ToolCallEntry entry) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("id", entry.id());
        raw.put("type", "function");
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", entry.name());
        function.put("arguments", toArgumentsStr(entry.arguments()));
        raw.put("function", function);
        return raw;
    }

    private static String toArgumentsStr(Object arguments) {
        if (arguments == null) {
            return "{}";
        }
        if (arguments instanceof String s) {
            return s.isEmpty() ? "{}" : s;
        }
        return ONode.ofBean(arguments).toJson();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toArgumentsMap(Object arguments) {
        if (arguments instanceof Map) {
            return (Map<String, Object>) arguments;
        }
        if (arguments instanceof String s) {
            try {
                return ONode.ofJson(s).toBean(Map.class);
            } catch (Exception ignored) {
                // 非 JSON 字符串（协议可能输出的中间形态），返回空参数
            }
        }
        return new HashMap<>();
    }

    // ==================== tool ====================

    private static ToolMessage toToolMessage(LoopraChatMessage msg) {
        ToolResult result = new ToolResult(msg.getContent() == null ? "" : msg.getContent());
        if (msg.hasToolImage()) {
            ImageBlock image = ImageBlock.ofUrl(msg.getToolImageUrl());
            if (msg.getToolImageDetail() != null) {
                image.metaAdd("detail", msg.getToolImageDetail());
            }
            result.addBlock(image);
        }
        return new ToolMessage(result, null, msg.getToolCallId(), false);
    }
}
