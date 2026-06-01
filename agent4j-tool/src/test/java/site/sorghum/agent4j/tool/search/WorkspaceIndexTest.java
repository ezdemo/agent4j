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
            assertTrue(matches.get(0).getContent().contains("hello"));
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
            assertTrue(matches.get(0).getFile().endsWith(".java"));
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

    // ==================== 辅助 ====================

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

    /** 创建文件并写入指定内容。 */
    private void createFiles(Path root, String path, String content) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.write(file, Arrays.asList(content.split("\n")));
    }
}
