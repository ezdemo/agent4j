package site.sorghum.agent4j.tool.terminal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CommandChainParser} 单元测试——命令链解析器。
 *
 * @author Sorghum
 */
@DisplayName("CommandChainParser 命令链解析测试")
class CommandChainParserTest {

    @Test
    @DisplayName("简单命令（无操作符时返回 null）")
    void simpleCommand() throws Exception {
        var chain = CommandChainParser.parse("ls -la");
        // 无操作符时返回 null
        assertNull(chain);
    }

    @Test
    @DisplayName("管道连接")
    void pipeCommand() throws Exception {
        var chain = CommandChainParser.parse("grep foo | wc -l");
        assertNotNull(chain);
        assertEquals(2, chain.segments().size());
        assertEquals(1, chain.ops().size());
        assertEquals(CommandChainParser.ChainOp.PIPE, chain.ops().get(0));
    }

    @Test
    @DisplayName("&& 链")
    void andChain() throws Exception {
        // cd 会被拦截，用 git -C 代替
        var chain = CommandChainParser.parse("make && make test");
        assertNotNull(chain);
        assertEquals(2, chain.segments().size());
        assertEquals(CommandChainParser.ChainOp.AND, chain.ops().get(0));
    }

    @Test
    @DisplayName("|| 链")
    void orChain() throws Exception {
        var chain = CommandChainParser.parse("make || echo failed");
        assertNotNull(chain);
        assertEquals(2, chain.segments().size());
        assertEquals(CommandChainParser.ChainOp.OR, chain.ops().get(0));
    }

    @Test
    @DisplayName("分号分隔")
    void semicolonChain() throws Exception {
        // 分号被 tokenizer 保留在 token 内，不被解析为链操作符
        var chain = CommandChainParser.parse("echo a ; echo b");
        assertNotNull(chain);
        assertEquals(2, chain.segments().size());
        assertEquals(CommandChainParser.ChainOp.SEMI, chain.ops().get(0));
    }

    @Test
    @DisplayName("三连链: && ||")
    void tripleChain() throws Exception {
        var chain = CommandChainParser.parse("make && make test || echo failed");
        assertNotNull(chain);
        assertEquals(3, chain.segments().size());
        assertEquals(2, chain.ops().size());
        assertEquals(CommandChainParser.ChainOp.AND, chain.ops().get(0));
        assertEquals(CommandChainParser.ChainOp.OR, chain.ops().get(1));
    }

    @Test
    @DisplayName("复杂管道链")
    void complexPipeChain() throws Exception {
        var chain = CommandChainParser.parse("cat data.txt | grep error | sort | uniq -c");
        assertNotNull(chain);
        assertEquals(4, chain.segments().size());
        assertEquals(3, chain.ops().size());
        for (var op : chain.ops()) {
            assertEquals(CommandChainParser.ChainOp.PIPE, op);
        }
    }

    @Test
    @DisplayName("重定向输出")
    void redirectOut() throws Exception {
        var chain = CommandChainParser.parse("echo hello > output.txt");
        assertNotNull(chain);
        assertEquals(1, chain.segments().size());
        var seg = chain.segments().get(0);
        assertEquals(1, seg.redirects().size());
        assertEquals(CommandChainParser.RedirectKind.OUT, seg.redirects().get(0).kind());
        assertEquals("output.txt", seg.redirects().get(0).target());
    }

    @Test
    @DisplayName("重定向追加")
    void redirectAppend() throws Exception {
        var chain = CommandChainParser.parse("echo log >> file.log");
        assertNotNull(chain);
        var seg = chain.segments().get(0);
        assertEquals(CommandChainParser.RedirectKind.APPEND, seg.redirects().get(0).kind());
    }

    @Test
    @DisplayName("输入重定向")
    void redirectIn() throws Exception {
        var chain = CommandChainParser.parse("sort < input.txt");
        assertNotNull(chain);
        var seg = chain.segments().get(0);
        assertEquals(CommandChainParser.RedirectKind.IN, seg.redirects().get(0).kind());
        assertEquals("input.txt", seg.redirects().get(0).target());
    }

    @Test
    @DisplayName("错误重定向")
    void redirectErr() throws Exception {
        var chain = CommandChainParser.parse("grep foo 2> error.log");
        assertNotNull(chain);
        var seg = chain.segments().get(0);
        assertEquals(CommandChainParser.RedirectKind.ERR_OUT, seg.redirects().get(0).kind());
    }

    @Test
    @DisplayName("错误合并 2>&1")
    void redirectErrMerge() throws Exception {
        var chain = CommandChainParser.parse("cmd 2>&1");
        assertNotNull(chain);
        var seg = chain.segments().get(0);
        assertEquals(CommandChainParser.RedirectKind.ERR_MERGE, seg.redirects().get(0).kind());
    }

    @Test
    @DisplayName("混合管道和重定向")
    void pipeWithRedirect() throws Exception {
        var chain = CommandChainParser.parse("cat log.txt | grep error > errors.txt");
        assertNotNull(chain);
        assertEquals(2, chain.segments().size());
        assertEquals(1, chain.ops().size());
        assertEquals(CommandChainParser.ChainOp.PIPE, chain.ops().get(0));
        // 第二段有重定向
        assertEquals(1, chain.segments().get(1).redirects().size());
        assertEquals("errors.txt", chain.segments().get(1).redirects().get(0).target());
    }

    @Test
    @DisplayName("不支持后台运行 &")
    void backgroundNotSupported() {
        assertThrows(CommandChainParser.UnsupportedSyntaxException.class,
                () -> CommandChainParser.parse("cmd &"));
    }

    @Test
    @DisplayName("不支持 heredoc <<")
    void heredocNotSupported() {
        assertThrows(CommandChainParser.UnsupportedSyntaxException.class,
                () -> CommandChainParser.parse("cmd << EOF"));
    }
}
