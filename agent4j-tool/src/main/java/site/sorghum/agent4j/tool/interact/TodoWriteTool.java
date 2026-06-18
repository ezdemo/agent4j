package site.sorghum.agent4j.tool.interact;

import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.ai.chat.tool.AbsToolProvider;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Param;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.solon.SolonToTools;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 任务跟踪工具 —— 在会话中管理 3 步以上的任务列表。
 *
 * @author Sorghum
 */
@Component
public class TodoWriteTool extends AbsToolProvider implements SolonToTools {

    @Inject
    private InteractionService interactionService;

    @ToolMapping(name = "todo_write", description = """
                在会话中管理任务跟踪列表，适合 3 步以上的复杂工作流。status 取值: pending/in_progress/completed。
                """)
    public String todoWrite(@Param(name = "todos", description = "Todo 列表") List<Map<String, Object>> todos,
                            ToolContext ctx) {
        return interactionService.todoWrite(todos, ctx.getSessionId());
    }

    @Override
    public Collection<FunctionTool> getSolonTools() {
        return this.getTools();
    }

    @Override
    public String getSystemPrompt() {
        return """
                在会话中管理任务跟踪列表，适合 3 步以上的复杂工作流。status 取值: pending/in_progress/completed。
                """;
    }
}


