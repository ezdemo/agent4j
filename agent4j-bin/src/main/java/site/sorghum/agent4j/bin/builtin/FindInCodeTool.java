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
 * 标识符查找工具 —— 在单个文件中查找指定标识符。
 * <p>
 * 先去除注释和字符串字面量后搜索，减少误报。
 * 返回匹配位置和上下文（前后各 40 字符）。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class FindInCodeTool extends AgentTool {

    @Inject
    private CodeQueryService codeQueryService;

    @Override
    public String getName() { return "find_in_code"; }

    @Override
    public String getDescription() {
        return "Find an identifier in a single file, AST-filtered.";
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
            return ToolResult.ok(codeQueryService.findInCode(ctx.getRootDir(),
                    ctx.getString("path"), ctx.getString("name")));
        } catch (IOException e) {
            return ToolResult.fail("IO_ERROR", e.getMessage());
        }
    }
}
