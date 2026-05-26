package site.sorghum.agent4j.tool.file;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link FileEdit} 单元测试——文件读写编辑引擎。
 *
 * @author Sorghum
 */
@DisplayName("FileEdit 文件读写编辑引擎测试")
class FileEditTest {

    @Nested
    @DisplayName("readFile 读取文件")
    class ReadFile {

        @Test
        @DisplayName("应完整读取小文件")
        void shouldReadFullFile(@TempDir Path tempDir) throws IOException {
            Path f = tempDir.resolve("test.txt");
            Files.write(f, "hello\nworld".getBytes());
            String result = FileEdit.readFile(tempDir, "test.txt", null, null, null);
            assertTrue(result.contains("hello"));
            assertTrue(result.contains("world"));
        }

        @Test
        @DisplayName("文件不存在应返回错误")
        void notFound_shouldReturnError(@TempDir Path tempDir) throws IOException {
            String result = FileEdit.readFile(tempDir, "nonexistent.txt", null, null, null);
            assertTrue(result.contains("NOT_FOUND"));
        }

        @Test
        @DisplayName("目录路径应返回提示")
        void directoryPath_shouldReturnIsDir(@TempDir Path tempDir) throws IOException {
            Files.createDirectories(tempDir.resolve("subdir"));
            String result = FileEdit.readFile(tempDir, "subdir", null, null, null);
            assertTrue(result.contains("IS_DIR"));
        }

        @Test
        @DisplayName("head 应返回前 N 行")
        void head_shouldReturnFirstNLines(@TempDir Path tempDir) throws IOException {
            Path f = tempDir.resolve("test.txt");
            Files.write(f, "line1\nline2\nline3\nline4\nline5".getBytes());
            String result = FileEdit.readFile(tempDir, "test.txt", 3, null, null);
            assertTrue(result.contains("line1"));
            assertTrue(result.contains("line3"));
            assertFalse(result.contains("line5"));
        }

        @Test
        @DisplayName("tail 应返回后 N 行")
        void tail_shouldReturnLastNLines(@TempDir Path tempDir) throws IOException {
            Path f = tempDir.resolve("test.txt");
            Files.write(f, "line1\nline2\nline3\nline4\nline5".getBytes());
            String result = FileEdit.readFile(tempDir, "test.txt", null, 2, null);
            assertTrue(result.contains("line4"));
            assertTrue(result.contains("line5"));
            assertFalse(result.contains("line1"));
        }

        @Test
        @DisplayName("range 应返回指定行范围")
        void range_shouldReturnLines(@TempDir Path tempDir) throws IOException {
            Path f = tempDir.resolve("test.txt");
            Files.write(f, "line1\nline2\nline3\nline4\nline5".getBytes());
            String result = FileEdit.readFile(tempDir, "test.txt", null, null, "2-4");
            assertTrue(result.contains("line2"));
            assertTrue(result.contains("line4"));
            assertFalse(result.contains("line1"));
            assertFalse(result.contains("line5"));
        }

        @Test
        @DisplayName("二进制文件应被拒绝")
        void binaryFile_shouldBeRefused(@TempDir Path tempDir) throws IOException {
            Path f = tempDir.resolve("binary.bin");
            byte[] data = new byte[]{0, 1, 2, 3, 4};
            Files.write(f, data);
            String result = FileEdit.readFile(tempDir, "binary.bin", null, null, null);
            assertTrue(result.contains("REFUSED") || result.contains("二进制"));
        }

        @Test
        @DisplayName("超大文件应使用大纲模式")
        void largeFile_shouldUseOutlineMode(@TempDir Path tempDir) throws IOException {
            Path f = tempDir.resolve("large.txt");
            StringBuilder sb = new StringBuilder();
            // 生成足够大的文件以触发 outline 模式（阈值 64KB）
            for (int i = 0; i < 20000; i++) {
                sb.append("This is a rather long line to ensure the file exceeds the outline threshold of 64 KiB: line ").append(i).append("\n");
            }
            Files.write(f, sb.toString().getBytes());
            assertTrue(Files.size(f) > 64 * 1024, "文件应大于 64KB 以触发大纲模式");
            String result = FileEdit.readFile(tempDir, "large.txt", null, null, null);
            assertTrue(result.contains("large file") || result.contains("outline"),
                    () -> "应为大纲模式，实际输出前100字符: " + result.substring(0, Math.min(100, result.length())));
        }
    }

