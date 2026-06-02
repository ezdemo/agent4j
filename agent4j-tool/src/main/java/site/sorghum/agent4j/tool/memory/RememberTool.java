package site.sorghum.agent4j.tool.memory;

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
 * 记忆保存工具 —— 将信息持久化到 ~/.agent4j/memory/。
 *
 * @author Sorghum
 */
@Component
public class RememberTool extends AgentTool {

    @Inject
    private MemoryService memoryService;

    @Override
    public String getName() {
        return "remember";
    }

    @Override
    public String getDescription() {
        return "Save a memory for future sessions.";
    }

    @Override
    public String toToolSpec() {
        return """
                ### remember
                
                描述：持久化保存信息到 ~/.agent4j/memory/，供未来会话使用。
                scope: global/project，type: user/feedback/project/reference。
                参数: name(必填), type(必填), scope(必填), description(必填), content(必填), priority(可选)。可写。
                """;
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Arrays.asList(
                new ToolParameter("name", "string", true, "记忆标识"),
                new ToolParameter("type", "string", true, "类型: user/feedback/project/reference"),
                new ToolParameter("scope", "string", true, "作用域: global/project"),
                new ToolParameter("description", "string", true, "简短描述"),
                new ToolParameter("content", "string", true, "完整内容"),
                new ToolParameter("priority", "int", false, "优先级: 0=low,1=medium,2=high")
        );
    }

    @Override
    public ToolResult execute(ToolContext ctx) {
        try {
            Integer priority = ctx.has("priority") ? ctx.getInt("priority", 0) : null;
            return ToolResult.ok(memoryService.remember(
                    ctx.getString("name"), ctx.getString("type"), ctx.getString("scope"),
                    ctx.getString("description"), ctx.getString("content"), priority));
        } catch (IOException e) {
            return ToolResult.fail("IO_ERROR", e.getMessage());
        }
    }
}
