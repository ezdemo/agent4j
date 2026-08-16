package site.sorghum.loopra.bin.requirement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 需求池存储单元测试：增删改查 + 磁盘持久化。
 *
 * @author Sorghum
 */
class RequirementStoreTest {

    @TempDir
    Path tempDir;

    private RequirementStore store;

    @BeforeEach
    void setUp() {
        store = new RequirementStore(tempDir);
    }

    @AfterEach
    void tearDown() {
        store = null;
    }

    private Requirement sample(String id, String title) {
        long now = System.currentTimeMillis();
        return Requirement.builder()
                .id(id)
                .title(title)
                .description("描述")
                .priority("high")
                .projectHash("p1")
                .projectName("agent4j")
                .status("todo")
                .summary("")
                .sessionName("req_" + id)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    @Test
    void upsertThenLoadAllReturnsRequirements() {
        store.upsert(sample("a", "需求 A"));
        store.upsert(sample("b", "需求 B"));

        List<Requirement> all = store.loadAll();
        assertEquals(2, all.size());
        assertEquals("需求 A", all.get(0).getTitle());
        assertEquals("todo", all.get(0).getStatus());
        assertEquals("req_a", all.get(0).getSessionName());
    }

    @Test
    void updateOverwritesExistingFields() {
        store.upsert(sample("a", "需求 A"));

        Requirement existing = store.get("a");
        existing.setStatus("done");
        existing.setSummary("AI 完成总结");
        store.upsert(existing);

        Requirement updated = store.get("a");
        assertEquals("done", updated.getStatus());
        assertEquals("AI 完成总结", updated.getSummary());
        assertEquals(1, store.loadAll().size());
    }

    @Test
    void removeDeletesRequirement() {
        store.upsert(sample("a", "需求 A"));
        store.upsert(sample("b", "需求 B"));

        assertTrue(store.remove("a"));
        assertFalse(store.remove("a"));
        assertEquals(1, store.loadAll().size());
        assertNull(store.get("a"));
        assertNotNull(store.get("b"));
    }

    @Test
    void dataPersistsAcrossStoreInstances() throws Exception {
        store.upsert(sample("a", "需求 A"));

        // 新实例（同一目录）应能读回磁盘数据
        RequirementStore reloaded = new RequirementStore(tempDir);
        List<Requirement> all = reloaded.loadAll();
        assertEquals(1, all.size());
        assertEquals("需求 A", all.get(0).getTitle());
        assertEquals("req_a", all.get(0).getSessionName());
        assertTrue(Files.exists(tempDir.resolve("requirements.json")));
    }

    @Test
    void loadAllOnMissingFileReturnsEmptyList() {
        assertTrue(store.loadAll().isEmpty());
    }
}
