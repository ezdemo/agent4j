package site.sorghum.agent4j.tool.code;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * 标识符查找工具 —— 在单个文件中查找指定标识符。
 *
 * @author Sorghum
 */
@Component
public class FindInCodeTool extends AgentTool {

    @Inject
    private CodeQueryService codeQueryService;

    @Override
    public String getName() {
        return "find_in_code";
    }

    @Override
    public String getDescription() {
        return "Find an identifier in a single file, AST-filtered.";
    }

    @Override
    public String toToolSpec() {
        return """
                ### find_in_code
                
                描述：在单个文件中查找指定标识符，AST 过滤。
                参数: path(必填)。只读。
                """;
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
            String pathStr = ctx.getString("path");
            // 检查路径是否被屏蔽
            Path resolved = ctx.getRootDir().resolve(pathStr).toAbsolutePath().normalize();
            if (ctx.isPathBlocked(resolved)) {
                return ToolResult.fail("PATH_BLOCKED", "路径被屏蔽: " + pathStr);
            }
            return ToolResult.ok(codeQueryService.findInCode(ctx.getRootDir(),
                    pathStr, ctx.getString("name")));
        } catch (IOException e) {
            return ToolResult.fail("IO_ERROR", e.getMessage());
        }
    }
}
