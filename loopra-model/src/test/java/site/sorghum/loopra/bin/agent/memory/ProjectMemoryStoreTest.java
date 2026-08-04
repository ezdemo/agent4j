package site.sorghum.loopra.bin.agent.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ProjectMemoryStore} 单元测试 —— 记忆文件读写、文件头剥离、
 * append 空值跳过、注入长度保护、截断保护。
 *
 * @author Sorghum
 */
class ProjectMemoryStoreTest {

    @Test
    void loadReturnsEmptyWhenFileMissing(@TempDir Path dir) {
        assertEquals("", ProjectMemoryStore.load(dir));
    }

    @Test
    void loadReturnsEmptyForNullWorkspace() {
        assertEquals("", ProjectMemoryStore.load(null));
    }

    @Test
    void appendCreatesDirectoryAndFile(@TempDir Path dir) throws Exception {
        Path expected = dir.resolve(".loopra").resolve("loopra-memory.md");
        assertFalse(Files.exists(expected));

        ProjectMemoryStore.append(dir, "- 用户偏好中文回复\n- 改动 AgentLoop 后跑 ReasonBreakerTest");

        assertTrue(Files.exists(expected), "应自动创建 .loopra 目录和文件");
        String content = Files.readString(expected);
        assertTrue(content.contains("# Loopra 项目记忆"), "首次写入应带文件头说明");
        assertTrue(content.contains("---"), "应有文件头分隔行");
        assertTrue(content.contains("用户偏好中文回复"));
        assertTrue(content.contains("会话折叠沉淀"), "条目应带时间戳标题");
    }

    @Test
    void loadStripsHeaderReturnsBodyOnly(@TempDir Path dir) {
        ProjectMemoryStore.append(dir, "- 架构约定：AgentLoop 不持有 workspace 字段");
        String loaded = ProjectMemoryStore.load(dir);
        assertFalse(loaded.contains("# Loopra 项目记忆"), "load 应剥离文件头说明");
        assertFalse(loaded.contains("---"));
        assertTrue(loaded.contains("AgentLoop 不持有 workspace 字段"));
    }

    @Test
    void appendSkipsEmptyAndNoneFacts(@TempDir Path dir) {
        ProjectMemoryStore.append(dir, "");
        ProjectMemoryStore.append(dir, "   ");
        ProjectMemoryStore.append(dir, "无");
        ProjectMemoryStore.append(dir, "无。");
        assertEquals("", ProjectMemoryStore.load(dir), "空值与\"无\"不应写入");
    }

    @Test
    void appendSkipsForNullWorkspace() {
        // 不抛异常即可
        assertDoesNotThrow(() -> ProjectMemoryStore.append(null, "- some fact"));
    }

    @Test
    void appendAccumulatesMultipleEntries(@TempDir Path dir) {
        ProjectMemoryStore.append(dir, "- 第一条事实");
        ProjectMemoryStore.append(dir, "- 第二条事实");
        String loaded = ProjectMemoryStore.load(dir);
        assertTrue(loaded.contains("第一条事实"));
        assertTrue(loaded.contains("第二条事实"));
        // 两条应是独立条目（各自带 ## 时间戳标题）
        long entryCount = loaded.lines().filter(l -> l.startsWith("## ")).count();
        assertEquals(2, entryCount, "两条 append 应产生两个条目");
    }

    @Test
    void loadKeepsTailWhenOverInjectLimit(@TempDir Path dir) {
        // 写入一条超大记忆，超过 MAX_INJECT_CHARS
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < ProjectMemoryStore.MAX_INJECT_CHARS + 2000; i++) {
            huge.append('x');
        }
        ProjectMemoryStore.append(dir, huge.toString());
        String loaded = ProjectMemoryStore.load(dir);
        assertTrue(loaded.length() <= ProjectMemoryStore.MAX_INJECT_CHARS + 200,
                "超限时应只返回尾部，长度受控");
    }

    @Test
    void memoryFilePathResolvesUnderLoopraDir(@TempDir Path dir) {
        Path file = ProjectMemoryStore.memoryFilePath(dir);
        assertEquals(dir.resolve(".loopra").resolve("loopra-memory.md"), file);
    }

    @Test
    void memoryFilePathNullForNullWorkspace() {
        assertNull(ProjectMemoryStore.memoryFilePath(null));
    }

    @Test
    void listEntriesReturnsEmptyWhenFileMissing(@TempDir Path dir) {
        assertTrue(ProjectMemoryStore.listEntries(dir).isEmpty());
    }

    @Test
    void listEntriesReturnsAllInOrder(@TempDir Path dir) {
        ProjectMemoryStore.append(dir, "- 第一条事实");
        ProjectMemoryStore.append(dir, "- 第二条事实");
        ProjectMemoryStore.append(dir, "- 第三条事实");
        List<String> entries = ProjectMemoryStore.listEntries(dir);
        assertEquals(3, entries.size());
        assertTrue(entries.get(0).contains("第一条事实"));
        assertTrue(entries.get(1).contains("第二条事实"));
        assertTrue(entries.get(2).contains("第三条事实"));
    }

    @Test
    void deleteByIndexRemovesTargetEntry(@TempDir Path dir) {
        ProjectMemoryStore.append(dir, "- 第一条事实");
        ProjectMemoryStore.append(dir, "- 第二条事实");
        ProjectMemoryStore.append(dir, "- 第三条事实");

        boolean ok = ProjectMemoryStore.deleteByIndex(dir, 2);
        assertTrue(ok);

        List<String> after = ProjectMemoryStore.listEntries(dir);
        assertEquals(2, after.size());
        assertTrue(after.get(0).contains("第一条事实"));
        assertFalse(after.get(1).contains("第二条事实"));
        assertTrue(after.get(1).contains("第三条事实"));
    }

    @Test
    void deleteByIndexReturnsFalseForOutOfRange(@TempDir Path dir) {
        ProjectMemoryStore.append(dir, "- 唯一一条");
        assertFalse(ProjectMemoryStore.deleteByIndex(dir, 0));
        assertFalse(ProjectMemoryStore.deleteByIndex(dir, 2));
        // 确保未误删
        assertEquals(1, ProjectMemoryStore.listEntries(dir).size());
    }

    @Test
    void deleteByIndexReturnsFalseWhenFileMissing(@TempDir Path dir) {
        assertFalse(ProjectMemoryStore.deleteByIndex(dir, 1));
    }
}
