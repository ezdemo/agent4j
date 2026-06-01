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
 * 文件复制工具 —— 复制文件或目录。
 *
 * @author Sorghum
 */
@Component
public class CopyFileTool extends AgentTool {

    @Override
    public String getName() {
        return "copy_file";
    }

    @Override
    public String getDescription() {
        return "Copy a file or directory. Parent directories of the destination are created as needed.";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Arrays.asList(
                new ToolParameter("source", "string", true, "源路径"),
                new ToolParameter("destination", "string", true, "目标路径")
        );
    }

    @Override
    public ToolResult execute(ToolContext ctx) {
        try {
            String srcStr = ctx.getString("source");
            String dstStr = ctx.getString("destination");
            // 检查源和目标路径是否被屏蔽
            Path srcResolved = ctx.getRootDir().resolve(srcStr).toAbsolutePath().normalize();
            Path dstResolved = ctx.getRootDir().resolve(dstStr).toAbsolutePath().normalize();
            if (ctx.isPathBlocked(srcResolved)) {
                return ToolResult.fail("PATH_BLOCKED", "源路径被屏蔽: " + srcStr);
            }
            if (ctx.isPathBlocked(dstResolved)) {
                return ToolResult.fail("PATH_BLOCKED", "目标路径被屏蔽: " + dstStr);
            }
            String result = FileEdit.copyFile(ctx.getRootDir(), srcStr, dstStr);
            return ToolResult.ok(result);
        } catch (IOException e) {
            return ToolResult.fail("IO_ERROR", e.getMessage());
        }
    }
}
