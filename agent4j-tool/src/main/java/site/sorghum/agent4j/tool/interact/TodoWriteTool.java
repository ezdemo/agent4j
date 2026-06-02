package site.sorghum.agent4j.tool.interact;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 任务跟踪工具 —— 在会话中管理 3 步以上的任务列表。
 *
 * @author Sorghum
 */
@Component
public class TodoWriteTool extends AgentTool {

    @Inject
    private InteractionService interactionService;

    @Override
    public String getName() {
        return "todo_write";
    }

    @Override
    public String getDescription() {
        return "In-session task tracker for 3+ step work.";
    }

    @Override
    public String toToolSpec() {
        return """
                ### todo_write
                
                描述：在会话中管理任务跟踪列表，适合 3 步以上的复杂工作流。status 取值: pending/in_progress/completed。
                参数: todos(必填，[{status, content}, ...])。可写。
                """;
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Collections.singletonList(
                new ToolParameter("todos", "array", true, "Todo 列表")
        );
    }

    @Override
    public ToolResult execute(ToolContext ctx) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> todos = (List<Map<String, Object>>) ctx.getParams().get("todos");
        return ToolResult.ok(interactionService.todoWrite(todos, ctx.getSessionId()));
    }
}
