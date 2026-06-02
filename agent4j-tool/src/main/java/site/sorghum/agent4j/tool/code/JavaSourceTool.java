package site.sorghum.agent4j.tool.code;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Java 源码定位工具 —— 通过全限定类名查找 Java 源文件。
 *
 * @author Sorghum
 */
@Component
public class JavaSourceTool extends AgentTool {

    @Inject
    private CodeQueryService codeQueryService;

    @Override
    public String getName() {
        return "java_source";
    }

    @Override
    public String getDescription() {
        return "Find and return Java source code by fully-qualified class name.";
    }

    @Override
    public String toToolSpec() {
        return """
                ### java_source
                
                描述：通过全限定类名查找并返回 Java 源码。jarKeyword 限定 Jar 包范围。
                参数: className(必填), jarKeyword(必填)。只读。
                """;
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Arrays.asList(
                new ToolParameter("className", "string", true, "Fully qualified class name"),
                new ToolParameter("jarKeyword", "string", true, "Keyword to filter jars")
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
