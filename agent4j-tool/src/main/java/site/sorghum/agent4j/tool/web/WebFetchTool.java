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
    public String getName() {
        return "web_fetch";
    }

    @Override
    public String getDescription() {
        return "Download a URL and return its visible text content.";
    }

    @Override
    public String toToolSpec() {
        return """
                ### web_fetch
                
                描述：下载指定 URL 的内容并返回可视化的文本。自动提取网页正文，去除 HTML 标签和广告。
                
                ## 使用指南
                
                1. **获取文档**：读取在线 API 文档、README、教程等
                2. **获取问题解决方案**：读取 StackOverflow、GitHub Issues 等内容
                3. **仅支持 HTTP/HTTPS**：url 必须以 http:// 或 https:// 开头
                
                参数：
                  - url (string, 必填): 完整的 URL（http:// 或 https://）
                
                只读：是
                风暴豁免：是""";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Collections.singletonList(
                new ToolParameter("url", "string", true, "Absolute http:// or https:// URL")
        );
    }

    @Override
    public ToolResult execute(ToolContext ctx) {
        return ToolResult.ok(webService.webFetch(ctx.getString("url")));
    }
}
