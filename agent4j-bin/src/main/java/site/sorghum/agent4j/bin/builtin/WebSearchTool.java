package site.sorghum.agent4j.bin.builtin;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.bin.service.WebService;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Component
public class WebSearchTool extends AgentTool {

    @Inject
    private WebService webService;

    @Override
    public String getName() { return "web_search"; }

    @Override
    public String getDescription() {
        return "Search the public web. Returns ranked results with title, url, and snippet.";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Collections.singletonList(
                new ToolParameter("query", "string", true, "搜索查询")
        );
    }

    @Override
    public ToolResult execute(ToolContext ctx) {
        try {
            Integer topK = ctx.has("topK") ? ctx.getInt("topK", 5) : null;
            return ToolResult.ok(webService.webSearch(ctx.getString("query"), topK));
        } catch (IOException e) {
            return ToolResult.fail("IO_ERROR", e.getMessage());
        }
    }
}
