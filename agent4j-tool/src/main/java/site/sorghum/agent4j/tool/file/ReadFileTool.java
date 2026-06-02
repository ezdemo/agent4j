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
    public String getName() {
        return "read_file";
    }

    @Override
    public String getDescription() {
        return """
                Read a file under the workspace root. Returns full text content (no annotations).
                Always read the complete file — do NOT use head/tail/range unless specifically needed for line-level precision.
                Binaries and files over 100 MiB are refused.""";
    }

    @Override
    public String toToolSpec() {
        return """
                ### read_file
                
                描述：读取工作区内的文件内容。返回完整的文本内容（无行号标注）。
                
                ## 使用指南
                
                1. **完整读取**：默认返回整个文件内容
                2. **一次性读取**：请直接读取完整文件，不要使用 head/tail/range 分段读取，除非你确实只需要文件的特定行
                3. **大文件处理**：超过 100 MiB 的文件会被拒绝读取
                4. **二进制文件**：二进制文件会自动检测并拒绝
                
                参数：
                  - path (string, 必填): 文件路径（相对于工作区根目录）
                  - head (int, 可选): 返回前 N 行（仅在需要精确行范围时使用）
                  - tail (int, 可选): 返回后 N 行（仅在需要精确行范围时使用）
                  - range (string, 可选): 行范围 "start-end"，如 "1-500"（仅在需要精确行范围时使用，(end-start + 1) 必须 >= 500）
                
                只读：是
                风暴豁免：是""";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Arrays.asList(
                new ToolParameter("path", "string", true, "文件路径（相对于工作区根目录）"),
                new ToolParameter("head", "int", false, "返回前 N 行"),
                new ToolParameter("tail", "int", false, "返回后 N 行"),
                new ToolParameter("range", "string", false, "行范围 \"start-end\"，如 \"1-1000\"")
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
    public boolean isReadOnly() {
        return true;
    }
}