    @Nested
    @DisplayName("editFile SEARCH/REPLACE 编辑")
    class EditFile {

        @Test
        @DisplayName("应正确替换文本")
        void shouldReplaceText(@TempDir Path tempDir) throws IOException {
            Path f = tempDir.resolve("Hello.java");
            Files.write(f, "public class Hello {\n    public void greet() {\n        System.out.println(\"Hello!\");\n    }\n}".getBytes());

            String result = FileEdit.editFile(tempDir, "Hello.java",
                    "System.out.println(\"Hello!\");",
                    "System.out.println(\"Hello, World!\");");

            assertTrue(result.contains("edited Hello.java"));
            String content = new String(Files.readAllBytes(f));
            assertTrue(content.contains("Hello, World!"));
            assertFalse(content.contains("Hello!"));
        }

        @Test
        @DisplayName("search 必须唯一，重复应拒绝")
        void duplicateSearch_shouldReject(@TempDir Path tempDir) throws IOException {
            Path f = tempDir.resolve("Dup.java");
            Files.write(f, "foo\nbar\nfoo".getBytes());

            IOException ex = assertThrows(IOException.class,
                    () -> FileEdit.editFile(tempDir, "Dup.java", "foo", "baz"));
            assertTrue(ex.getMessage().contains("多次"));
        }

        @Test
        @DisplayName("search 未找到应报错")
        void searchNotFound_shouldReject(@TempDir Path tempDir) throws IOException {
            Path f = tempDir.resolve("NoMatch.java");
            Files.write(f, "hello world".getBytes());

            IOException ex = assertThrows(IOException.class,
                    () -> FileEdit.editFile(tempDir, "NoMatch.java", "nonexistent", "replacement"));
            assertTrue(ex.getMessage().contains("未找到"));
        }

        @Test
        @DisplayName("search 不能为空")
        void emptySearch_shouldReject(@TempDir Path tempDir) {
            assertThrows(IOException.class,
                    () -> FileEdit.editFile(tempDir, "test.txt", "", "replacement"));
        }

        @Test
        @DisplayName("替换后的文件内容应正确")
        void replacedContent_shouldBeCorrect(@TempDir Path tempDir) throws IOException {
            Path f = tempDir.resolve("Test.java");
            Files.write(f, "int a = 1;\nint b = 2;\nint c = 3;".getBytes());

            FileEdit.editFile(tempDir, "Test.java", "int b = 2;", "int b = 20;");
            String content = new String(Files.readAllBytes(f));
            assertEquals("int a = 1;\nint b = 20;\nint c = 3;", content);
        }

        @Test
        @DisplayName("应支持多行 SEARCH/REPLACE")
        void shouldSupportMultiLine(@TempDir Path tempDir) throws IOException {
            Path f = tempDir.resolve("Multi.java");
            Files.write(f, "class A {\n    void old() {}\n    void keep() {}\n}".getBytes());

            FileEdit.editFile(tempDir, "Multi.java",
                    "    void old() {}",
                    "    void newMethod() {}");
            String content = new String(Files.readAllBytes(f));
            assertTrue(content.contains("newMethod"));
            assertFalse(content.contains("old()"));
        }
    }

    @Nested
    @DisplayName("writeFile 写入文件")
    class WriteFile {

        @Test
        @DisplayName("应创建新文件")
        void shouldCreateFile(@TempDir Path tempDir) throws IOException {
            String result = FileEdit.writeFile(tempDir, "newfile.txt", "hello");
            assertTrue(result.contains("wrote"));
            assertTrue(Files.exists(tempDir.resolve("newfile.txt")));
            assertEquals("hello", new String(Files.readAllBytes(tempDir.resolve("newfile.txt"))));
        }

