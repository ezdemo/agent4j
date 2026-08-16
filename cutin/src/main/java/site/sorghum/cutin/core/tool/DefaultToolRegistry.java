package site.sorghum.cutin.core.tool;

import site.sorghum.cutin.core.context.LoopContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link ToolRegistry} 的默认内存实现，使用并发 Map 保证注册与查找的线程安全。
 */
public final class DefaultToolRegistry implements ToolRegistry {

    /** 工具 id 到工具的映射。 */
    private final Map<String, Tool> tools = new ConcurrentHashMap<>();

    /** {@inheritDoc} */
    @Override
    public void register(Tool tool) {
        tools.put(tool.id(), tool);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Tool> find(String toolId) {
        return Optional.ofNullable(tools.get(toolId));
    }

    /** {@inheritDoc} */
    @Override
    public List<ToolDefinition> definitions() {
        return tools.values().stream().map(Tool::definition).toList();
    }

    /** {@inheritDoc} */
    @Override
    public ToolResult call(ToolCall call, LoopContext context) {
        Tool tool = find(call.toolId())
            .orElseThrow(() -> new ToolNotFoundException("unknown tool: " + call.toolId()));
        return tool.call(call, context);
    }
}
