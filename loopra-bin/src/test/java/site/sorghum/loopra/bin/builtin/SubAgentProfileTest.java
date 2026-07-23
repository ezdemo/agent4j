package site.sorghum.loopra.bin.builtin;

import org.junit.jupiter.api.Test;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.ai.chat.tool.FunctionToolDesc;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SubAgentProfileTest {

    @Test
    void resolvesEveryBuiltInProfile() {
        for (String id : new String[]{"explore", "implement", "test", "review", "plan"}) {
            assertEquals(id, SubAgentProfile.from(id).id());
        }
    }

    @Test
    void readOnlyPromptsDistinguishProjectWorkspaceFromCommunicationStore() {
        for (SubAgentProfile profile : new SubAgentProfile[]{
                SubAgentProfile.EXPLORE, SubAgentProfile.REVIEW, SubAgentProfile.PLAN}) {
            assertTrue(profile.instructions().contains("不修改项目文件"));
            assertTrue(profile.instructions().contains("workspace_write"));
            assertFalse(profile.instructions().contains("不修改任何文件"));
        }
    }

    @Test
    void readOnlyProfilesSelectToolsFromMetadata() {
        FunctionToolDesc extensionQuery = tool("extension_query");
        extensionQuery.metaPut("readOnly", true);
        List<FunctionTool> tools = List.of(
                tool("read"), tool("workspace_read"), tool("workspace_list"),
                tool("workspace_write"), tool("finish"), tool("edit"), tool("bash"),
                extensionQuery);

        for (SubAgentProfile profile : new SubAgentProfile[]{
                SubAgentProfile.EXPLORE, SubAgentProfile.REVIEW, SubAgentProfile.PLAN}) {
            Set<String> allowed = profile.allowedTools(tools);
            assertTrue(allowed.contains("read"));
            assertTrue(allowed.contains("workspace_read"));
            assertTrue(allowed.contains("workspace_list"));
            assertTrue(allowed.contains("workspace_write"));
            assertTrue(allowed.contains("finish"));
            assertTrue(allowed.contains("extension_query"));
            assertFalse(allowed.contains("edit"));
            assertFalse(allowed.contains("bash"));
        }
        assertNull(SubAgentProfile.IMPLEMENT.allowedTools(tools));
        assertNull(SubAgentProfile.TEST.allowedTools(tools));
    }

    private static FunctionToolDesc tool(String name) {
        return new FunctionToolDesc(name)
                .description(name)
                .inputSchema("{\"type\":\"object\"}")
                .returnType(String.class)
                .doHandle(args -> "ok");
    }

    @Test
    void rejectsUnknownProfile() {
        assertThrows(IllegalArgumentException.class, () -> SubAgentProfile.from("designer"));
    }
}
