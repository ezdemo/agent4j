package site.sorghum.agent4j.tool.web;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.util.Collections;
import java.util.List;

/**
 * 网络搜索工具 —— 通过 DuckDuckGo Lite 搜索互联网。
 *
 * @author Sorghum
 */
@Component
public class WebSearchTool extends AgentTool {

    @Inject
    private WebService webService;

    @Override
    public String getName() {
        return "web_search";
    }

    @Override
    public String getDescription() {
        return "Search the public web. Returns ranked results with title, url, and snippet.";
    }

    @Override
    public String toToolSpec() {
        return """
                ### web_search
                
                描述：搜索互联网，返回标题、URL 和摘要。
                参数: query(必填)。只读。
                """;
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Collections.singletonList(
                new ToolParameter("query", "string", true, "搜索查询")
        );
    }

    @Override
    public ToolResult execute(ToolContext ctx) {
        Integer topK = ctx.has("topK") ? ctx.getInt("topK", 5) : null;
        return ToolResult.ok(webService.webSearch(ctx.getString("query"), topK));
    }
}
