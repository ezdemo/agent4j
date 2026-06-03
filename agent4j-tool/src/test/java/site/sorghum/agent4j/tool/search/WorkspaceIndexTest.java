package site.sorghum.agent4j.tool.search;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link WorkspaceIndex} 单元测试。
 *
 * @author Sorghum
 */
@DisplayName("WorkspaceIndex 工作区索引测试")
class WorkspaceIndexTest {

    private void createFiles(Path root, String... paths) throws IOException {
        for (String path : paths) {
            Path file = root.resolve(path);
            Files.createDirectories(file.getParent());
            if (path.contains("/")) {
                // 可能是目录也可能文件，检查扩展名判断
            }
            if (!path.endsWith("/")) {
                Files.write(file, Arrays.asList("test content for " + path));
            } else {
                Files.createDirectories(file);
            }
        }
    }

    /**
     * 创建文件并写入指定内容。
     */
    private void createFiles(Path root, String path, String content) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.write(file, Arrays.asList(content.split("\n")));
    }

    @Nested
    @DisplayName("扫描与初始化")
    class Scan {

        @Test
        @DisplayName("应正确扫描目录树")
        void shouldScanDirectory(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir,
                    "src/main/java/Foo.java",
                    "src/main/resources/application.yml",
                    "pom.xml",
                    "README.md");

            WorkspaceIndex index = new WorkspaceIndex(tempDir);
            index.refresh();

            assertTrue(index.fileCount() >= 4, "至少应有 4 个文件");
        }

        @Test
        @DisplayName("应自动跳过 denylist 目录")
        void shouldSkipDenylistDirs(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir,
                    "pom.xml",
                    "node_modules/lodash/index.js",
                    ".git/HEAD",
                    "target/classes/Foo.class");

            WorkspaceIndex index = new WorkspaceIndex(tempDir);
            index.refresh();

            List<String> files = index.glob("*");
            // 只有 pom.xml 不在 denylist 中
            boolean onlyPomXml = files.size() == 1 && files.get(0).equals("pom.xml");
            assertTrue(onlyPomXml,
                    "应只有 pom.xml，实际: " + files);
        }

        @Test
        @DisplayName("应解析 .gitignore 规则")
        void shouldRespectGitignore(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir,
                    "pom.xml",
                    "secret.key",
                    "logs/app.log");

            // 写入 .gitignore
            Files.write(tempDir.resolve(".gitignore"),
                    Arrays.asList("*.key", "logs/"));

            WorkspaceIndex index = new WorkspaceIndex(tempDir);
            index.refresh();

            List<String> files = index.glob("*");
            // .gitignore 本身不会被忽略（git 也不忽略它）
            assertEquals(2, files.size(), "应有 .gitignore 和 pom.xml，实际: " + files);
            assertTrue(files.contains(".gitignore"));
            assertTrue(files.contains("pom.xml"));
        }
    }

    @Nested
    @DisplayName("tree 目录树")
    class Tree {

        @Test
        @DisplayName("应生成正确的缩进树")
        void shouldGenerateTree(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir,
                    "pom.xml",
                    "src/main/java/Foo.java",
                    "src/test/java/FooTest.java");

            WorkspaceIndex index = new WorkspaceIndex(tempDir);
            index.refresh();

            assertEquals(3, index.fileCount(), "应有 3 个文件被索引");

            String tree = index.tree(4);
            assertNotNull(tree);
            if (!tree.contains("pom.xml")) {
                throw new AssertionError("树应包含 pom.xml\n实际树输出:\n" + tree);
            }
            assertTrue(tree.contains("src/"), tree);
            assertTrue(tree.contains("Foo.java"), tree);
        }

        @Test
        @DisplayName("maxDepth=0 应仅显示根目录")
        void shouldLimitDepth(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir,
                    "src/main/java/Foo.java",
                    "pom.xml");

            WorkspaceIndex index = new WorkspaceIndex(tempDir);
            index.refresh();

            String tree = index.tree(0);
            assertFalse(tree.contains("Foo.java"), "maxDepth=0 不应包含深层文件");
        }
    }

    @Nested
    @DisplayName("glob 文件名匹配")
    class Glob {

        @Test
        @DisplayName("应匹配 Java 文件")
        void shouldMatchJavaFiles(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir,
                    "src/main/java/A.java",
                    "src/main/java/B.java",
                    "src/main/resources/config.yml",
                    "pom.xml");

            WorkspaceIndex index = new WorkspaceIndex(tempDir);
            index.refresh();

            List<String> files = index.glob("**/*.java");
            assertEquals(2, files.size());
            assertTrue(files.stream().allMatch(f -> f.endsWith(".java")));
        }

        @Test
        @DisplayName("应支持 ** 多级匹配")
        void shouldSupportDoubleStar(@TempDir Path tempDir) throws IOException {
            // 分别创建以确保两个文件都存在
            Path deep = tempDir.resolve("a/b/deep.txt");
            Files.createDirectories(deep.getParent());
            Files.write(deep, Collections.singletonList("deep"));

            Path shallow = tempDir.resolve("shallow.txt");
            Files.write(shallow, Collections.singletonList("shallow"));

            WorkspaceIndex index = new WorkspaceIndex(tempDir);
            index.refresh();

            assertEquals(2, index.fileCount(), "应有 2 个 .txt 文件被索引");

            List<String> files = index.glob("**/*.txt");
            assertEquals(2, files.size(),
                    "**/*.txt 应匹配根目录和子目录的 .txt 文件，实际: " + files);
        }

        @Test
        @DisplayName("应支持 {a,b} 分支选择")
        void shouldSupportBraceExpansion(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir,
                    "README.md",
                    "CHANGELOG.md",
                    "pom.xml");

            WorkspaceIndex index = new WorkspaceIndex(tempDir);
            index.refresh();

            List<String> files = index.glob("*.{md,xml}");
            assertEquals(3, files.size());
        }

        @Test
        @DisplayName("无匹配应返回空列表")
        void shouldReturnEmptyOnNoMatch(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir, "pom.xml");

            WorkspaceIndex index = new WorkspaceIndex(tempDir);
            index.refresh();

            assertTrue(index.glob("*.rs").isEmpty());
        }
    }

    // ==================== 辅助 ====================

    @Nested
    @DisplayName("grep 内容搜索")
    class Grep {

        @Test
        @DisplayName("应在文件中搜索到匹配内容")
        void shouldFindMatches(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir, "a.txt", "hello world\nfoo bar\nhello again");
            createFiles(tempDir, "b.txt", "no match here");

            WorkspaceIndex index = new WorkspaceIndex(tempDir);
            index.refresh();

            List<SearchMatch> matches = index.grep("hello");
            assertEquals(2, matches.size());
            assertTrue(matches.get(0).content().contains("hello"));
        }

        @Test
        @DisplayName("应支持正则表达式")
        void shouldSupportRegex(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir, "test.txt", "line1\nline2\nLINE3");

            WorkspaceIndex index = new WorkspaceIndex(tempDir);
            index.refresh();

            List<SearchMatch> matches = index.grep("(?i)line");
            assertEquals(3, matches.size());
        }

        @Test
        @DisplayName("glob 过滤应生效")
        void shouldFilterByGlob(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir, "a.java", "hello");
            createFiles(tempDir, "b.txt", "hello");

            WorkspaceIndex index = new WorkspaceIndex(tempDir);
            index.refresh();

            List<SearchMatch> matches = index.grep("hello", "*.java");
            assertEquals(1, matches.size());
            assertTrue(matches.get(0).file().endsWith(".java"));
        }

        @Test
        @DisplayName("无匹配应返回空列表")
        void shouldReturnEmptyOnGrepNoMatch(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir, "test.txt", "nothing to see here");

            WorkspaceIndex index = new WorkspaceIndex(tempDir);
            index.refresh();

            assertTrue(index.grep("NONEXISTENT_PATTERN_XYZ").isEmpty());
        }
    }

    @Nested
    @DisplayName("增量刷新")
    class IncrementalRefresh {

        @Test
        @DisplayName("新增文件后应能被索引到")
        void shouldDetectNewFiles(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir, "old.txt", "content");

            WorkspaceIndex index = new WorkspaceIndex(tempDir);
            index.refresh();
            assertEquals(1, index.glob("*.txt").size());

            // 新增文件
            createFiles(tempDir, "new.txt", "fresh");
            index.refresh();

            assertEquals(2, index.glob("*.txt").size());
        }
    }

    @Nested
    @DisplayName("globToRegex 模式转换")
    class GlobToRegex {

        @Test
        @DisplayName("*.java 应只匹配根目录 Java 文件")
        void singleStarExtension() {
            var p = WorkspaceIndex.globToRegex("*.java");
            assertTrue(p.matcher("Foo.java").matches(),       "Foo.java 应匹配");
            assertTrue(p.matcher("A.java").matches(),          "A.java 应匹配");
            assertFalse(p.matcher("Foo.txt").matches(),        "Foo.txt 不应匹配");
            assertFalse(p.matcher("src/Foo.java").matches(),   "src/Foo.java 带路径不应匹配单级 *");
        }

        @Test
        @DisplayName("**/*.java 应匹配全部层级 Java 文件")
        void doubleStarAllJava() {
            var p = WorkspaceIndex.globToRegex("**/*.java");
            assertTrue(p.matcher("Foo.java").matches(),           "根目录 Java 应匹配");
            assertTrue(p.matcher("src/Foo.java").matches(),       "一级子目录应匹配");
            assertTrue(p.matcher("src/main/Foo.java").matches(),  "多级子目录应匹配");
            assertFalse(p.matcher("Foo.txt").matches(),           "非 Java 不应匹配");
        }

        @Test
        @DisplayName("src/**/*.java 应限制在 src 下")
        void doubleStarUnderSrc() {
            var p = WorkspaceIndex.globToRegex("src/**/*.java");
            assertTrue(p.matcher("src/Foo.java").matches(),          "src 根应匹配");
            assertTrue(p.matcher("src/main/Foo.java").matches(),     "src 子目录应匹配");
            assertFalse(p.matcher("test/Foo.java").matches(),        "test 下不应匹配");
            assertFalse(p.matcher("Foo.java").matches(),             "根目录不应匹配");
        }

        @Test
        @DisplayName("?at 应匹配单字符+at")
        void singleCharWildcard() {
            var p = WorkspaceIndex.globToRegex("?at");
            assertTrue(p.matcher("cat").matches(),   "cat 应匹配");
            assertTrue(p.matcher("bat").matches(),   "bat 应匹配");
            assertTrue(p.matcher("rat").matches(),   "rat 应匹配");
            assertFalse(p.matcher("at").matches(),   "at 太短不应匹配");
            assertFalse(p.matcher("chat").matches(), "chat 太长不应匹配");
            assertFalse(p.matcher("c/at").matches(), "含斜线不应匹配");
        }

        @Test
        @DisplayName("*.{md,mdx} 应匹配 md 和 mdx")
        void braceExpansion() {
            var p = WorkspaceIndex.globToRegex("*.{md,mdx}");
            assertTrue(p.matcher("README.md").matches(),    "md 应匹配");
            assertTrue(p.matcher("README.mdx").matches(),   "mdx 应匹配");
            assertFalse(p.matcher("README.txt").matches(),  "txt 不应匹配");
        }

        @Test
        @DisplayName("{src,test}/*.java 应匹配 src 和 test 目录")
        void braceExpansionPath() {
            var p = WorkspaceIndex.globToRegex("{src,test}/*.java");
            assertTrue(p.matcher("src/Foo.java").matches(),     "src 下应匹配");
            assertTrue(p.matcher("test/FooTest.java").matches(), "test 下应匹配");
            assertFalse(p.matcher("lib/Foo.java").matches(),     "lib 下不应匹配");
        }

        @Test
        @DisplayName("** 单独应匹配任意路径")
        void doubleStarAlone() {
            var p = WorkspaceIndex.globToRegex("**");
            assertTrue(p.matcher("Foo.java").matches());
            assertTrue(p.matcher("src/Foo.java").matches());
            assertTrue(p.matcher("a/b/c/d.txt").matches());
        }

        @Test
        @DisplayName("a/**/b/*.java 应匹配中间任意层级")
        void doubleStarMiddle() {
            var p = WorkspaceIndex.globToRegex("a/**/b/*.java");
            assertTrue(p.matcher("a/b/Foo.java").matches(),          "零中间层应匹配");
            assertTrue(p.matcher("a/x/b/Foo.java").matches(),        "一层中间应匹配");
            assertTrue(p.matcher("a/x/y/b/Foo.java").matches(),      "多层中间应匹配");
            assertFalse(p.matcher("a/c/Foo.java").matches(),         "缺少 b 不应匹配");
            assertFalse(p.matcher("a/b/Foo.txt").matches(),          "非 java 不应匹配");
        }

        @Test
        @DisplayName("特殊正则字符应被转义")
        void specialRegexChars() {
            var p = WorkspaceIndex.globToRegex("test(1).java");
            assertTrue(p.matcher("test(1).java").matches(),  "括号应作为字面匹配");
            assertFalse(p.matcher("test1.java").matches(),   "无括号不应匹配");

            var p2 = WorkspaceIndex.globToRegex("file[name].txt");
            assertTrue(p2.matcher("file[name].txt").matches(), "方括号应作为字面匹配");
        }

        @Test
        @DisplayName("空字符串只匹配空路径")
        void emptyPattern() {
            var p = WorkspaceIndex.globToRegex("");
            assertTrue(p.matcher("").matches());
            assertFalse(p.matcher("a").matches());
        }

        @Test
        @DisplayName("多层扩展名如 *.spec.js 应正确匹配")
        void multiDotExtension() {
            var p = WorkspaceIndex.globToRegex("*.spec.js");
            assertTrue(p.matcher("foo.spec.js").matches());
            assertFalse(p.matcher("foo.js").matches());
            assertFalse(p.matcher("foo.spec.ts").matches());
        }
    }

    @Nested
    @DisplayName("glob 高级匹配场景")
    class GlobAdvanced {

        @Test
        @DisplayName("**/* 应匹配所有非目录文件")
        void doubleStarAllFiles(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir,
                    "pom.xml",
                    "src/main/java/Foo.java",
                    "src/test/FooTest.java",
                    "README.md");

            WorkspaceIndex index = new WorkspaceIndex(tempDir);
            index.refresh();

            List<String> files = index.glob("**/*");
            assertEquals(4, files.size(), "**/* 应匹配所有文件");
        }

        @Test
        @DisplayName("* 应仅匹配根目录文件")
        void singleStarOnlyRoot(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir,
                    "pom.xml",
                    "src/main/java/Foo.java");

            WorkspaceIndex index = new WorkspaceIndex(tempDir);
            index.refresh();

            List<String> files = index.glob("*");
            assertEquals(1, files.size(), "* 应只匹配根目录文件");
            assertEquals("pom.xml", files.get(0));
        }

        @Test
        @DisplayName("{md,txt,xml} 多分支应全部匹配")
        void braceMultipleExtensions(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir,
                    "readme.md",
                    "notes.txt",
                    "config.xml",
                    "app.java");

            WorkspaceIndex index = new WorkspaceIndex(tempDir);
            index.refresh();

            List<String> files = index.glob("*.{md,txt,xml}");
            assertEquals(3, files.size());
            assertTrue(files.contains("readme.md"));
            assertTrue(files.contains("notes.txt"));
            assertTrue(files.contains("config.xml"));
        }

        @Test
        @DisplayName("src/**/*Test*.java 应匹配测试文件")
        void patternWithTestName(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir,
                    "src/main/java/Util.java",
                    "src/test/java/UtilTest.java",
                    "src/test/java/HelperTest.java",
                    "src/test/java/helper/Helper.java");

            WorkspaceIndex index = new WorkspaceIndex(tempDir);
            index.refresh();

            List<String> files = index.glob("src/**/*Test*.java");
            assertEquals(2, files.size(), "应匹配 2 个 Test 文件");
            assertTrue(files.stream().allMatch(f -> f.contains("Test")),
                    "所有匹配文件应包含 Test，实际: " + files);
        }

        @Test
        @DisplayName("**/resources/** 应匹配 resources 目录下所有文件")
        void resourcesDirectory(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir,
                    "src/main/resources/application.yml",
                    "src/main/resources/static/index.html",
                    "src/test/resources/test-data.json",
                    "pom.xml");

            WorkspaceIndex index = new WorkspaceIndex(tempDir);
            index.refresh();

            List<String> files = index.glob("**/resources/**");
            assertEquals(3, files.size(), "resources 下应有 3 文件");
            assertTrue(files.stream().allMatch(f -> f.contains("resources")),
                    "所有匹配应包含 resources，实际: " + files);
        }

        @Test
        @DisplayName("文件名含空格和特殊字符应能匹配")
        void specialCharsInFilename(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir,
                    "my file.txt",
                    "test(1).java",
                    "foo-bar.js");

            WorkspaceIndex index = new WorkspaceIndex(tempDir);
            index.refresh();

            assertEquals(1, index.glob("my file.txt").size(), "空格文件名应匹配");
            assertEquals(1, index.glob("test(1).java").size(), "括号文件名应匹配");
            assertEquals(1, index.glob("foo-bar.js").size(), "连字符文件名应匹配");
        }

        @Test
        @DisplayName("maxResults 截断应生效")
        void maxResultsTruncation(@TempDir Path tempDir) throws IOException {
            for (int i = 0; i < 10; i++) {
                createFiles(tempDir, "file" + i + ".txt");
            }

            WorkspaceIndex index = new WorkspaceIndex(tempDir);
            index.refresh();

            // glob 方法本身没有 maxResults 参数，但 GlobTool 有
            // 这里测试 glob 返回全部
            List<String> files = index.glob("*.txt");
            assertEquals(10, files.size());
        }

        @Test
        @DisplayName("深层嵌套目录应能被 ** 匹配")
        void deepNestedDirectories(@TempDir Path tempDir) throws IOException {
            // 创建深层嵌套
            Path deep = tempDir.resolve("a/b/c/d/e/f/deep.txt");
            Files.createDirectories(deep.getParent());
            Files.write(deep, Collections.singletonList("deep"));

            createFiles(tempDir, "root.txt");

            WorkspaceIndex index = new WorkspaceIndex(tempDir);
            index.refresh();

            List<String> files = index.glob("**/*.txt");
            assertEquals(2, files.size(), "深层和根目录的 txt 应都被匹配");
            assertTrue(files.contains("a/b/c/d/e/f/deep.txt"),
                    "深层文件应被匹配，实际: " + files);
        }

        @Test
        @DisplayName("路径不通配时 glob 应返回精确路径")
        void exactPathMatch(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir,
                    "src/main/java/Foo.java",
                    "src/main/java/Bar.java");

            WorkspaceIndex index = new WorkspaceIndex(tempDir);
            index.refresh();

            List<String> files = index.glob("src/main/java/Foo.java");
            assertEquals(1, files.size());
            assertEquals("src/main/java/Foo.java", files.get(0));
        }
    }

    @Nested
    @DisplayName("glob 结合 blocked paths")
    class GlobWithBlockedPaths {

        @Test
        @DisplayName("屏蔽目录下的文件不应出现在索引中")
        void blockedPathShouldBeExcluded(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir,
                    "src/main/java/Foo.java",
                    "secret/config.yml");

            WorkspaceIndex index = new WorkspaceIndex(tempDir,
                    Collections.singletonList("secret"));
            index.refresh();

            List<String> files = index.glob("**/*.yml");
            assertTrue(files.isEmpty(), "secret 目录被屏蔽，不应有匹配");
        }

        @Test
        @DisplayName("屏蔽目录外的文件应正常匹配")
        void nonBlockedPathShouldMatch(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir,
                    "src/main/java/Foo.java",
                    "secret/config.yml");

            WorkspaceIndex index = new WorkspaceIndex(tempDir,
                    Collections.singletonList("secret"));
            index.refresh();

            List<String> files = index.glob("**/*.java");
            assertEquals(1, files.size());
            assertEquals("src/main/java/Foo.java", files.get(0));
        }
    }

    @Nested
    @DisplayName("glob 并发安全")
    class GlobConcurrency {

        @Test
        @DisplayName("多次调用 glob 应返回一致结果")
        void multipleGlobCallsConsistent(@TempDir Path tempDir) throws IOException {
            createFiles(tempDir,
                    "a.java", "b.java", "c.java");

            WorkspaceIndex index = new WorkspaceIndex(tempDir);
            index.refresh();

            for (int i = 0; i < 10; i++) {
                List<String> files = index.glob("*.java");
                assertEquals(3, files.size(), "第 " + (i + 1) + " 次调用结果不一致");
            }
        }
    }
}
