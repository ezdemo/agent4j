package site.sorghum.agent4j.tool.file;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReadFileTool / WriteFileTool / EditFileTool / CopyFileTool / GetFileInfoTool 集成测试。
 *
 * @author Sorghum
 */
@DisplayName("文件读写编辑工具测试")
class ReadWriteEditToolTest {

    private ToolContext ctx(Path root) {
        return new ToolContext(new HashMap<>(), root);
    }

    @Nested
    @DisplayName("WriteFileTool 写入文件")
    class WriteFileTests {

        @Test
        @DisplayName("创建新文件")
        void createNewFile(@TempDir Path tempDir) {
            WriteFileTool tool = new WriteFileTool();
            Map<String, Object> params = new HashMap<>();
            params.put("path", "hello.txt");
            params.put("content", "Hello, World!");
            ToolContext ctx = new ToolContext(params, tempDir);

            ToolResult r = tool.execute(ctx);
            assertTrue(r.success());
            assertTrue(Files.exists(tempDir.resolve("hello.txt")));
        }

        @Test
        @DisplayName("自动创建父目录")
        void createParentDir(@TempDir Path tempDir) {
            WriteFileTool tool = new WriteFileTool();
            Map<String, Object> params = new HashMap<>();
            params.put("path", "a/b/c/deep.txt");
            params.put("content", "deep");
            ToolContext ctx = new ToolContext(params, tempDir);

            ToolResult r = tool.execute(ctx);
            assertTrue(r.success());
            assertTrue(Files.exists(tempDir.resolve("a/b/c/deep.txt")));
        }

        @Test
        @DisplayName("覆盖已有文件")
        void overwriteFile(@TempDir Path tempDir) throws IOException {
            Files.write(tempDir.resolve("existing.txt"), "old".getBytes());
            WriteFileTool tool = new WriteFileTool();
            Map<String, Object> params = new HashMap<>();
            params.put("path", "existing.txt");
            params.put("content", "new content");
            ToolContext ctx = new ToolContext(params, tempDir);

            ToolResult r = tool.execute(ctx);
            assertTrue(r.success());
            assertEquals("new content", Files.readString(tempDir.resolve("existing.txt")));
        }
    }

    @Nested
    @DisplayName("ReadFileTool 读取文件")
    class ReadFileTests {

        @Test
        @DisplayName("读取完整文件")
        void readFullFile(@TempDir Path tempDir) throws IOException {
            Files.write(tempDir.resolve("readme.md"), Arrays.asList("# Title", "", "content here"));
            ReadFileTool tool = new ReadFileTool();
            Map<String, Object> params = new HashMap<>();
            params.put("path", "readme.md");
            ToolContext ctx = new ToolContext(params, tempDir);

            ToolResult r = tool.execute(ctx);
            assertTrue(r.success());
            assertTrue(r.text().contains("Title"));
            assertTrue(r.text().contains("content here"));
        }

        @Test
        @DisplayName("读取前 N 行")
        void readHead(@TempDir Path tempDir) throws IOException {
            Files.write(tempDir.resolve("lines.txt"), Arrays.asList("a", "b", "c", "d", "e"));
            ReadFileTool tool = new ReadFileTool();
            Map<String, Object> params = new HashMap<>();
            params.put("path", "lines.txt");
            params.put("head", 2);
            ToolContext ctx = new ToolContext(params, tempDir);

            ToolResult r = tool.execute(ctx);
            assertTrue(r.success());
            // head 返回完整文件 + 行范围标注
            assertTrue(r.text().contains("a"));
            assertTrue(r.text().contains("b"));
        }

        @Test
        @DisplayName("读取后 N 行")
        void readTail(@TempDir Path tempDir) throws IOException {
            Files.write(tempDir.resolve("lines.txt"), Arrays.asList("a", "b", "c", "d", "e"));
            ReadFileTool tool = new ReadFileTool();
            Map<String, Object> params = new HashMap<>();
            params.put("path", "lines.txt");
            params.put("tail", 2);
            ToolContext ctx = new ToolContext(params, tempDir);

            ToolResult r = tool.execute(ctx);
            assertTrue(r.success());
            // tail 返回完整文件 + 行范围标注
            assertTrue(r.text().contains("d"));
            assertTrue(r.text().contains("e"));
        }

