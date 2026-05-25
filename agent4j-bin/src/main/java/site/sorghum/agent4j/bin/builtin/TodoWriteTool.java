package site.sorghum.agent4j.bin.builtin;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.bin.service.InteractionService;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class TodoWriteTool extends AgentTool {

    @Inject
    private InteractionService interactionService;

    @Override
    public String getName() { return "todo_write"; }

    @Override
    public String getDescription() {
        return "In-session task tracker for 3+ step work.";
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
        return ToolResult.ok(interactionService.todoWrite(todos));
    }
}
