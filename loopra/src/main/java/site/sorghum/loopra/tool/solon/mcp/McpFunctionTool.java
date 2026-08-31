package site.sorghum.loopra.tool.solon.mcp;

import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.ai.chat.tool.ToolCallResultConverter;
import org.noear.solon.ai.chat.tool.ToolResult;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * 标记并包装 MCP 网关产生的工具。
 *
 * <p>MCP 工具的参数会直接发送给远端服务器，不能混入 Loopra 内部的
 * {@code ctx}/{@code __cwd} 运行时对象。保留一个明确的类型标记，供
 * Loopra 与 cutin 的桥接层区分外部 MCP 工具和本地 FunctionTool。</p>
 */
public final class McpFunctionTool implements FunctionTool {

    private final FunctionTool delegate;

    public McpFunctionTool(FunctionTool delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public String type() {
        return delegate.type();
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public String title() {
        return delegate.title();
    }

    @Override
    public String description() {
        return delegate.description();
    }

    @Override
    public Map<String, Object> meta() {
        return delegate.meta();
    }

    @Override
    public void metaPut(String key, Object value) {
        delegate.metaPut(key, value);
    }

    @Override
    public boolean returnDirect() {
        return delegate.returnDirect();
    }

    @Override
    public String inputSchema() {
        return delegate.inputSchema();
    }

    @Override
    public String outputSchema() {
        return delegate.outputSchema();
    }

    @Override
    public Type returnType() {
        return delegate.returnType();
    }

    @Override
    public ToolCallResultConverter resultConverter() {
        return delegate.resultConverter();
    }

    @Override
    public Object handle(Map<String, Object> args) throws Throwable {
        return delegate.handle(args);
    }

    @Override
    public CompletableFuture<Object> handleAsync(Map<String, Object> args) {
        return delegate.handleAsync(args);
    }

    @Override
    public ToolResult call(Map<String, Object> args) throws Throwable {
        return delegate.call(args);
    }
}
