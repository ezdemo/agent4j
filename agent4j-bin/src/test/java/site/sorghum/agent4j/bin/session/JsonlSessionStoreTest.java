package site.sorghum.agent4j.bin.session;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class JsonlSessionStoreTest {

    private JsonlSessionStore store;
    private static int counter = 0;

    @BeforeEach
    void setUp() throws IOException {
        store = new JsonlSessionStore();
        // 使用唯一会话名避免测试间干扰
        counter++;
        store.switchTo("test-" + System.nanoTime() + "-" + counter);
    }

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.shutdown();
        }
    }

    @Test
    void currentNameIsNullBeforeSwitch() throws IOException {
        // 新建 store 时 currentName 为 null（不自动分配会话名）
        JsonlSessionStore freshStore = new JsonlSessionStore();
        try {
            assertNull(freshStore.currentName());
        } finally {
            freshStore.shutdown();
        }
    }

    @Test
    void currentNameIsNonNullAfterSwitch() {
        // setUp 中已 switchTo，此时 currentName 非空
        assertNotNull(store.currentName());
        assertFalse(store.currentName().isEmpty());
    }

    @Test
    void appendAndLoad() throws IOException {
        Map<String, Object> msg = new LinkedHashMap<String, Object>();
        msg.put("role", "user");
        msg.put("content", "hello");
        store.append(msg);

        store.flush();
        List<Map<String, Object>> loaded = store.load();
        assertEquals(1, loaded.size());
        assertEquals("user", loaded.get(0).get("role"));
        assertEquals("hello", loaded.get(0).get("content"));
    }

    @Test
    void appendMultipleMessages() throws IOException {
        for (int i = 0; i < 5; i++) {
            Map<String, Object> msg = new LinkedHashMap<String, Object>();
            msg.put("role", "user");
            msg.put("content", "msg" + i);
            store.append(msg);
        }
        store.flush();
        List<Map<String, Object>> loaded = store.load();
        assertEquals(5, loaded.size());
    }

    @Test
    void rewriteReplacesAllMessages() throws IOException {
        Map<String, Object> msg1 = new LinkedHashMap<String, Object>();
        msg1.put("role", "user");
        msg1.put("content", "original");
        store.append(msg1);
        store.flush();

        List<Map<String, Object>> newMsgs = new ArrayList<Map<String, Object>>();
        Map<String, Object> msg2 = new LinkedHashMap<String, Object>();
        msg2.put("role", "assistant");
        msg2.put("content", "replaced");
        newMsgs.add(msg2);
        store.rewrite(newMsgs);

        List<Map<String, Object>> loaded = store.load();
        assertEquals(1, loaded.size());
        assertEquals("replaced", loaded.get(0).get("content"));
    }

    @Test
    void switchToAndLoad() throws IOException {
        String uniqueName = "sw-" + System.nanoTime();
        Map<String, Object> msg = new LinkedHashMap<String, Object>();
        msg.put("role", "user");
        msg.put("content", "in session 1");
        store.append(msg);
        store.flush();

        String name1 = store.currentName();
        assertTrue(store.switchTo(uniqueName));
        assertNotEquals(name1, store.currentName());

        Map<String, Object> msg2 = new LinkedHashMap<String, Object>();
        msg2.put("role", "user");
        msg2.put("content", "in session 2");
        store.append(msg2);
        store.flush();

        List<Map<String, Object>> loaded = store.load();
        assertEquals(1, loaded.size());
        assertEquals("in session 2", loaded.get(0).get("content"));

        store.switchTo(name1);
        List<Map<String, Object>> loaded1 = store.load();
        assertEquals(1, loaded1.size());
        assertEquals("in session 1", loaded1.get(0).get("content"));
    }

    @Test
    void listReturnsSessions() throws IOException {
        Map<String, Object> msg = new LinkedHashMap<String, Object>();
        msg.put("role", "user");
        msg.put("content", "hi");
        store.append(msg);
        store.flush();

        List<SessionStore.SessionInfo> sessions = store.list();
        assertFalse(sessions.isEmpty());
        boolean found = false;
        for (SessionStore.SessionInfo s : sessions) {
            if (s.name.equals(store.currentName())) {
                found = true;
                assertTrue(s.messageCount >= 1);
            }
        }
        assertTrue(found, "当前会话应在列表中");
    }

    @Test
    void saveAndLoadUsage() throws IOException {
        store.saveUsage("test-session", 100, 200, 50, 50);
        long[] usage = store.loadUsage("test-session");
        assertEquals(100, usage[0]);
        assertEquals(200, usage[1]);
        assertEquals(50, usage[2]);
        assertEquals(50, usage[3]);
    }

    @Test
    void loadUsageNonexistentReturnsZeros() {
        long[] usage = store.loadUsage("nonexistent-session-12345");
        assertEquals(0, usage[0]);
        assertEquals(0, usage[1]);
        assertEquals(0, usage[2]);
        assertEquals(0, usage[3]);
    }

    @Test
    void deleteSession() throws IOException {
        Map<String, Object> msg = new LinkedHashMap<String, Object>();
        msg.put("role", "user");
        msg.put("content", "to be deleted");
        store.append(msg);
        store.flush();

        String name = store.currentName();
        boolean deleted = store.delete(name);
        assertTrue(deleted);
    }

    @Test
    void newSessionNameHasCorrectFormat() {
        String name = store.newSessionName();
        assertTrue(name.startsWith("agent4j-"), "会话名应以 agent4j- 开头");
        assertTrue(name.length() > "agent4j-".length(), "会话名应包含时间戳");
    }

    @Test
    void serializeMessageWithToolCalls() {
        Map<String, Object> msg = new LinkedHashMap<String, Object>();
        msg.put("role", "assistant");
        msg.put("content", "I'll edit");
        List<Map<String, Object>> tcs = new ArrayList<Map<String, Object>>();
        Map<String, Object> tc = new LinkedHashMap<String, Object>();
        tc.put("id", "tc_1");
        tc.put("name", "edit_file");
        tc.put("arguments", "{\"path\":\"a.java\"}");
        tcs.add(tc);
        msg.put("tool_calls", tcs);

        String json = JsonlSessionStore.serializeMessage(msg);
        assertTrue(json.contains("edit_file"));
        assertTrue(json.contains("tc_1"));
    }

    @Test
    void flushDoesNotThrow() {
        assertDoesNotThrow(() -> store.flush());
    }
}
