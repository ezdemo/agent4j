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
 * 网页抓取工具 —— 下载 URL 并提取可读文本内容。
 *
 * @author Sorghum
 */
@Component
public class WebFetchTool extends AgentTool {

    @Inject
    private WebService webService;

    @Override
    public String getName() { return "web_fetch"; }

    @Override
    public String getDescription() {
        return "Download a URL and return its visible text content.";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Collections.singletonList(
                new ToolParameter("url", "string", true, "Absolute http:// or https:// URL")
        );
    }

    @Override
    public ToolResult execute(ToolContext ctx) {
        try {
            return ToolResult.ok(webService.webFetch(ctx.getString("url")));
        } catch (IOException e) {
            return ToolResult.fail("IO_ERROR", e.getMessage());
        }
    }
}
