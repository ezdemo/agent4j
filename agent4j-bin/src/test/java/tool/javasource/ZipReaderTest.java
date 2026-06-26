package tool.javasource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import site.sorghum.agent4j.tool.javasource.ZipReader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ZipReader} 单元测试。
 */
class ZipReaderTest {

    @TempDir
    Path tempDir;

    // ── 辅助方法 ──────────────────────────────────────────────────────────

    /**
     * 在内存中创建一个最小合法 ZIP 文件并写入临时目录。
     *
     * @param entries 条目名与内容交替排列的键值对
     */
    private Path createZip(String... entries) throws IOException {
        Path zipPath = tempDir.resolve("test.jar");
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            for (int i = 0; i < entries.length; i += 2) {
                String name = entries[i];
                byte[] data = entries[i + 1].getBytes(StandardCharsets.UTF_8);
                ZipEntry entry = new ZipEntry(name);
                zos.putNextEntry(entry);
                zos.write(data);
                zos.closeEntry();
            }
            zos.finish();
            Files.write(zipPath, baos.toByteArray());
        }
        return zipPath;
    }

    // ── readJarEntry 测试 ───────────────────────────────────────────────

    @Test
    void 读取存储条目() throws IOException {
        Path jar = createZip("hello.txt", "hello world");
        ZipReader.JarEntry entry = ZipReader.readJarEntry(jar.toString(), "hello.txt");
        assertNotNull(entry);
        assertEquals("hello.txt", entry.fileName());
        assertEquals("hello world", new String(entry.data(), StandardCharsets.UTF_8));
    }

    @Test
    void 缺失条目返回null() throws IOException {
        Path jar = createZip("present.class", "fake");
        ZipReader.JarEntry entry = ZipReader.readJarEntry(jar.toString(), "missing.class");
        assertNull(entry);
    }

    @Test
    void 读取jar路径风格的class条目() throws IOException {
        Path jar = createZip("com/google/common/collect/Lists.class", "fake bytecode");
        ZipReader.JarEntry entry = ZipReader.readJarEntry(jar.toString(),
                "com/google/common/collect/Lists.class");
        assertNotNull(entry);
        assertEquals("com/google/common/collect/Lists.class", entry.fileName());
    }

    @Test
    void 多条目的正确读取() throws IOException {
        Path jar = createZip(
                "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n",
                "com/example/Foo.class", "foo bytes",
                "com/example/Bar.class", "bar bytes"
        );

        ZipReader.JarEntry foo = ZipReader.readJarEntry(jar.toString(), "com/example/Foo.class");
        assertEquals("foo bytes", new String(foo.data(), StandardCharsets.UTF_8));

        ZipReader.JarEntry bar = ZipReader.readJarEntry(jar.toString(), "com/example/Bar.class");
        assertEquals("bar bytes", new String(bar.data(), StandardCharsets.UTF_8));
    }

    // ── readJarEntryAsString 测试 ────────────────────────────────────────

    @Test
    void 以字符串读取条目() throws IOException {
        Path jar = createZip("README.md", "# Hello\n\nWorld");
        String content = ZipReader.readJarEntryAsString(jar.toString(), "README.md");
        assertEquals("# Hello\n\nWorld", content);
    }

    @Test
    void 缺失条目返回null字符串() throws IOException {
        Path jar = createZip("a.txt", "data");
        assertNull(ZipReader.readJarEntryAsString(jar.toString(), "missing.txt"));
    }

    // ── listJarEntries 测试 ──────────────────────────────────────────────

    @Test
    void 列出所有条目() throws IOException {
        Path jar = createZip(
                "a.class", "a",
                "b.class", "b",
                "c.class", "c"
        );
        List<String> entries = ZipReader.listJarEntries(jar.toString());
        assertEquals(3, entries.size());
        assertTrue(entries.containsAll(List.of("a.class", "b.class", "c.class")));
    }

    @Test
    void 空zip返回空列表() throws IOException {
        Path jarPath = tempDir.resolve("empty.jar");
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.finish();
            Files.write(jarPath, baos.toByteArray());
        }
        List<String> entries = ZipReader.listJarEntries(jarPath.toString());
        assertTrue(entries.isEmpty());
    }

    // ── 错误处理 ────────────────────────────────────────────────────

    @Test
    void 不存在的文件抛异常() {
        assertThrows(IOException.class, () ->
                ZipReader.readJarEntry("/nonexistent/path.jar", "x"));
    }

    @Test
    void 损坏文件抛异常() throws IOException {
        Path garbage = tempDir.resolve("garbage.bin");
        Files.writeString(garbage, "not a zip file");
        assertThrows(IOException.class, () ->
                ZipReader.readJarEntry(garbage.toString(), "x"));
    }
}
