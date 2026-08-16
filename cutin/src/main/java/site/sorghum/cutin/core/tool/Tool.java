package site.sorghum.cutin.core.tool;

import site.sorghum.cutin.core.context.LoopContext;

/**
 * 工具 SPI：表示模型可以调用的一个具体能力（读文件、执行 Git、调外部 API 等）。
 *
 * <p>工具的元数据通过 {@link ToolDefinition} 暴露给模型，真正执行时接收
 * {@link ToolCall} 与当前上下文，返回 {@link ToolResult}。</p>
 */
public interface Tool {

    /** 工具唯一标识，通常与 {@link ToolDefinition#id()} 一致。 */
    String id();

    /** 描述工具输入结构的定义，用于模型函数调用声明。 */
    ToolDefinition definition();

    /** 执行一次工具调用。 */
    ToolResult call(ToolCall call, LoopContext context);
}
