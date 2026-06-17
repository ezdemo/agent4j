package site.sorghum.agent4j.bin.agent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MessageHealer 单元测试 —— 消息修复器。
 */
class MessageHealerTest {

    private static ChatMessage msg(String role, String content) {
        return switch (role) {
            case "system" -> ChatMessage.ofSystem(content);
            case "user" -> ChatMessage.ofUser(content);
            case "assistant" -> ChatMessage.assistant(content, null, null);
            default -> ChatMessage.ofUser(content);
        };
    }

    private static ChatMessage toolMsg(String toolCallId, String content) {
        return ChatMessage.tool(toolCallId, content);
    }

    private static ChatMessage assistantWithToolCalls(String content, List<ToolCallEntry> toolCalls) {
        return ChatMessage.assistant(content, toolCalls, null);
    }

    private static ToolCallEntry toolCall(String id, String name) {
        return new ToolCallEntry(id, name, "{}");
    }

    @Test
    void emptyMessagesReturnsEmpty() {
        var hr = MessageHealer.heal(new ArrayList<>(), false);
        List<ChatMessage> result = hr.messages();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void normalMessagesUnchanged() {
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(msg("system", "hello"));
        msgs.add(msg("user", "hi"));
        msgs.add(msg("assistant", "hello!"));

        var hr = MessageHealer.heal(msgs, false);
        List<ChatMessage> result = hr.messages();
        assertEquals(3, result.size());
        assertEquals("system", result.get(0).getRole());
        assertEquals("user", result.get(1).getRole());
        assertEquals("assistant", result.get(2).getRole());
    }

    @Test
    void orphanToolMessageRemoved() {
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(msg("user", "hi"));
        // 孤立的 tool 消息（没有对应的 assistant tool_calls）
        msgs.add(toolMsg("tc_1", "result"));
        msgs.add(msg("assistant", "ok"));

        var hr = MessageHealer.heal(msgs, false);
        List<ChatMessage> result = hr.messages();
        assertEquals(2, result.size());
        assertEquals("user", result.get(0).getRole());
        assertEquals("assistant", result.get(1).getRole());
    }

    @Test
    void pairedToolMessagesPreserved() {
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(msg("user", "hi"));

        List<ToolCallEntry> tcs = new ArrayList<>();
        tcs.add(toolCall("tc_1", "read_file"));
        msgs.add(assistantWithToolCalls(null, tcs));
        msgs.add(toolMsg("tc_1", "file content"));

        msgs.add(msg("assistant", "done"));

        var hr = MessageHealer.heal(msgs, false);
        List<ChatMessage> result = hr.messages();
        assertEquals(4, result.size());
        assertEquals("tool", result.get(2).getRole());
    }

    @Test
    void missingToolCallIdHealed() {
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(msg("user", "hi"));

        List<ToolCallEntry> tcs = new ArrayList<>();
        tcs.add(toolCall("tc_1", "read_file"));
        msgs.add(assistantWithToolCalls(null, tcs));

        // tool 消息缺少 tool_call_id
        ChatMessage toolMsg = ChatMessage.tool(null, "result");
        msgs.add(toolMsg);

        var hr = MessageHealer.heal(msgs, false);
        List<ChatMessage> result = hr.messages();
        assertEquals(3, result.size());
        // tool 消息应被补上 tool_call_id
        assertNotNull(result.get(2).getToolCallId());
    }

    @Test
    void thinkingModeAddsEmptyReasoningContent() {
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(msg("user", "hi"));

        ChatMessage assistant = ChatMessage.assistant("hello", null, null);
        msgs.add(assistant);

        var hr = MessageHealer.heal(msgs, true);
        List<ChatMessage> result = hr.messages();
        assertEquals(2, result.size());
        assertEquals("", result.get(1).getReasoningContent());
    }

    @Test
    void thinkingModePreservesExistingReasoning() {
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(msg("user", "hi"));

        ChatMessage assistant = ChatMessage.assistant("hello", null, "thinking...");
        msgs.add(assistant);

        var hr = MessageHealer.heal(msgs, true);
        List<ChatMessage> result = hr.messages();
        assertEquals("thinking...", result.get(1).getReasoningContent());
    }

    @Test
    void unpairedToolCallsStrippedFromLastAssistant() {
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(msg("user", "hi"));

        // assistant 有 2 个 tool_calls，但只有 1 个 tool result
        List<ToolCallEntry> tcs = new ArrayList<>();
        tcs.add(toolCall("tc_1", "read_file"));
        tcs.add(toolCall("tc_2", "grep"));
        msgs.add(assistantWithToolCalls(null, tcs));
        msgs.add(toolMsg("tc_1", "result1"));
        // tc_2 的结果缺失

        var hr = MessageHealer.heal(msgs, false);
        List<ChatMessage> result = hr.messages();
        assertEquals(2, result.size());
        // assistant 的 tool_calls 应被剥离
        ChatMessage assistant = result.get(1);
        assertEquals("assistant", assistant.getRole());
        assertFalse(assistant.hasToolCalls(), "未配对的 tool_calls 应被剥离");
    }

    @Test
    void oversizedToolResultTruncated() {
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(msg("user", "hi"));

        List<ToolCallEntry> tcs = new ArrayList<>();
        tcs.add(toolCall("tc_1", "read_file"));
        msgs.add(assistantWithToolCalls(null, tcs));

        // 创建一个超大 tool 结果（> 8000 tokens ≈ > 16000 字符）
        StringBuilder bigContent = new StringBuilder();
        bigContent.append("x".repeat(20000));
        msgs.add(toolMsg("tc_1", bigContent.toString()));

        var hr = MessageHealer.heal(msgs, false);
        List<ChatMessage> result = hr.messages();
        assertEquals(3, result.size());
        String content = result.get(2).getContent();
        assertTrue(content.contains("truncated"), "超大结果应被截断");
        assertTrue(content.length() < bigContent.length(), "截断后应更短");
    }
}
