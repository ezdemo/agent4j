package site.sorghum.agent4j.bin.agent;

import org.junit.jupiter.api.Test;
import site.sorghum.agent4j.bin.agent.context.ContextFolding;
import site.sorghum.agent4j.bin.agent.model.ChatMessage;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContextFoldingTest {

    @Test
    void estimateCharsSingleMessage() {
        ChatMessage msg = ChatMessage.ofUser("hello world");
        int n = ContextFolding.estimateChars(msg);
        assertEquals(4 + 11, n, "role=4 chars + content=11 chars");
    }

    @Test
    void estimateCharsMultipleMessages() {
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ChatMessage.ofUser("hi"));
        msgs.add(ChatMessage.assistant("hello", null, null));
        int n = ContextFolding.estimateChars(msgs);
        assertTrue(n > 0);
    }

    @Test
    void estimateCharsWithReasoning() {
        ChatMessage msg = ChatMessage.assistant("ok", null, "thinking...");
        int n = ContextFolding.estimateChars(msg);
        assertTrue(n > 4 + 2, "reasoning_content 也应计入字符数");
    }

    @Test
    void foldReturnsOriginalIfUnderThreshold() throws Exception {
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ChatMessage.ofUser("short"));
        List<ChatMessage> result = ContextFolding.fold(msgs, 1000, 500, null);
        assertSame(msgs, result, "未超阈值应返回原列表");
    }
}
