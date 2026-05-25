package site.sorghum.agent4j.bin.builtin;

import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.bin.tool.FileEdit;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
public class EditFileTool extends AgentTool {

    @Override
    public String getName() { return "edit_file"; }

    @Override
    public String getDescription() {
        return "Apply a SEARCH/REPLACE edit to an existing file.\n"
                + "`search` must match exactly (whitespace sensitive, no regex).\n"
                + "The match must be unique in the file — otherwise refused to avoid surprise rewrites.";
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
            String result = FileEdit.editFile(ctx.getRootDir(), ctx.getString("path"),
                    ctx.getString("search"), ctx.getString("replace"));
            return ToolResult.ok(result);
        } catch (IOException e) {
            return ToolResult.fail("IO_ERROR", e.getMessage());
        }
    }
}