        @Test
        @DisplayName("应自动创建父目录")
        void shouldCreateParentDirectories(@TempDir Path tempDir) throws IOException {
            FileEdit.writeFile(tempDir, "a/b/c/deep.txt", "deep content");
            assertTrue(Files.exists(tempDir.resolve("a/b/c/deep.txt")));
            assertEquals("deep content", new String(Files.readAllBytes(tempDir.resolve("a/b/c/deep.txt"))));
        }

        @Test
        @DisplayName("应覆盖已有文件")
        void shouldOverwriteExistingFile(@TempDir Path tempDir) throws IOException {
            Path f = tempDir.resolve("existing.txt");
            Files.write(f, "old content".getBytes());
            FileEdit.writeFile(tempDir, "existing.txt", "new content");
            assertEquals("new content", new String(Files.readAllBytes(f)));
        }

        @Test
        @DisplayName("空内容应创建空文件")
        void emptyContent_shouldCreateEmptyFile(@TempDir Path tempDir) throws IOException {
            FileEdit.writeFile(tempDir, "empty.txt", "");
            assertEquals(0, Files.size(tempDir.resolve("empty.txt")));
        }
    }

    @Nested
    @DisplayName("multiEdit 批量原子编辑")
    class MultiEdit {

        @Test
        @DisplayName("应批量编辑多个文件")
        void shouldEditMultipleFiles(@TempDir Path tempDir) throws IOException {
            Path f1 = tempDir.resolve("A.java");
            Path f2 = tempDir.resolve("B.java");
            Files.write(f1, "old A".getBytes());
            Files.write(f2, "old B".getBytes());

            List<Map<String, Object>> edits = new ArrayList<>();
            edits.add(createEdit("A.java", "old A", "new A"));
            edits.add(createEdit("B.java", "old B", "new B"));

            String result = FileEdit.multiEdit(tempDir, edits);
            assertTrue(result.contains("applied 2 edits"));
            assertEquals("new A", new String(Files.readAllBytes(f1)));
            assertEquals("new B", new String(Files.readAllBytes(f2)));
        }

        @Test
        @DisplayName("某一步失败应回滚所有已写入文件")
        void failure_shouldRollback(@TempDir Path tempDir) throws IOException {
            Path f1 = tempDir.resolve("A.java");
            Path f2 = tempDir.resolve("B.java");
            Files.write(f1, "original A".getBytes());
            Files.write(f2, "original B".getBytes());

            List<Map<String, Object>> edits = new ArrayList<>();
            edits.add(createEdit("A.java", "original A", "modified A"));
            edits.add(createEdit("B.java", "NONEXISTENT", "modified B"));
            edits.add(createEdit("A.java", "modified A", "final A"));

            assertThrows(IOException.class, () -> FileEdit.multiEdit(tempDir, edits));
            // 验证回滚
            assertEquals("original A", new String(Files.readAllBytes(f1)));
            assertEquals("original B", new String(Files.readAllBytes(f2)));
        }

        @Test
        @DisplayName("空编辑列表应报错")
        void emptyEdits_shouldReject(@TempDir Path tempDir) {
            assertThrows(IOException.class,
                    () -> FileEdit.multiEdit(tempDir, Collections.emptyList()));
        }

        @Test
        @DisplayName("search 多次出现应报错")
        void duplicateSearch_shouldRejectMulti(@TempDir Path tempDir) throws IOException {
            Path f = tempDir.resolve("Dup.java");
            Files.write(f, "foo\nbar\nfoo".getBytes());

            List<Map<String, Object>> edits = new ArrayList<>();
            edits.add(createEdit("Dup.java", "foo", "baz"));

            assertThrows(IOException.class, () -> FileEdit.multiEdit(tempDir, edits));
        }

