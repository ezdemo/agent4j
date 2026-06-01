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
        return "### todo_write\n\n"
                + "描述：在会话中管理任务跟踪列表。适合 3 步以上的复杂工作流程。\n\n"
                + "## 使用指南\n\n"
                + "1. **创建任务列表**：在开始复杂工作前，用 todo_write 创建任务清单\n"
                + "2. **更新进度**：修改 todos 中对应项的 status 来标记进度\n"
                + "3. **跟踪复杂工作**：适合需要多个步骤的工作，帮助 LLM 保持进度意识\n"
                + "4. **status 取值**：pending（待办）/ in_progress（进行中）/ completed（已完成）\n\n"
                + "参数：\n"
                + "  - todos (array, 必填): Todo 列表，每项包含 status/content 等\n\n"
                + "只读：否\n"
                + "风暴豁免：否";
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
