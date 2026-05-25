package site.sorghum.agent4j.bin.builtin;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.bin.service.CodeQueryService;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
public class JavaSourceTool extends AgentTool {

    @Inject
    private CodeQueryService codeQueryService;

    @Override
    public String getName() { return "java_source"; }

    @Override
    public String getDescription() {
        return "Find and return Java source code by fully-qualified class name.";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Arrays.asList(
                new ToolParameter("className", "string", true, "Fully qualified class name"),
                new ToolParameter("jarKeyword", "string", false, "Keyword to filter jars")
        );
    }

    @Override
    public ToolResult execute(ToolContext ctx) {
        try {
            return ToolResult.ok(codeQueryService.javaSource(ctx.getRootDir(),
                    ctx.getString("className"), ctx.getString("jarKeyword")));
        } catch (IOException e) {
            return ToolResult.fail("IO_ERROR", e.getMessage());
        }
    }
}
