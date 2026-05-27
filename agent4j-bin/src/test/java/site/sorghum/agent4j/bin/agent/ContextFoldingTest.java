package site.sorghum.agent4j.bin.agent;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ContextFoldingTest {

    @Test
    void estimateCharsSingleMessage() {
        Map<String, Object> msg = new LinkedHashMap<String, Object>();
        msg.put("role", "user");
        msg.put("content", "hello world");
        int n = ContextFolding.estimateChars(msg);
        assertEquals(4 + 11, n, "role=4 chars + content=11 chars");
    }

    @Test
    void estimateCharsMultipleMessages() {
        List<Map<String, Object>> msgs = new ArrayList<Map<String, Object>>();
        Map<String, Object> m1 = new LinkedHashMap<String, Object>();
        m1.put("role", "user");
        m1.put("content", "hi");
        msgs.add(m1);
        Map<String, Object> m2 = new LinkedHashMap<String, Object>();
        m2.put("role", "assistant");
        m2.put("content", "hello");
        msgs.add(m2);
        int n = ContextFolding.estimateChars(msgs);
        assertTrue(n > 0);
    }

    @Test
    void estimateCharsWithReasoning() {
        Map<String, Object> msg = new LinkedHashMap<String, Object>();
        msg.put("role", "assistant");
        msg.put("content", "ok");
        msg.put("reasoning_content", "thinking...");
        int n = ContextFolding.estimateChars(msg);
        assertTrue(n > 4 + 2, "reasoning_content 也应计入字符数");
    }

    @Test
    void foldReturnsOriginalIfUnderThreshold() throws Exception {
        List<Map<String, Object>> msgs = new ArrayList<Map<String, Object>>();
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("role", "user");
        m.put("content", "short");
        msgs.add(m);
        List<Map<String, Object>> result = ContextFolding.fold(msgs, 1000, 500, null);
        assertSame(msgs, result, "未超阈值应返回原列表");
    }
}