        @Test
        @DisplayName("文件不存在应报错")
        void fileNotFound(@TempDir Path tempDir) {
            ReadFileTool tool = new ReadFileTool();
            Map<String, Object> params = new HashMap<>();
            params.put("path", "nonexistent.txt");
            ToolContext ctx = new ToolContext(params, tempDir);

            ToolResult r = tool.execute(ctx);
            assertFalse(r.success());
            assertTrue(r.text().contains("NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("EditFileTool SEARCH/REPLACE")
    class EditFileTests {

        @Test
        @DisplayName("基本替换")
        void basicReplace(@TempDir Path tempDir) throws IOException {
            Files.write(tempDir.resolve("Test.java"), Arrays.asList(
                    "public class Test {",
                    "    public void greet() {",
                    "        System.out.println(\"Hello!\");",
                    "    }",
                    "}"
            ));
            EditFileTool tool = new EditFileTool();
            Map<String, Object> params = new HashMap<>();
            params.put("path", "Test.java");
            params.put("search", "System.out.println(\"Hello!\");");
            params.put("replace", "System.out.println(\"Hi!\");");
            ToolContext ctx = new ToolContext(params, tempDir);

            ToolResult r = tool.execute(ctx);
            assertTrue(r.success());
            String content = Files.readString(tempDir.resolve("Test.java"));
            assertTrue(content.contains("Hi!"));
            assertFalse(content.contains("Hello!"));
        }

        @Test
        @DisplayName("search 唯一性检查")
        void searchUniqueness(@TempDir Path tempDir) throws IOException {
            Files.write(tempDir.resolve("dup.txt"), Arrays.asList("foo", "bar", "foo"));
            EditFileTool tool = new EditFileTool();
            Map<String, Object> params = new HashMap<>();
            params.put("path", "dup.txt");
            params.put("search", "foo");
            params.put("replace", "baz");
            ToolContext ctx = new ToolContext(params, tempDir);

            ToolResult r = tool.execute(ctx);
            // search "foo" 出现两次，应拒绝
            assertFalse(r.success());
        }

        @Test
        @DisplayName("search 精确匹配（空格敏感）")
        void exactMatch(@TempDir Path tempDir) throws IOException {
            Files.write(tempDir.resolve("test.txt"), Arrays.asList("hello world"));
            EditFileTool tool = new EditFileTool();
            Map<String, Object> params = new HashMap<>();
            params.put("path", "test.txt");
            params.put("search", "hello world");
            params.put("replace", "hi there");
            ToolContext ctx = new ToolContext(params, tempDir);

            ToolResult r = tool.execute(ctx);
            assertTrue(r.success());
            assertEquals("hi there", Files.readString(tempDir.resolve("test.txt")).trim());
        }

        @Test
        @DisplayName("search 找不到应报错")
        void searchNotFound(@TempDir Path tempDir) throws IOException {
            Files.write(tempDir.resolve("test.txt"), Arrays.asList("existing content"));
            EditFileTool tool = new EditFileTool();
            Map<String, Object> params = new HashMap<>();
            params.put("path", "test.txt");
            params.put("search", "nonexistent");
            params.put("replace", "replacement");
            ToolContext ctx = new ToolContext(params, tempDir);

            ToolResult r = tool.execute(ctx);
            assertFalse(r.success());
        }
    }

    @Nested
    @DisplayName("CopyFileTool 复制文件")
    class CopyFileTests {

        @Test
        @DisplayName("复制文件")
        void copyFile(@TempDir Path tempDir) throws IOException {
            Files.write(tempDir.resolve("src.txt"), "source content".getBytes());
            CopyFileTool tool = new CopyFileTool();
            Map<String, Object> params = new HashMap<>();
            params.put("source", "src.txt");
            params.put("destination", "dst.txt");
            ToolContext ctx = new ToolContext(params, tempDir);

            ToolResult r = tool.execute(ctx);
            assertTrue(r.success());
            assertTrue(Files.exists(tempDir.resolve("dst.txt")));
            assertEquals("source content", Files.readString(tempDir.resolve("dst.txt")));
        }
    }

    @Nested
    @DisplayName("GetFileInfoTool 文件信息")
    class GetFileInfoTests {

        @Test
        @DisplayName("获取文件信息")
        void getFileInfo(@TempDir Path tempDir) throws IOException {
            Files.write(tempDir.resolve("info.txt"), "data".getBytes());
            GetFileInfoTool tool = new GetFileInfoTool();
            Map<String, Object> params = new HashMap<>();
            params.put("path", "info.txt");
            ToolContext ctx = new ToolContext(params, tempDir);

            ToolResult r = tool.execute(ctx);
            assertTrue(r.success());
            assertTrue(r.text().contains("file"));
            assertTrue(r.text().contains("4")); // "data" is 4 bytes
        }

        @Test
        @DisplayName("获取目录信息")
        void getDirInfo(@TempDir Path tempDir) throws IOException {
            Files.createDirectories(tempDir.resolve("mydir"));
            GetFileInfoTool tool = new GetFileInfoTool();
            Map<String, Object> params = new HashMap<>();
            params.put("path", "mydir");
            ToolContext ctx = new ToolContext(params, tempDir);

            ToolResult r = tool.execute(ctx);
            assertTrue(r.success());
            assertTrue(r.text().contains("directory"));
        }
    }
}
