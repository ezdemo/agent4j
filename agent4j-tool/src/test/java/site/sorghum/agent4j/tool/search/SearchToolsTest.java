package site.sorghum.agent4j.tool.search;

import org.junit.jupiter.api.AfterEach;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GrepTool / GlobTool / TreeTool 集成测试。
 *
 * @author Sorghum
 */
@DisplayName("搜索工具集成测试")
class SearchToolsTest {

    @AfterEach
    void resetIndex() {
        GrepTool.clearIndexes();
    }

    @Nested
    @DisplayName("GrepTool")
    class GrepToolTests {

        @Test
        @DisplayName("应在文件中搜索到匹配")
        void shouldFindMatches(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir, "Foo.java", "class Foo {\n    Hashline hl;\n}");
            createFiles(tempDir, "Bar.java", "class Bar {}");

            GrepTool tool = new GrepTool();
            Map<String, Object> params = new HashMap<>();
            params.put("pattern", "Hashline");
            params.put("glob", "*.java");

            ToolResult result = tool.execute(new ToolContext(params, tempDir));

            assertTrue(result.success());
            assertTrue(result.text().contains("Hashline"));
        }

        @Test
        @DisplayName("应支持大小写不敏感搜索")
        void shouldSupportCaseInsensitive(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir, "test.txt", "HELLO world");

            GrepTool tool = new GrepTool();
            Map<String, Object> params = new HashMap<>();
            params.put("pattern", "hello");
            params.put("caseSensitive", false);

            ToolResult result = tool.execute(new ToolContext(params, tempDir));

            assertTrue(result.success());
            assertTrue(result.text().contains("HELLO"));
        }

        @Test
        @DisplayName("缺少 pattern 应报错")
        void shouldFailWithoutPattern() {
            GrepTool tool = new GrepTool();
            Map<String, Object> params = new HashMap<>();
            params.put("glob", "*.java");

            ToolResult result = tool.execute(new ToolContext(params));
            assertFalse(result.success());
            assertEquals("MISSING_PATTERN", result.errorCode());
        }
    }

    @Nested
    @DisplayName("GlobTool")
    class GlobToolTests {

        @Test
        @DisplayName("应匹配 Java 文件")
        void shouldMatchJavaFiles(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir, "A.java", "B.java", "C.txt");

            GlobTool tool = new GlobTool();
            Map<String, Object> params = new HashMap<>();
            params.put("pattern", "*.java");

            ToolResult result = tool.execute(new ToolContext(params, tempDir));

            assertTrue(result.success());
            assertTrue(result.text().contains(".java"));
        }

        @Test
        @DisplayName("缺少 pattern 应报错")
        void shouldFailWithoutPattern() {
            GlobTool tool = new GlobTool();
            ToolResult result = tool.execute(new ToolContext(new HashMap<>()));
            assertFalse(result.success());
        }
    }

    @Nested
    @DisplayName("TreeTool")
    class TreeToolTests {

        @Test
        @DisplayName("应生成目录树")
        void shouldGenerateTree(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir, "src/main/java/Foo.java", "pom.xml", "README.md");

            TreeTool tool = new TreeTool();
            Map<String, Object> params = new HashMap<>();
            params.put("maxDepth", 3);

            ToolResult result = tool.execute(new ToolContext(params, tempDir));

            assertTrue(result.success());
            assertTrue(result.text().contains("pom.xml"));
            assertTrue(result.text().contains("src/"));
            assertTrue(result.text().contains("个文件"));
        }
    }

    @Nested
    @DisplayName("共享索引验证")
    class SharedIndex {

        @Test
        @DisplayName("GrepTool 和 GlobTool 应共享同一索引")
        void shouldShareIndex(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir, "Test.java", "hello");

            // 用 GrepTool 建立索引
            GrepTool grep = new GrepTool();
            Map<String, Object> grepParams = new HashMap<>();
            grepParams.put("pattern", "hello");
            grep.execute(new ToolContext(grepParams, tempDir));

            // GlobTool 应复用
            GlobTool glob = new GlobTool();
            Map<String, Object> globParams = new HashMap<>();
            globParams.put("pattern", "*.java");

            ToolResult result = glob.execute(new ToolContext(globParams, tempDir));
            assertTrue(result.success());
            assertTrue(result.text().contains("Test.java"));
        }
    }

    // ==================== 辅助 ====================

    private void createFiles(Path root, String... paths) throws IOException {
        for (String path : paths) {
            Path file = root.resolve(path);
            Files.createDirectories(file.getParent());
            Files.write(file, Arrays.asList("test content for " + path));
        }
    }

    private void createFiles(Path root, String path, String content) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.write(file, Arrays.asList(content.split("\n")));
    }
}
