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

    private int countLines(String text) {
        // 匹配行格式：文件:行号:内容 或者 "无匹配"
        if (text.contains("无匹配")) return 0;
        return (int) text.lines().filter(l -> l.contains(":")).count();
    }

    @Nested
    @DisplayName("GrepTool")
    class GrepToolTests {

        @Test
        @DisplayName("基本文本搜索")
        void shouldFindBasicText(@TempDir Path tempDir) throws IOException {
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
        @DisplayName("正则：行首锚点 ^")
        void shouldMatchLineStart(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir, "test.txt", "abc\ndef\nabc xyz");
            GrepTool tool = new GrepTool();
            Map<String, Object> params = new HashMap<>();
            params.put("pattern", "^abc");
            ToolResult r = tool.execute(new ToolContext(params, tempDir));
            assertTrue(r.success());
            assertEquals(2, countLines(r.text()));
        }

        @Test
        @DisplayName("正则：行尾锚点 $")
        void shouldMatchLineEnd(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir, "test.txt", "hello world\nfoo\nbar world");
            GrepTool tool = new GrepTool();
            Map<String, Object> params = new HashMap<>();
            params.put("pattern", "world$");
            ToolResult r = tool.execute(new ToolContext(params, tempDir));
            assertTrue(r.success());
            assertEquals(2, countLines(r.text()));
        }

        @Test
        @DisplayName("正则：字符类 \\d")
        void shouldMatchDigitClass(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir, "data.txt", "id: 42\nname: foo\nyear: 2025");
            GrepTool tool = new GrepTool();
            Map<String, Object> params = new HashMap<>();
            params.put("pattern", "\\d+");
            ToolResult r = tool.execute(new ToolContext(params, tempDir));
            assertTrue(r.success());
            assertTrue(r.text().contains("42"));
            assertTrue(r.text().contains("2025"));
        }

        @Test
        @DisplayName("正则：单词边界 \\b")
        void shouldMatchWordBoundary(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir, "test.txt", "class FooClass\nclass Foo\nFooBar");
            GrepTool tool = new GrepTool();
            Map<String, Object> params = new HashMap<>();
            params.put("pattern", "\\bFoo\\b");
            ToolResult r = tool.execute(new ToolContext(params, tempDir));
            assertTrue(r.success());
            // 只应匹配第二行 "class Foo"，不匹配 FooClass 和 FooBar
            assertEquals(1, countLines(r.text()));
        }

        @Test
        @DisplayName("正则：择一匹配 a|b")
        void shouldMatchAlternation(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir, "test.txt", "apple\nbanana\ncherry\napricot");
            GrepTool tool = new GrepTool();
            Map<String, Object> params = new HashMap<>();
            params.put("pattern", "apple|cherry");
            ToolResult r = tool.execute(new ToolContext(params, tempDir));
            assertTrue(r.success());
            assertTrue(r.text().contains("apple"));
            assertTrue(r.text().contains("cherry"));
        }

        @Test
        @DisplayName("正则：贪婪与惰性量词")
        void shouldMatchGreedyVsLazy(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir, "test.txt", "<a> <b> <c>");
            GrepTool tool = new GrepTool();
            Map<String, Object> params = new HashMap<>();
            // 匹配 < 开头 > 结尾的最短内容
            params.put("pattern", "<[^>]+>");
            ToolResult r = tool.execute(new ToolContext(params, tempDir));
            assertTrue(r.success());
            assertTrue(r.text().contains("<a>"));
        }

        @Test
        @DisplayName("正则：贪婪与惰性量词")
        void shouldMatchGreedyVsLazy2(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir, "test.txt", "xABCxDEFx");
            GrepTool tool = new GrepTool();
            Map<String, Object> params = new HashMap<>();
            params.put("pattern", "x[A-Z]+x");
            ToolResult r = tool.execute(new ToolContext(params, tempDir));
            assertTrue(r.success());
        }

        @Test
        @DisplayName("正则：中括号字符集 [abc]")
        void shouldMatchCharSet(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir, "test.txt", "cat\ndog\ncar\ncut");
            GrepTool tool = new GrepTool();
            Map<String, Object> params = new HashMap<>();
            params.put("pattern", "c[au]t");
            ToolResult r = tool.execute(new ToolContext(params, tempDir));
            assertTrue(r.success());
            assertTrue(r.text().contains("cat"));
            assertTrue(r.text().contains("cut"));
        }

        @Test
        @DisplayName("正则：零宽断言 (?=...)")
        void shouldMatchLookahead(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir, "test.txt", "foo bar\nfoo baz\nqux foo");
            GrepTool tool = new GrepTool();
            Map<String, Object> params = new HashMap<>();
            params.put("pattern", "foo(?= bar)");
            ToolResult r = tool.execute(new ToolContext(params, tempDir));
            assertTrue(r.success());
            assertTrue(r.text().contains("foo bar"));
        }

        @Test
        @DisplayName("正则：非贪婪匹配 *?")
        void shouldMatchNonGreedy(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir, "test.txt", "a\"b\"\nc\"d\"");
            GrepTool tool = new GrepTool();
            Map<String, Object> params = new HashMap<>();
            params.put("pattern", "\".*?\"");
            ToolResult r = tool.execute(new ToolContext(params, tempDir));
            assertTrue(r.success());
            // 每行一个最短引号匹配
            assertEquals(2, countLines(r.text()));
        }

        @Test
        @DisplayName("正则：Java 类名匹配")
        void shouldMatchJavaClass(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir, "Demo.java", "public class DemoController {\n    private int count;\n}");
            createFiles(tempDir, "Util.java", "public final class StringUtil {\n    public static boolean isEmpty(String s) { return s == null || s.isEmpty(); }\n}");
            GrepTool tool = new GrepTool();
            Map<String, Object> params = new HashMap<>();
            params.put("pattern", "class\\s+\\w+");
            params.put("glob", "*.java");
            ToolResult r = tool.execute(new ToolContext(params, tempDir));
            assertTrue(r.success());
            assertTrue(r.text().contains("DemoController"));
            assertTrue(r.text().contains("StringUtil"));
        }

        @Test
        @DisplayName("正则：匹配 import 语句")
        void shouldMatchImport(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir, "App.java", "import java.util.List;\nimport java.io.File;\npackage com.demo;\n\npublic class App {}");
            GrepTool tool = new GrepTool();
            Map<String, Object> params = new HashMap<>();
            params.put("pattern", "^import\\s+java\\.");
            params.put("glob", "*.java");
            ToolResult r = tool.execute(new ToolContext(params, tempDir));
            assertTrue(r.success());
            assertEquals(2, countLines(r.text()));
        }

        @Test
        @DisplayName("正则：匹配方法签名")
        void shouldMatchMethodSignature(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir, "Service.java", "public String getUserById(Long id) {\n    return \"user\";\n}\nprivate void init() {}");
            GrepTool tool = new GrepTool();
            Map<String, Object> params = new HashMap<>();
            params.put("pattern", "(public|private)\\s+\\w+\\s+\\w+\\(");
            params.put("glob", "*.java");
            ToolResult r = tool.execute(new ToolContext(params, tempDir));
            assertTrue(r.success());
            assertTrue(r.text().contains("getUserById"));
            assertTrue(r.text().contains("init"));
        }

        @Test
        @DisplayName("正则：转义特殊字符作为字面量")
        void shouldEscapeSpecialChars(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir, "expr.txt", "a + b = c\na (b) [c]\ndollar $ign");
            GrepTool tool = new GrepTool();
            Map<String, Object> params = new HashMap<>();
            params.put("pattern", "\\$ign");
            ToolResult r = tool.execute(new ToolContext(params, tempDir));
            assertTrue(r.success());
            assertTrue(r.text().contains("$ign"));
        }

        @Test
        @DisplayName("正则：匹配中文字符")
        void shouldMatchChinese(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir, "zh.txt", "你好世界\nhello world\n你好");
            GrepTool tool = new GrepTool();
            Map<String, Object> params = new HashMap<>();
            params.put("pattern", "你好");
            ToolResult r = tool.execute(new ToolContext(params, tempDir));
            assertTrue(r.success());
            assertEquals(2, countLines(r.text()));
        }

        @Test
        @DisplayName("正则：空白行匹配")
        void shouldMatchEmptyLines(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir, "test.txt", "foo\n\n\nbar\n");
            GrepTool tool = new GrepTool();
            Map<String, Object> params = new HashMap<>();
            params.put("pattern", "^$");
            ToolResult r = tool.execute(new ToolContext(params, tempDir));
            assertTrue(r.success());
        }

        @Test
        @DisplayName("无匹配应返回空结果")
        void shouldReturnEmptyWhenNoMatch(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir, "test.txt", "abc def ghi");
            GrepTool tool = new GrepTool();
            Map<String, Object> params = new HashMap<>();
            params.put("pattern", "xyz");
            ToolResult r = tool.execute(new ToolContext(params, tempDir));
            assertTrue(r.success());
            assertTrue(r.text().contains("无匹配"));
        }

        @Test
        @DisplayName("大小写敏感默认 true")
        void shouldBeCaseSensitiveByDefault(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir, "test.txt", "Hello HELLO hello");
            GrepTool tool = new GrepTool();
            Map<String, Object> params = new HashMap<>();
            params.put("pattern", "hello");
            ToolResult r = tool.execute(new ToolContext(params, tempDir));
            assertTrue(r.success());
            // 默认大小写敏感，只能匹配到小写 hello
            assertEquals(1, countLines(r.text()));
        }

        @Test
        @DisplayName("大小写不敏感应匹配所有变体")
        void shouldMatchAllCasesWhenInsensitive(@TempDir Path tempDir) throws IOException {
            // 每个单词单独一行，grep 按行返回
            createFiles(tempDir, "test.txt", "Hello\nHELLO\nhello");
            GrepTool tool = new GrepTool();
            Map<String, Object> params = new HashMap<>();
            params.put("pattern", "hello");
            params.put("caseSensitive", false);
            ToolResult r = tool.execute(new ToolContext(params, tempDir));
            assertTrue(r.success());
            assertEquals(3, countLines(r.text()));
        }

        @Test
        @DisplayName("无效正则应报错")
        void shouldFailOnInvalidRegex(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir, "test.txt", "some content");
            GrepTool tool = new GrepTool();
            Map<String, Object> params = new HashMap<>();
            params.put("pattern", "[invalid");
            ToolResult r = tool.execute(new ToolContext(params, tempDir));
            assertFalse(r.success());
        }

        @Test
        @DisplayName("多文件搜索")
        void shouldSearchMultipleFiles(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir, "a.txt", "TODO: fix me");
            createFiles(tempDir, "b.txt", "TODO: update docs");
            createFiles(tempDir, "c.txt", "done");
            GrepTool tool = new GrepTool();
            Map<String, Object> params = new HashMap<>();
            params.put("pattern", "TODO");
            params.put("glob", "*.txt");
            ToolResult r = tool.execute(new ToolContext(params, tempDir));
            assertTrue(r.success());
            assertEquals(2, countLines(r.text()));
        }

        @Test
        @DisplayName("glob 过滤应只搜索匹配的文件")
        void shouldFilterByGlob(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir, "data.txt", "secret");
            createFiles(tempDir, "data.xml", "secret");
            createFiles(tempDir, "data.json", "secret");
            GrepTool tool = new GrepTool();
            Map<String, Object> params = new HashMap<>();
            params.put("pattern", "secret");
            params.put("glob", "*.{txt,xml}");
            ToolResult r = tool.execute(new ToolContext(params, tempDir));
            assertTrue(r.success());
            assertEquals(2, countLines(r.text()));
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

    // ==================== 辅助 ====================

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
}
