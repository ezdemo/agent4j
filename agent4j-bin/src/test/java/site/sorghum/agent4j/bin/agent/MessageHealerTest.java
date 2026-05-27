package site.sorghum.agent4j.bin.agent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MessageHealer 单元测试 —— 消息修复器。
 */
class MessageHealerTest {

    private static Map<String, Object> msg(String role, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    private static Map<String, Object> toolMsg(String toolCallId, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", "tool");
        m.put("tool_call_id", toolCallId);
        m.put("content", content);
        return m;
    }

    private static Map<String, Object> assistantWithToolCalls(String content, List<Map<String, Object>> toolCalls) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", "assistant");
        if (content != null) m.put("content", content);
        m.put("tool_calls", toolCalls);
        return m;
    }

    private static Map<String, Object> toolCall(String id, String name) {
        Map<String, Object> tc = new LinkedHashMap<>();
        tc.put("id", id);
        tc.put("type", "function");
        Map<String, Object> fn = new LinkedHashMap<>();
        fn.put("name", name);
        fn.put("arguments", "{}");
        tc.put("function", fn);
        return tc;
    }

    @Test
    void emptyMessagesReturnsEmpty() {
        List<Map<String, Object>> result = MessageHealer.heal(new ArrayList<Map<String, Object>>(), false);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void normalMessagesUnchanged() {
        List<Map<String, Object>> msgs = new ArrayList<>();
        msgs.add(msg("system", "hello"));
        msgs.add(msg("user", "hi"));
        msgs.add(msg("assistant", "hello!"));

        List<Map<String, Object>> result = MessageHealer.heal(msgs, false);
        assertEquals(3, result.size());
        assertEquals("system", result.get(0).get("role"));
        assertEquals("user", result.get(1).get("role"));
        assertEquals("assistant", result.get(2).get("role"));
    }

    @Test
    void orphanToolMessageRemoved() {
        List<Map<String, Object>> msgs = new ArrayList<>();
        msgs.add(msg("user", "hi"));
        // 孤立的 tool 消息（没有对应的 assistant tool_calls）
        msgs.add(toolMsg("tc_1", "result"));
        msgs.add(msg("assistant", "ok"));

        List<Map<String, Object>> result = MessageHealer.heal(msgs, false);
        assertEquals(2, result.size());
        assertEquals("user", result.get(0).get("role"));
        assertEquals("assistant", result.get(1).get("role"));
    }

    @Test
    void pairedToolMessagesPreserved() {
        List<Map<String, Object>> msgs = new ArrayList<>();
        msgs.add(msg("user", "hi"));

        List<Map<String, Object>> tcs = new ArrayList<>();
        tcs.add(toolCall("tc_1", "read_file"));
        msgs.add(assistantWithToolCalls(null, tcs));
        msgs.add(toolMsg("tc_1", "file content"));

        msgs.add(msg("assistant", "done"));

        List<Map<String, Object>> result = MessageHealer.heal(msgs, false);
        assertEquals(4, result.size());
        assertEquals("tool", result.get(2).get("role"));
    }

    @Test
    void missingToolCallIdHealed() {
        List<Map<String, Object>> msgs = new ArrayList<>();
        msgs.add(msg("user", "hi"));

        List<Map<String, Object>> tcs = new ArrayList<>();
        tcs.add(toolCall("tc_1", "read_file"));
        msgs.add(assistantWithToolCalls(null, tcs));

        // tool 消息缺少 tool_call_id
        Map<String, Object> toolMsg = new LinkedHashMap<>();
        toolMsg.put("role", "tool");
        toolMsg.put("content", "result");
        msgs.add(toolMsg);

        List<Map<String, Object>> result = MessageHealer.heal(msgs, false);
        assertEquals(3, result.size());
        // tool 消息应被补上 tool_call_id
        assertNotNull(result.get(2).get("tool_call_id"));
    }

    @Test
    void thinkingModeAddsEmptyReasoningContent() {
        List<Map<String, Object>> msgs = new ArrayList<>();
        msgs.add(msg("user", "hi"));

        Map<String, Object> assistant = new LinkedHashMap<>();
        assistant.put("role", "assistant");
        assistant.put("content", "hello");
        msgs.add(assistant);

        List<Map<String, Object>> result = MessageHealer.heal(msgs, true);
        assertEquals(2, result.size());
        assertTrue(result.get(1).containsKey("reasoning_content"));
        assertEquals("", result.get(1).get("reasoning_content"));
    }

    @Test
    void thinkingModePreservesExistingReasoning() {
        List<Map<String, Object>> msgs = new ArrayList<>();
        msgs.add(msg("user", "hi"));

        Map<String, Object> assistant = new LinkedHashMap<>();
        assistant.put("role", "assistant");
        assistant.put("content", "hello");
        assistant.put("reasoning_content", "thinking...");
        msgs.add(assistant);

        List<Map<String, Object>> result = MessageHealer.heal(msgs, true);
        assertEquals("thinking...", result.get(1).get("reasoning_content"));
    }

    @Test
    void unpairedToolCallsStrippedFromLastAssistant() {
        List<Map<String, Object>> msgs = new ArrayList<>();
        msgs.add(msg("user", "hi"));

        // assistant 有 2 个 tool_calls，但只有 1 个 tool result
        List<Map<String, Object>> tcs = new ArrayList<>();
        tcs.add(toolCall("tc_1", "read_file"));
        tcs.add(toolCall("tc_2", "grep"));
        msgs.add(assistantWithToolCalls(null, tcs));
        msgs.add(toolMsg("tc_1", "result1"));
        // tc_2 的结果缺失

        List<Map<String, Object>> result = MessageHealer.heal(msgs, false);
        assertEquals(2, result.size());
        // assistant 的 tool_calls 应被剥离
        Map<String, Object> assistant = result.get(1);
        assertEquals("assistant", assistant.get("role"));
        assertFalse(assistant.containsKey("tool_calls"), "未配对的 tool_calls 应被剥离");
    }

    @Test
    void oversizedToolResultTruncated() {
        List<Map<String, Object>> msgs = new ArrayList<>();
        msgs.add(msg("user", "hi"));

        List<Map<String, Object>> tcs = new ArrayList<>();
        tcs.add(toolCall("tc_1", "read_file"));
        msgs.add(assistantWithToolCalls(null, tcs));

        // 创建一个超大 tool 结果（> 8000 tokens ≈ > 16000 字符）
        StringBuilder bigContent = new StringBuilder();
        for (int i = 0; i < 20000; i++) {
            bigContent.append("x");
        }
        msgs.add(toolMsg("tc_1", bigContent.toString()));

        List<Map<String, Object>> result = MessageHealer.heal(msgs, false);
        assertEquals(3, result.size());
        String content = (String) result.get(2).get("content");
        assertTrue(content.contains("truncated"), "超大结果应被截断");
        assertTrue(content.length() < bigContent.length(), "截断后应更短");
    }
}
