package site.sorghum.agent4j.bin.builtin;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.bin.service.MemoryService;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * 记忆删除工具 —— 通过名称删除持久化的记忆。
 * <p>
 * 删除 ~/.agent4j/memory/ 下的对应 JSON 文件。
 * 操作不可逆。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class ForgetTool extends AgentTool {

    @Inject
    private MemoryService memoryService;

    @Override
    public String getName() { return "forget"; }

    @Override
    public String getDescription() {
        return "Delete a memory.";
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
            return ToolResult.ok(memoryService.forget(ctx.getString("name")));
        } catch (IOException e) {
            return ToolResult.fail("IO_ERROR", e.getMessage());
        }
    }
}
