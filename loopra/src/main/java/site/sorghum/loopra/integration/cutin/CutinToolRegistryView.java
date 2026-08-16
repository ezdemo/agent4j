package site.sorghum.loopra.integration.cutin;

import site.sorghum.cutin.core.context.LoopContext;
import site.sorghum.cutin.core.tool.*;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
  * 基于 Loopra 工具表的实时 cutin {@link ToolRegistry} 视图。
 * <p>
  * Loopra 的 {@code ToolRegistry} 持有 Solon 工具定义，并把每次注册/刷新
  * 同步进该视图；Loopra 热刷新内容时，视图在 cutin 引擎内保持稳定。
 * </p>
 */
public final class CutinToolRegistryView implements ToolRegistry, ToolProvider {

    private final ConcurrentHashMap<String, Tool> tools = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Tool> ordered = new CopyOnWriteArrayList<>();

    public void setTools(Collection<? extends Tool> newTools) {
        tools.clear();
        ordered.clear();
        for (Tool tool : newTools) {
            Tool previous = tools.putIfAbsent(tool.id(), tool);
            if (previous == null) {
                ordered.add(tool);
            }
        }
    }

    public void remove(String toolId) {
        Tool removed = tools.remove(toolId);
        if (removed != null) {
            ordered.remove(removed);
        }
    }

    public Collection<Tool> toolValues() {
        return List.copyOf(ordered);
    }

    @Override
    public String id() {
        return "loopra-tools";
    }

    @Override
    public List<Tool> tools() {
        return List.copyOf(ordered);
    }

    @Override
    public void register(Tool tool) {
        Tool previous = tools.putIfAbsent(tool.id(), tool);
        if (previous == null) {
            ordered.add(tool);
        }
    }

    @Override
    public Optional<Tool> find(String toolId) {
        return Optional.ofNullable(tools.get(toolId));
    }

    @Override
    public List<ToolDefinition> definitions() {
        return ordered.stream().map(Tool::definition).toList();
    }

    @Override
    public ToolResult call(ToolCall call, LoopContext context) {
        Tool tool = find(call.toolId())
            .orElseThrow(() -> new ToolNotFoundException("unknown tool: " + call.toolId()));
        return tool.call(call, context);
    }
}
