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

@Component
public class ReadFileTool extends AgentTool {

    @Override
    public String getName() { return "read_file"; }

    @Override
    public String getDescription() {
        return "Read a file under the workspace root. Returns full text content (no annotations).\n"
                + "Optional scoping: head (first N lines), tail (last N lines), range \"A-B\" (1-indexed inclusive).\n"
                + "Binaries and files over 32 MiB are refused.";
    }

    @Override
    public String toToolSpec() {
        return "### read_file\n\n"
                + "描述：读取工作区内的文件内容。返回完整的文本内容（无行号标注）。\n\n"
                + "## 使用指南\n\n"
                + "1. **完整读取**：默认返回整个文件内容\n"
                + "2. **范围读取**：通过以下参数控制输出范围：\n"
                + "   - head: 返回文件前 N 行\n"
                + "   - tail: 返回文件后 N 行\n"
                + "   - range: 返回指定行范围 \"start-end\"（从 1 开始计数，两端包含）\n"
                + "3. **head/tail/range 可以组合使用**：如 head=50 返回前 50 行\n"
                + "4. **大文件处理**：超过 32 MiB 的文件会被拒绝读取\n"
                + "5. **二进制文件**：二进制文件会自动检测并拒绝\n\n"
                + "参数：\n"
                + "  - path (string, 必填): 文件路径（相对于工作区根目录）\n"
                + "  - head (int, 可选): 返回前 N 行\n"
                + "  - tail (int, 可选): 返回后 N 行\n"
                + "  - range (string, 可选): 行范围 \"start-end\"，如 \"50-100\"\n\n"
                + "只读：是\n"
                + "风暴豁免：是";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Arrays.asList(
                new ToolParameter("path", "string", true, "文件路径（相对于工作区根目录）"),
                new ToolParameter("head", "int", false, "返回前 N 行"),
                new ToolParameter("tail", "int", false, "返回后 N 行"),
                new ToolParameter("range", "string", false, "行范围 \"start-end\"，如 \"50-100\"")
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
            Integer head = ctx.has("head") ? ctx.getInt("head", 0) : null;
            Integer tail = ctx.has("tail") ? ctx.getInt("tail", 0) : null;
            String result = FileEdit.readFile(ctx.getRootDir(), pathStr,
                    head, tail, ctx.getString("range"));
            return ToolResult.ok(result);
        } catch (IOException e) {
            return ToolResult.fail("IO_ERROR", e.getMessage());
        }
    }

    @Override
    public boolean isReadOnly() { return true; }
}
