package site.sorghum.agent4j.bin.agent;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PromptPrefixTest {

    @Test
    void toMessagesReturnsSystemMessage() {
        PromptPrefix prefix = new PromptPrefix("You are helpful", new java.util.ArrayList<Map<String, Object>>());
        java.util.List<Map<String, Object>> msgs = prefix.toMessages();
        assertEquals(1, msgs.size());
        assertEquals("system", msgs.get(0).get("role"));
        assertEquals("You are helpful", msgs.get(0).get("content"));
    }

    @Test
    void toolsReturnsFrozenList() {
        java.util.List<Map<String, Object>> tools = new java.util.ArrayList<Map<String, Object>>();
        Map<String, Object> tool = new LinkedHashMap<String, Object>();
        tool.put("type", "function");
        tools.add(tool);
        PromptPrefix prefix = new PromptPrefix("sys", tools);
        assertEquals(1, prefix.tools().size());
    }

    @Test
    void fingerprintChangesOnSystemReplace() {
        PromptPrefix prefix = new PromptPrefix("sys1", new java.util.ArrayList<Map<String, Object>>());
        String fp1 = prefix.fingerprint();
        prefix.replaceSystem("sys2");
        String fp2 = prefix.fingerprint();
        assertNotEquals(fp1, fp2, "替换 system 后指纹应变化");
    }

    @Test
    void nullSystemThrows() {
        assertThrows(NullPointerException.class, () -> {
            new PromptPrefix(null, new java.util.ArrayList<Map<String, Object>>());
        });
    }
}
