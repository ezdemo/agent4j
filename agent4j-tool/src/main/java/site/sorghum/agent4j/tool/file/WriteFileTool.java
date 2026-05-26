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
    public String toToolSpec() {
        return "### write_file\n\n"
                + "描述：创建新文件或覆盖已有文件的内容。父目录会自动创建。\n\n"
                + "## 使用指南\n\n"
                + "1. **创建新文件**：指定 path 和 content，父目录不存在时会自动创建\n"
                + "2. **覆盖已有文件**：会直接覆盖，不可恢复——确认文件内容后再使用\n"
                + "3. **编辑已有文件**：推荐使用 edit_file（SEARCH/REPLACE）而非 write_file，\n"
                + "   因为 edit_file 更精确且有验证\n"
                + "4. **大文件创建**：没有大小限制，但过大的文件会影响 LLM 上下文\n\n"
                + "参数：\n"
                + "  - path (string, 必填): 文件路径（相对于工作区根目录）\n"
                + "  - content (string, 必填): 文件内容\n\n"
                + "只读：否\n"
                + "风暴豁免：否";
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
