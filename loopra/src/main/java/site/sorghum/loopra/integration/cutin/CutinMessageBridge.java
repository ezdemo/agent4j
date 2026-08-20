package site.sorghum.loopra.integration.cutin;

import site.sorghum.cutin.core.context.Message;
import site.sorghum.cutin.core.tool.ToolCall;
import site.sorghum.loopra.bin.agent.model.ChatMessage;
import site.sorghum.loopra.bin.agent.model.ToolCallEntry;
import site.sorghum.loopra.bin.agent.model.UserMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
  * Loopra 公共 {@link ChatMessage} 模型与 cutin 不可变 {@link Message} 模型之间的消息转换。
 */
public final class CutinMessageBridge {

    private CutinMessageBridge() {
    }

    public static List<Message> toCutin(List<ChatMessage> messages) {
        List<Message> result = new ArrayList<>(messages.size());
        for (ChatMessage message : messages) {
            result.add(toCutin(message));
        }
        return result;
    }

    public static Message toCutin(ChatMessage message) {
        if (message.isTool()) {
            return new Message(
                "tool",
                message.getContent(),
                message.getToolCallId(),
                List.of()
            );
        }
        if (message.isAssistant()) {
            Map<String, Object> metadata = new HashMap<>();
            if (message.getReasoningContent() != null) {
                metadata.put("reasoning_content", message.getReasoningContent());
            }
            if (message.getThinkingBlocks() != null && !message.getThinkingBlocks().isEmpty()) {
                metadata.put("thinking_blocks", List.copyOf(message.getThinkingBlocks()));
            }
            if (message.getResponseReasoning() != null) {
                metadata.put("response_reasoning", message.getResponseReasoning());
            }
            return new Message(
                "assistant",
                message.getContent(),
                null,
                toCutinToolCalls(message.getToolCalls()),
                metadata
            );
        }
        return new Message(
            message.getRole(),
            message.getContent(),
            null,
            List.of()
        );
    }

    /** 把回合级用户消息转换为 Cutin 消息，并保留图片元数据。 */
    public static Message toCutin(UserMessage message) {
        Map<String, Object> metadata = new HashMap<>();
        if (message != null && message.hasImages()) {
            metadata.put("images", List.copyOf(message.getImages()));
        }
        return new Message(
            "user",
            message == null ? null : message.getText(),
            null,
            List.of(),
            metadata
        );
    }

    public static List<ChatMessage> toLoopra(List<Message> messages) {
        List<ChatMessage> result = new ArrayList<>(messages.size());
        for (Message message : messages) {
            switch (message.role()) {
                case "system" -> result.add(ChatMessage.ofSystem(message.content()));
                case "user" -> result.add(toLoopraUser(message));
                case "assistant" -> result.add(toLoopraAssistant(message));
                case "tool" -> result.add(ChatMessage.tool(
                    message.toolCallId(),
                    message.content()
                ));
                default -> result.add(ChatMessage.ofUser(message.content()));
            }
        }
        return result;
    }

    private static ChatMessage toLoopraUser(Message message) {
        Object images = message.metadata("images");
        if (images instanceof List<?> list && !list.isEmpty()) {
            List<String> urls = list.stream().map(String::valueOf).toList();
            return ChatMessage.ofUser(message.content(), urls);
        }
        return ChatMessage.ofUser(message.content());
    }

    private static ChatMessage toLoopraAssistant(Message message) {
        ChatMessage assistant = ChatMessage.assistant(
            message.content(),
            toLoopraToolCalls(message.toolCalls()),
            metadataString(message, "reasoning_content")
        );
        Object blocks = message.metadata("thinking_blocks");
        if (blocks instanceof List<?> list && !list.isEmpty()) {
            java.util.List<String> strings = new ArrayList<>();
            for (Object block : list) {
                strings.add(block == null ? "" : String.valueOf(block));
            }
            assistant.setThinkingBlocks(strings);
        }
        Object responseReasoning = message.metadata("response_reasoning");
        if (responseReasoning != null) {
            assistant.setResponseReasoning(String.valueOf(responseReasoning));
        }
        return assistant;
    }

    private static String metadataString(Message message, String key) {
        Object value = message.metadata(key);
        return value == null ? null : String.valueOf(value);
    }

    private static List<ToolCall> toCutinToolCalls(List<ToolCallEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        List<ToolCall> result = new ArrayList<>(entries.size());
        for (ToolCallEntry entry : entries) {
            result.add(new ToolCall(
                entry.id(),
                entry.name(),
                parseArguments(entry.arguments()),
                entry.id()
            ));
        }
        return result;
    }

    private static List<ToolCallEntry> toLoopraToolCalls(List<ToolCall> calls) {
        if (calls == null || calls.isEmpty()) {
            return List.of();
        }
        List<ToolCallEntry> result = new ArrayList<>(calls.size());
        for (ToolCall call : calls) {
            result.add(new ToolCallEntry(
                call.id(),
                call.toolId(),
                call.arguments()
            ));
        }
        return result;
    }

    private static Map<String, Object> parseArguments(Object arguments) {
        if (arguments == null) {
            return Map.of();
        }
        if (arguments instanceof Map<?, ?> map) {
            Map<String, Object> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        if (arguments instanceof String json && !json.isBlank()) {
            try {
                Object bean = org.noear.snack4.ONode.ofJson(json).toBean(Map.class);
                if (bean instanceof Map<?, ?> map) {
                    Map<String, Object> result = new HashMap<>();
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        result.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    return result;
                }
            } catch (RuntimeException ignored) {
                 // 落入空参数分支
            }
        }
        return Map.of();
    }
}
