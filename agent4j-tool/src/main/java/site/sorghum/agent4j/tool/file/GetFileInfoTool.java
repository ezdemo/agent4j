package site.sorghum.agent4j.tool.file;

import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * 文件信息查询工具 —— 查看工作区内文件或目录的元信息。
 *
 * @author Sorghum
 */
@Component
public class GetFileInfoTool extends AgentTool {

    @Override
    public String getName() { return "get_file_info"; }

    @Override
    public String getDescription() {
        return "Stat a path under the workspace. Returns JSON with type, size in bytes, mtime.";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Collections.singletonList(
                new ToolParameter("path", "string", true, "文件或目录路径")
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
            return ToolResult.ok(FileEdit.getFileInfo(ctx.getRootDir(), pathStr));
        } catch (IOException e) {
            return ToolResult.ok("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    @Override
    public boolean isReadOnly() { return true; }
}
