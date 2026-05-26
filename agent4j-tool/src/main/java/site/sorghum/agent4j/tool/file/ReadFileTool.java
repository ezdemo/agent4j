package site.sorghum.agent4j.tool.file;

import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.io.IOException;
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
            Integer head = ctx.has("head") ? ctx.getInt("head", 0) : null;
            Integer tail = ctx.has("tail") ? ctx.getInt("tail", 0) : null;
            String result = FileEdit.readFile(ctx.getRootDir(), ctx.getString("path"),
                    head, tail, ctx.getString("range"));
            return ToolResult.ok(result);
        } catch (IOException e) {
            return ToolResult.fail("IO_ERROR", e.getMessage());
        }
    }

    @Override
    public boolean isReadOnly() { return true; }
}
