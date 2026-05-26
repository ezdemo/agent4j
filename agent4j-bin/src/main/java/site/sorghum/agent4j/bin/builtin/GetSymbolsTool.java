package site.sorghum.agent4j.bin.builtin;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.bin.service.CodeQueryService;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * 符号大纲提取工具 —— 获取源文件中的顶层符号（类、方法、字段）。
 * <p>
 * 使用正则模式匹配提取 Java 源文件中的类声明、方法签名和字段定义，
 * 返回带行号的符号列表，帮助模型快速了解文件结构。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class GetSymbolsTool extends AgentTool {

    @Inject
    private CodeQueryService codeQueryService;

    @Override
    public String getName() { return "get_symbols"; }

    @Override
    public String getDescription() {
        return "Outline a single source file via tree-sitter — returns top-level symbols.";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Collections.singletonList(
                new ToolParameter("path", "string", true, "文件路径")
        );
    }

    @Override
    public ToolResult execute(ToolContext ctx) {
        try {
            return ToolResult.ok(codeQueryService.getSymbols(ctx.getRootDir(),
                    ctx.getString("path")));
        } catch (IOException e) {
            return ToolResult.fail("IO_ERROR", e.getMessage());
        }
    }
}
