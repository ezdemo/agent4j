package site.sorghum.agent4j.tool.web;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.io.IOException;
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
    public String getName() { return "web_search"; }

    @Override
    public String getDescription() {
        return "Search the public web. Returns ranked results with title, url, and snippet.";
    }

    @Override
    public String toToolSpec() {
        return "### web_search\n\n"
                + "描述：通过 DuckDuckGo Lite 搜索互联网。返回带有标题、URL 和摘要的排序结果。\n\n"
                + "## 使用指南\n\n"
                + "1. **搜索技术问题**：搜索 API 文档、错误信息、最佳实践等\n"
                + "2. **获取最新信息**：框架/库的版本更新、已知问题等\n"
                + "3. **搜索限定**：可以通过 query 关键词精确定位搜索结果\n\n"
                + "参数：\n"
                + "  - query (string, 必填): 搜索查询语句\n\n"
                + "只读：是\n"
                + "风暴豁免：是";
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
