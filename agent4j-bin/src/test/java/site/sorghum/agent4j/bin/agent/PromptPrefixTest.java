package site.sorghum.agent4j.bin.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PromptPrefixTest {

    @Test
    void toMessagesReturnsSystemMessage() {
        PromptPrefix prefix = new PromptPrefix("You are helpful", new java.util.ArrayList<>());
        List<ChatMessage> msgs = prefix.toMessages();
        assertEquals(1, msgs.size());
        assertEquals("system", msgs.get(0).getRole());
        assertEquals("You are helpful", msgs.get(0).getContent());
    }

    @Test
    void toolsReturnsFrozenList() {
        java.util.List<java.util.Map<String, Object>> tools = new java.util.ArrayList<>();
        java.util.Map<String, Object> tool = new java.util.LinkedHashMap<>();
        tool.put("type", "function");
        tools.add(tool);
        PromptPrefix prefix = new PromptPrefix("sys", tools);
        assertEquals(1, prefix.tools().size());
    }

    @Test
    void fingerprintChangesOnSystemReplace() {
        PromptPrefix prefix = new PromptPrefix("sys1", new java.util.ArrayList<>());
        String fp1 = prefix.fingerprint();
        prefix.replaceSystem("sys2");
        String fp2 = prefix.fingerprint();
        assertNotEquals(fp1, fp2, "替换 system 后指纹应变化");
    }

    @Test
    void nullSystemThrows() {
        assertThrows(NullPointerException.class, () -> {
            new PromptPrefix(null, new java.util.ArrayList<>());
        });
    }
}
