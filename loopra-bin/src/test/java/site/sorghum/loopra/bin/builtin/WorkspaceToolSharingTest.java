package site.sorghum.loopra.bin.builtin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.noear.solon.ai.chat.tool.FunctionTool;
import site.sorghum.loopra.bin.tool.ToolRegistry;
import site.sorghum.loopra.bin.workspace.SharedWorkspace;
import site.sorghum.loopra.tool.ToolContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceToolSharingTest {

    @TempDir
    Path tempDir;

    @Test
    void parentAndSubAgentToolsShareEntriesInBothDirections() {
        SharedWorkspace parentWorkspace = new SharedWorkspace();
        SharedWorkspace subAgentWorkspace = new SharedWorkspace();
        WorkspaceWriteTool parentWriter = new WorkspaceWriteTool(parentWorkspace);
        WorkspaceReadTool parentReader = new WorkspaceReadTool(parentWorkspace);
        WorkspaceWriteTool subWriter = new WorkspaceWriteTool(subAgentWorkspace);
        WorkspaceReadTool subReader = new WorkspaceReadTool(subAgentWorkspace);
        ToolContext parentContext = new ToolContext(Map.of(), tempDir.toString(), "parent-session");
        ToolContext subContext = new ToolContext(Map.of(), tempDir.toString(), "parent-session");

        assertEquals("Successfully wrote KV entry: tasks/share/context",
                parentWriter.workspaceWrite("tasks/share/context", "from-parent", null, null, null, parentContext));
        assertTrue(subReader.workspaceRead("tasks/share/context", null, subContext)
                .contains("Value: from-parent"));

        assertEquals("Successfully wrote KV entry: tasks/share/result",
                subWriter.workspaceWrite("tasks/share/result", "from-sub-agent", null, null, null, subContext));
        assertTrue(parentReader.workspaceRead("tasks/share/result", null, parentContext)
                .contains("Value: from-sub-agent"));
        assertTrue(Files.isRegularFile(tempDir.resolve(".loopra/workspace/workspace.json")));
    }

    @Test
    void copiedSubAgentRegistryRetainsParentWorkspaceToolInstances() {
        SharedWorkspace workspace = new SharedWorkspace();
        FunctionTool writeTool = new WorkspaceWriteTool(workspace).getSolonTools().iterator().next();
        FunctionTool readTool = new WorkspaceReadTool(workspace).getSolonTools().iterator().next();
        ToolRegistry parentRegistry = new ToolRegistry().setDisabledTools(java.util.Set.of());
        parentRegistry.register(writeTool);
        parentRegistry.register(readTool);

        ToolRegistry subAgentRegistry = parentRegistry.copy();

        assertSame(writeTool, subAgentRegistry.get("workspace_write"));
        assertSame(readTool, subAgentRegistry.get("workspace_read"));
    }

    @Test
    void emptyOptionalPlaceholdersDoNotOverrideDocumentContent() {
        SharedWorkspace workspace = new SharedWorkspace();
        WorkspaceWriteTool writer = new WorkspaceWriteTool(workspace);
        ToolContext context = new ToolContext(Map.of(), tempDir.toString(), "session");

        assertEquals("Successfully wrote document entry: tasks/share/report (type: text/plain)",
                writer.workspaceWrite("tasks/share/report", "", "report-body", "", "", context));
        assertEquals("report-body", workspace.readDoc(tempDir, "tasks/share/report").orElseThrow().getContent());
        assertTrue(workspace.readKV(tempDir, "tasks/share/report").isEmpty());
    }
}
