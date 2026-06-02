package site.sorghum.agent4j.tool.terminal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CommandTokenizer} 单元测试——Shell 命令分词器。
 *
 * @author Sorghum
 */
@DisplayName("CommandTokenizer 命令分词测试")
class CommandTokenizerTest {

    @Test
    @DisplayName("简单命令")
    void simpleCommand() {
        List<String> tokens = CommandTokenizer.tokenize("ls -la");
        assertEquals(List.of("ls", "-la"), tokens);
    }

    @Test
    @DisplayName("带引号的参数")
    void quotedArgs() {
        List<String> tokens = CommandTokenizer.tokenize("echo \"hello world\"");
        assertEquals(List.of("echo", "hello world"), tokens);
    }

    @Test
    @DisplayName("单引号参数")
    void singleQuotedArgs() {
        List<String> tokens = CommandTokenizer.tokenize("echo 'hello world'");
        assertEquals(List.of("echo", "hello world"), tokens);
    }

    @Test
    @DisplayName("混合引号")
    void mixedQuotes() {
        List<String> tokens = CommandTokenizer.tokenize("git commit -m \"fix: resolve bug\"");
        assertEquals(List.of("git", "commit", "-m", "fix: resolve bug"), tokens);
    }

    @Test
    @DisplayName("管道命令")
    void pipeCommand() {
        List<String> tokens = CommandTokenizer.tokenize("grep foo | wc -l");
        assertEquals(List.of("grep", "foo", "|", "wc", "-l"), tokens);
    }

    @Test
    @DisplayName("重定向")
    void redirect() {
        List<String> tokens = CommandTokenizer.tokenize("cat input.txt > output.txt");
        assertEquals(List.of("cat", "input.txt", ">", "output.txt"), tokens);
    }

    @Test
    @DisplayName("追加重定向")
    void appendRedirect() {
        List<String> tokens = CommandTokenizer.tokenize("echo log >> file.log");
        assertEquals(List.of("echo", "log", ">>", "file.log"), tokens);
    }

    @Test
    @DisplayName("链式命令 &&")
    void chainAnd() {
        List<String> tokens = CommandTokenizer.tokenize("cd dir && npm install");
        assertEquals(List.of("cd", "dir", "&&", "npm", "install"), tokens);
    }

    @Test
    @DisplayName("链式命令 ||")
    void chainOr() {
        List<String> tokens = CommandTokenizer.tokenize("make || echo failed");
        assertEquals(List.of("make", "||", "echo", "failed"), tokens);
    }

    @Test
    @DisplayName("分号分隔")
    void semicolon() {
        List<String> tokens = CommandTokenizer.tokenize("echo a; echo b");
        // 分号附在前一个 token 上
        assertEquals(List.of("echo", "a;", "echo", "b"), tokens);
    }

    @Test
    @DisplayName("反斜杠转义空格（\\空格 → 字面量空格）")
    void escapedSpace() {
        List<String> tokens = CommandTokenizer.tokenize("echo hello\\ world");
        assertEquals(List.of("echo", "hello world"), tokens);
    }

    @Test
    @DisplayName("多个连续空格")
    void multipleSpaces() {
        List<String> tokens = CommandTokenizer.tokenize("git   status");
        assertEquals(List.of("git", "status"), tokens);
    }

    @Test
    @DisplayName("空命令")
    void emptyCommand() {
        List<String> tokens = CommandTokenizer.tokenize("");
        assertTrue(tokens.isEmpty());
    }

    @Test
    @DisplayName("只有空格")
    void onlySpaces() {
        List<String> tokens = CommandTokenizer.tokenize("   ");
        assertTrue(tokens.isEmpty());
    }

    @Test
    @DisplayName("Maven 命令")
    void mavenCommand() {
        List<String> tokens = CommandTokenizer.tokenize("mvn clean install -DskipTests");
        assertEquals(List.of("mvn", "clean", "install", "-DskipTests"), tokens);
    }

    @Test
    @DisplayName("npm 命令带参数")
    void npmCommand() {
        List<String> tokens = CommandTokenizer.tokenize("npm run build -- --prod");
        assertEquals(List.of("npm", "run", "build", "--", "--prod"), tokens);
    }

    @Test
    @DisplayName("git 命令")
    void gitCommand() {
        List<String> tokens = CommandTokenizer.tokenize("git log --oneline -5");
        assertEquals(List.of("git", "log", "--oneline", "-5"), tokens);
    }

    @Test
    @DisplayName("带空引号")
    void emptyQuotes() {
        List<String> tokens = CommandTokenizer.tokenize("echo \"\"");
        // 空引号被丢弃
        assertEquals(List.of("echo"), tokens);
    }

    @Test
    @DisplayName("JDK 命令")
    void javaCommand() {
        List<String> tokens = CommandTokenizer.tokenize("java -jar app.jar --server.port=8080");
        assertEquals(List.of("java", "-jar", "app.jar", "--server.port=8080"), tokens);
    }
}
