package site.sorghum.agent4j.tool.code;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * 符号大纲提取工具 —— 获取源文件中的顶层符号（类、方法、字段）。
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
