package site.sorghum.cutin.core.tool;

import site.sorghum.cutin.core.context.LoopContext;

import java.util.List;
import java.util.Optional;

/**
 * 工具注册表 SPI：管理工具的注册、查找、定义列举与调用。
 */
public interface ToolRegistry {

    /** 注册一个工具；相同 id 的旧工具会被覆盖。 */
    void register(Tool tool);

    /** 按工具 id 查找工具。 */
    Optional<Tool> find(String toolId);

    /** 返回全部已注册工具的定义，供模型声明工具。 */
    List<ToolDefinition> definitions();

    /** 执行一次工具调用。 */
    ToolResult call(ToolCall call, LoopContext context);
}
