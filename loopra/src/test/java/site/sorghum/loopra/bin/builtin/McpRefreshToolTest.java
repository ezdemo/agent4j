package site.sorghum.loopra.bin.builtin;

import org.junit.jupiter.api.Test;
import site.sorghum.loopra.tool.ToolContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpRefreshToolTest {

    @Test
    void exposesMcpRefreshAndRequiresProjectContext() {
        McpRefreshTool tool = new McpRefreshTool();

        assertTrue(tool.getSolonTools().stream().anyMatch(item -> "mcprefresh".equals(item.name())));
        assertEquals("WORKSPACE_MISSING: 无法确定当前项目，不能刷新 MCP", tool.refresh(null));
        assertEquals("WORKSPACE_MISSING: 无法确定当前项目，不能刷新 MCP",
                tool.refresh(new ToolContext(Map.of(), null, "session-1")));
    }
}
