package site.sorghum.loopra.bin.agent.bridge;

import org.junit.jupiter.api.Test;
import org.noear.solon.ai.chat.interceptor.ToolChain;
import org.noear.solon.ai.chat.interceptor.ToolRequest;
import org.noear.solon.ai.chat.tool.FunctionToolDesc;
import org.noear.solon.ai.chat.tool.ToolResult;
import org.noear.solon.core.util.RankEntity;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SampleToolInterceptorTest {

    @Test
    void deniesBlacklistedTool() throws Throwable {
        FunctionToolDesc rm = tool("rm", args -> "deleted");
        ToolChain chain = new ToolChain(
                List.of(new RankEntity<>(new SampleToolInterceptor(), 0)), rm);

        ToolResult result = chain.doIntercept(new ToolRequest(null, null, Map.of()));

        assertTrue(result.isError());
        assertTrue(result.getContent().contains("rm"));
        assertTrue(result.getContent().contains("拒绝"));
    }

    @Test
    void rewritesBashArgs() throws Throwable {
        AtomicReference<Map<String, Object>> captured = new AtomicReference<>();
        FunctionToolDesc bash = tool("bash", args -> {
            captured.set(args);
            return "ok";
        });
        ToolChain chain = new ToolChain(
                List.of(new RankEntity<>(new SampleToolInterceptor(), 0)), bash);

        ToolResult result = chain.doIntercept(
                new ToolRequest(null, null, new java.util.HashMap<>(Map.of("command", "ls -la"))));

        assertFalse(result.isError());
        assertEquals("echo [intercepted] ls -la", captured.get().get("command"));
    }

    @Test
    void passesThroughOtherTools() throws Throwable {
        AtomicReference<Map<String, Object>> captured = new AtomicReference<>();
        FunctionToolDesc read = tool("read", args -> {
            captured.set(args);
            return "contents";
        });
        ToolChain chain = new ToolChain(
                List.of(new RankEntity<>(new SampleToolInterceptor(), 0)), read);
        Map<String, Object> args = Map.of("path", "a.txt");

        ToolResult result = chain.doIntercept(new ToolRequest(null, null, args));

        assertFalse(result.isError());
        assertEquals("contents", result.getContent());
        assertEquals("a.txt", captured.get().get("path"));
    }

    @Test
    void multipleInterceptorsRunInIndexOrder() throws Throwable {
        // 两个拦截器依次改写 bash 参数，验证链式传递与顺序执行
        AtomicReference<Map<String, Object>> captured = new AtomicReference<>();
        SampleToolInterceptor first = new SampleToolInterceptor();
        SampleToolInterceptor second = new SampleToolInterceptor(Set.of()); // 不拒绝任何工具
        FunctionToolDesc bash = tool("bash", args -> {
            captured.set(args);
            return "ok";
        });
        ToolChain chain = new ToolChain(
                List.of(new RankEntity<>(first, 0), new RankEntity<>(second, 1)), bash);

        chain.doIntercept(new ToolRequest(null, null, new java.util.HashMap<>(Map.of("command", "pwd"))));

        // 第一个注入一层前缀，第二个再注入一层，参数在链间传递
        assertEquals("echo [intercepted] echo [intercepted] pwd", captured.get().get("command"));
    }

    private static FunctionToolDesc tool(String name, org.noear.solon.ai.chat.tool.ToolHandler handler) {
        return new FunctionToolDesc(name)
                .description(name)
                .inputSchema("{\"type\":\"object\"}")
                .returnType(String.class)
                .doHandle(handler);
    }
}
