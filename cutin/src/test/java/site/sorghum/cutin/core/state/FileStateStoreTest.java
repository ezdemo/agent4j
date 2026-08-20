package site.sorghum.cutin.core.state;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.noear.snack4.ONode;
import site.sorghum.cutin.core.context.Budget;
import site.sorghum.cutin.core.context.Message;
import site.sorghum.cutin.core.context.Usage;
import site.sorghum.cutin.core.json.JsonSupport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 文件状态存储测试：验证快照落盘后可按版本恢复。
 */
class FileStateStoreTest {

    /** 临时存储目录。 */
    @TempDir
    Path tempDir;

    /** 保存的快照应能通过新实例按版本与最新记录恢复。 */
    @Test
    void persistsAndRestoresSnapshotsByVersion() {
        FileStateStore store = new FileStateStore(tempDir);
        LoopSnapshot snapshot = new LoopSnapshot(
            "loop-1",
            3,
            "tool",
            List.of(new Message("user", "hello")),
            Map.of("count", 2),
            Map.of(),
            new Usage(10, 5, 1, 3, 2),
            Budget.unlimited(),
            null
        );

        store.save(snapshot);

        FileStateStore reopened = new FileStateStore(tempDir);
        LoopSnapshot restored = reopened.version("loop-1", 3).orElseThrow();

        assertEquals(snapshot.loopId(), restored.loopId());
        assertEquals(snapshot.stateVersion(), restored.stateVersion());
        assertEquals(snapshot.nodeId(), restored.nodeId());
        assertEquals(snapshot.messages(), restored.messages());
        assertEquals(snapshot.variables(), restored.variables());
        assertEquals(snapshot.usage(), restored.usage());
        assertEquals(snapshot.budget().snapshot(), restored.budget().snapshot());
        assertEquals(snapshot, reopened.latest("loop-1").orElseThrow());
    }

    /** 旧快照缺少缓存字段时应按 0 兼容恢复。 */
    @Test
    void restoresLegacySnapshotWithoutCacheFields() throws Exception {
        FileStateStore store = new FileStateStore(tempDir);
        store.save(new LoopSnapshot(
            "loop-1",
            3,
            "tool",
            List.of(new Message("user", "hello")),
            Map.of(),
            Map.of(),
            new Usage(10, 5, 1, 3, 2),
            Budget.unlimited(),
            null
        ));

        Path file;
        try (var files = Files.list(tempDir.resolve("loop-1"))) {
            file = files.findFirst().orElseThrow();
        }
        ONode json = JsonSupport.read(Files.readString(file));
        ONode usage = json.get("usage");
        usage.remove("cacheReadTokens");
        usage.remove("cacheCreationTokens");
        Files.writeString(file, JsonSupport.writePretty(json));

        LoopSnapshot restored = new FileStateStore(tempDir).version("loop-1", 3).orElseThrow();
        assertEquals(new Usage(10, 5, 1), restored.usage());
    }

    /** 工作目录应随快照持久化，并在恢复时保留。 */
    @Test
    void persistsWorkingDirectory() {
        Path workingDirectory = tempDir.resolve("project").toAbsolutePath().normalize();
        FileStateStore store = new FileStateStore(tempDir);
        LoopSnapshot snapshot = new LoopSnapshot(
            "loop-1",
            3,
            "tool",
            List.of(new Message("user", "hello")),
            Map.of(),
            Map.of(),
            new Usage(10, 5, 1, 3, 2),
            Budget.unlimited(),
            workingDirectory
        );

        store.save(snapshot);

        assertEquals(
            workingDirectory,
            new FileStateStore(tempDir).version("loop-1", 3).orElseThrow().workingDirectory()
        );
    }
}
