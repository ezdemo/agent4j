package site.sorghum.loopra.bin.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SubAgentSessionStoreTest {

    @TempDir
    Path tempDir;

    private SubAgentSessionStore newStore() {
        return new SubAgentSessionStore(tempDir);
    }

    private Map<String, Object> payload(String subId, String subSessionId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("subId", subId);
        payload.put("subSessionId", subSessionId);
        return payload;
    }

    @Test
    void recordAndListReadsMetaAndStatus() {
        SubAgentSessionStore store = newStore();
        String subSessionId = "sub-abc123";

        Map<String, Object> start = payload("1", subSessionId);
        start.put("task", "初音未来：探索项目结构。");
        start.put("name", "初音未来");
        start.put("title", "探索项目结构。");
        start.put("profile", "explore");
        start.put("startedAt", 1000L);
        store.record("父会话", subSessionId, "sub_start", start);

        Map<String, Object> toolCall = payload("1", subSessionId);
        toolCall.put("name", "read");
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("file_path", "src/App.java");
        toolCall.put("args", args);
        store.record("父会话", subSessionId, "sub_tool_call", toolCall);

        Map<String, Object> end = payload("1", subSessionId);
        end.put("status", "completed");
        end.put("endedAt", 2000L);
        store.record("父会话", subSessionId, "sub_end", end);

        List<SubAgentSessionStore.SubSessionInfo> list = store.list("父会话");
        assertEquals(1, list.size());
        SubAgentSessionStore.SubSessionInfo info = list.get(0);
        assertEquals(subSessionId, info.subSessionId());
        assertEquals("初音未来：探索项目结构。", info.task());
        assertEquals("初音未来", info.name());
        assertEquals("探索项目结构。", info.title());
        assertEquals("explore", info.profile());
        assertEquals("completed", info.status());
        assertEquals(1000L, info.startedAt());
        assertEquals(2000L, info.endedAt());
        assertEquals(3, info.eventCount());
    }

    @Test
    void deleteRemovesFileAndEmptyDir() {
        SubAgentSessionStore store = newStore();
        String subSessionId = "sub-del-1";

        Map<String, Object> start = payload("1", subSessionId);
        start.put("task", "t");
        start.put("startedAt", 1L);
        store.record("parent", subSessionId, "sub_start", start);
        store.record("parent", subSessionId, "sub_end", payload("1", subSessionId));
        assertEquals(1, store.list("parent").size());

        assertTrue(store.delete("parent", subSessionId));
        assertTrue(store.list("parent").isEmpty());
        // 目录整体清空
        assertFalse(Files.exists(SubAgentSessionStore.subDir(tempDir, "parent")));
        // 再次删除返回 false
        assertFalse(store.delete("parent", subSessionId));
    }

    @Test
    void deleteRejectsInvalidSubSessionId() {
        SubAgentSessionStore store = newStore();
        assertFalse(store.delete("parent", "../evil"));
        assertFalse(store.delete("parent", ""));
    }

    @Test
    void eventsReadBackInOrderWithNestedStructures() {
        SubAgentSessionStore store = newStore();
        String subSessionId = "sub-events-1";

        Map<String, Object> start = payload("2", subSessionId);
        start.put("task", "t");
        start.put("profile", "review");
        start.put("startedAt", 1L);
        store.record("parent", subSessionId, "sub_start", start);

        Map<String, Object> toolCall = payload("2", subSessionId);
        toolCall.put("name", "bash");
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("command", "ls -la");
        args.put("cwd", "/tmp");
        toolCall.put("args", args);
        store.record("parent", subSessionId, "sub_tool_call", toolCall);

        Map<String, Object> choice = payload("2", subSessionId);
        choice.put("title", "审批");
        choice.put("options", List.of(Map.of("title", "同意", "value", "approve")));
        store.record("parent", subSessionId, "sub_choice", choice);

        List<Map<String, Object>> events = store.events("parent", subSessionId);
        assertEquals(3, events.size());
        assertEquals("sub_start", events.get(0).get("type"));
        assertEquals("sub_tool_call", events.get(1).get("type"));
        assertEquals("bash", events.get(1).get("name"));
        assertEquals("ls -la", ((Map<?, ?>) events.get(1).get("args")).get("command"));
        assertEquals("sub_choice", events.get(2).get("type"));
        assertEquals("同意", ((Map<?, ?>) ((List<?>) events.get(2).get("options")).get(0)).get("title"));
    }

    @Test
    void missingSubEndIsRunning() {
        SubAgentSessionStore store = newStore();
        Map<String, Object> start = payload("3", "sub-running");
        start.put("task", "t");
        start.put("profile", "implement");
        start.put("startedAt", 1L);
        store.record("parent", "sub-running", "sub_start", start);

        List<SubAgentSessionStore.SubSessionInfo> list = store.list("parent");
        assertEquals(1, list.size());
        assertEquals("running", list.get(0).status());
        assertEquals(0L, list.get(0).endedAt());
    }

    @Test
    void parentsAreIsolated() {
        SubAgentSessionStore store = newStore();
        store.record("会话A", "sub-a", "sub_start", payload("1", "sub-a"));
        store.record("会话B", "sub-b", "sub_start", payload("1", "sub-b"));

        assertEquals(1, store.list("会话A").size());
        assertEquals("sub-a", store.list("会话A").get(0).subSessionId());
        assertEquals(1, store.list("会话B").size());
        assertEquals("sub-b", store.list("会话B").get(0).subSessionId());
        assertTrue(store.events("会话A", "sub-b").isEmpty());
    }

    @Test
    void listSortedByStartedAtDesc() {
        SubAgentSessionStore store = newStore();
        Map<String, Object> old = payload("1", "sub-old");
        old.put("startedAt", 100L);
        store.record("parent", "sub-old", "sub_start", old);
        Map<String, Object> recent = payload("2", "sub-recent");
        recent.put("startedAt", 200L);
        store.record("parent", "sub-recent", "sub_start", recent);

        List<SubAgentSessionStore.SubSessionInfo> list = store.list("parent");
        assertEquals("sub-recent", list.get(0).subSessionId());
        assertEquals("sub-old", list.get(1).subSessionId());
    }

    @Test
    void invalidSubSessionIdIsRejectedEverywhere() {
        SubAgentSessionStore store = newStore();
        // 含路径分隔符/冒号的非法 id：写入与读取都必须被拒绝，杜绝目录穿越
        assertFalse(SubAgentSessionStore.isValidSubSessionId("../evil"));
        assertFalse(SubAgentSessionStore.isValidSubSessionId("a:b"));
        assertFalse(SubAgentSessionStore.isValidSubSessionId("a\\b"));
        assertFalse(SubAgentSessionStore.isValidSubSessionId(""));
        assertFalse(SubAgentSessionStore.isValidSubSessionId(null));
        assertTrue(SubAgentSessionStore.isValidSubSessionId("sub-abc123"));

        store.record("parent", "../evil", "sub_start", payload("1", "../evil"));
        assertTrue(store.events("parent", "../evil").isEmpty());
        assertTrue(store.list("parent").isEmpty());
    }

    @Test
    void deleteParentRemovesDirectory() throws Exception {
        SubAgentSessionStore store = newStore();
        store.record("parent", "sub-a", "sub_start", payload("1", "sub-a"));
        Path subDir = SubAgentSessionStore.subDir(tempDir, "parent");
        assertTrue(Files.isDirectory(subDir));

        SubAgentSessionStore.deleteParent(tempDir, "parent");
        assertFalse(Files.exists(subDir));
        assertTrue(store.list("parent").isEmpty());
    }

    @Test
    void deleteAllRemovesAllSubDirectories() throws Exception {
        SubAgentSessionStore store = newStore();
        store.record("会话A", "sub-a", "sub_start", payload("1", "sub-a"));
        store.record("会话B", "sub-b", "sub_start", payload("1", "sub-b"));

        SubAgentSessionStore.deleteAll(tempDir);
        assertFalse(Files.exists(SubAgentSessionStore.subDir(tempDir, "会话A")));
        assertFalse(Files.exists(SubAgentSessionStore.subDir(tempDir, "会话B")));
        assertTrue(store.list("会话A").isEmpty());
        assertTrue(store.list("会话B").isEmpty());
    }

    @Test
    void chineseParentSessionSanitizedToValidDir() {
        SubAgentSessionStore store = newStore();
        // 中文父会话名（含特殊字符）sanitize 后必须是合法目录且可正常读写
        store.record("原会话[复刻] 2026/特殊:字符", "sub-ok1", "sub_start", payload("1", "sub-ok1"));
        List<SubAgentSessionStore.SubSessionInfo> list = store.list("原会话[复刻] 2026/特殊:字符");
        assertEquals(1, list.size());
        assertEquals("sub-ok1", list.get(0).subSessionId());
        assertEquals(1, store.events("原会话[复刻] 2026/特殊:字符", "sub-ok1").size());
    }
}
