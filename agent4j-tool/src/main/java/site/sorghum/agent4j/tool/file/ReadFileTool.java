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
                
                描述：读取工作区文件，返回完整文本内容。超过 100 MiB 或二进制文件会被拒绝。
                参数: path(必填), head/tail/range(可选，分段读取用)。只读。
                """;
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
            // NOT_FOUND / IS_DIR / REFUSED 等错误前缀时返回 fail
            if (result.startsWith("[NOT_FOUND]") || result.startsWith("[IS_DIR]") || result.startsWith("[REFUSED]")) {
                return ToolResult.fail(result.substring(1, result.indexOf("]")), result);
            }
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