        @Test
        @DisplayName("编辑同一文件多次应正确执行")
        void multipleEditsSameFile(@TempDir Path tempDir) throws IOException {
            Path f = tempDir.resolve("Multi.java");
            Files.write(f, "a\nb\nc".getBytes());

            List<Map<String, Object>> edits = new ArrayList<>();
            edits.add(createEdit("Multi.java", "a", "A"));
            edits.add(createEdit("Multi.java", "c", "C"));

            FileEdit.multiEdit(tempDir, edits);
            assertEquals("A\nb\nC", new String(Files.readAllBytes(f)));
        }
    }

    @Nested
    @DisplayName("copyFile 复制文件/目录")
    class CopyFile {

        @Test
        @DisplayName("应复制文件")
        void shouldCopyFile(@TempDir Path tempDir) throws IOException {
            Files.write(tempDir.resolve("source.txt"), "content".getBytes());
            String result = FileEdit.copyFile(tempDir, "source.txt", "dest.txt");
            assertTrue(result.contains("copied"));
            assertTrue(Files.exists(tempDir.resolve("dest.txt")));
            assertEquals("content", new String(Files.readAllBytes(tempDir.resolve("dest.txt"))));
        }

        @Test
        @DisplayName("应复制目录")
        void shouldCopyDirectory(@TempDir Path tempDir) throws IOException {
            Files.createDirectories(tempDir.resolve("src/sub"));
            Files.write(tempDir.resolve("src/file.txt"), "data".getBytes());

            FileEdit.copyFile(tempDir, "src", "dst");
            assertTrue(Files.exists(tempDir.resolve("dst/file.txt")));
            assertEquals("data", new String(Files.readAllBytes(tempDir.resolve("dst/file.txt"))));
        }

        @Test
        @DisplayName("源不存在应报错")
        void sourceNotFound_shouldThrow(@TempDir Path tempDir) {
            assertThrows(IOException.class,
                    () -> FileEdit.copyFile(tempDir, "nonexistent", "dest"));
        }
    }

    @Nested
    @DisplayName("getFileInfo 文件元信息")
    class GetFileInfo {

        @Test
        @DisplayName("应返回文件信息")
        void shouldReturnFileInfo(@TempDir Path tempDir) throws IOException {
            Path f = tempDir.resolve("test.txt");
            Files.write(f, "hello".getBytes());
            String result = FileEdit.getFileInfo(tempDir, "test.txt");
            assertTrue(result.contains("\"type\":\"file\""));
            assertTrue(result.contains("\"size\""));
            assertTrue(result.contains("\"mtime\""));
        }

        @Test
        @DisplayName("应返回目录信息")
        void shouldReturnDirInfo(@TempDir Path tempDir) throws IOException {
            Files.createDirectories(tempDir.resolve("mydir"));
            String result = FileEdit.getFileInfo(tempDir, "mydir");
            assertTrue(result.contains("\"type\":\"directory\""));
        }

        @Test
        @DisplayName("不存在的路径应返回错误")
        void notFound_shouldReturnError(@TempDir Path tempDir) throws IOException {
            String result = FileEdit.getFileInfo(tempDir, "nonexistent");
            assertTrue(result.contains("error"));
        }
    }

    @Nested
    @DisplayName("路径安全防护")
    class PathSecurity {

        @Test
        @DisplayName("路径越界应被拒绝")
        void pathTraversal_shouldBeRejected(@TempDir Path tempDir) {
            assertThrows(IOException.class,
                    () -> FileEdit.readFile(tempDir, "../outside.txt", null, null, null));
        }

        @Test
        @DisplayName("绝对路径越界应被拒绝")
        void absolutePathOutside_shouldBeRejected(@TempDir Path tempDir) {
            String outside = tempDir.getParent().resolve("secret.txt").toString();
            assertThrows(IOException.class,
                    () -> FileEdit.readFile(tempDir, outside, null, null, null));
        }
    }

    // ==================== 辅助方法 ====================

    private static Map<String, Object> createEdit(String path, String search, String replace) {
        Map<String, Object> edit = new LinkedHashMap<>();
        edit.put("path", path);
        edit.put("search", search);
        edit.put("replace", replace);
        return edit;
    }
}
