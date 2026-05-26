package site.sorghum.agent4j.tool.file;

import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * 文件写入工具 —— 创建或覆盖文件。
 *
 * @author Sorghum
 */
@Component
public class WriteFileTool extends AgentTool {

    @Override
    public String getName() { return "write_file"; }

    @Override
    public String getDescription() {
        return "Create or overwrite a file with the given content. Parent directories are created as needed.";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Arrays.asList(
                new ToolParameter("path", "string", true, "文件路径（相对于工作区根目录）"),
                new ToolParameter("content", "string", true, "文件内容")
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
            String result = FileEdit.writeFile(ctx.getRootDir(), pathStr, ctx.getString("content"));
            return ToolResult.ok(result);
        } catch (IOException e) {
            return ToolResult.fail("IO_ERROR", e.getMessage());
        }
    }
}
