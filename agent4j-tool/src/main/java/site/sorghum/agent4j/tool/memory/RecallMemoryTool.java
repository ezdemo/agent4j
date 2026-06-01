package site.sorghum.agent4j.tool.memory;

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
 * 记忆读取工具 —— 通过名称检索持久化的记忆内容。
 *
 * @author Sorghum
 */
@Component
public class RecallMemoryTool extends AgentTool {

    @Inject
    private MemoryService memoryService;

    @Override
    public String getName() {
        return "recall_memory";
    }

    @Override
    public String getDescription() {
        return "Read the full body of a memory.";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Collections.singletonList(
                new ToolParameter("name", "string", true, "记忆名称")
        );
    }

    @Override
    public ToolResult execute(ToolContext ctx) {
        try {
            return ToolResult.ok(memoryService.recallMemory(ctx.getString("name")));
        } catch (IOException e) {
            return ToolResult.fail("IO_ERROR", e.getMessage());
        }
    }
}
