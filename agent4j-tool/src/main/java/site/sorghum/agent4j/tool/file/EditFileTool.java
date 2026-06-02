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
public class EditFileTool extends AgentTool {

    @Override
    public String getName() {
        return "edit_file";
    }

    @Override
    public String getDescription() {
        return """
                Apply a SEARCH/REPLACE edit to an existing file.
                `search` must match exactly (whitespace sensitive, no regex).
                The match must be unique in the file — otherwise refused to avoid surprise rewrites.""";
    }

    @Override
    public String toToolSpec() {
        return """
                ### edit_file
                
                描述：对已有文件执行 SEARCH/REPLACE 编辑。search 必须在文件中唯一匹配（空格敏感），
                包含充足前后文（3-5行）确保唯一性。同一文件的多次修改尽量一次完成。
                参数: path(必填), search(必填，精确原文), replace(必填，替换后文本)。可写。
                """;
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Arrays.asList(
                new ToolParameter("path", "string", true, "文件路径"),
                new ToolParameter("search", "string", true, "要搜索替换的精确文本（必须唯一）"),
                new ToolParameter("replace", "string", true, "替换后的文本")
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
            String result = FileEdit.editFile(ctx.getRootDir(), pathStr,
                    ctx.getString("search"), ctx.getString("replace"));
            return ToolResult.ok(result);
        } catch (IOException e) {
            return ToolResult.fail("IO_ERROR", e.getMessage());
        }
    }
}
