package site.sorghum.loopra.web.controller;

import org.junit.jupiter.api.Test;
import org.noear.solon.ai.chat.tool.FunctionToolDesc;
import site.sorghum.loopra.bin.builtin.SubAgentProfileConfig;
import site.sorghum.loopra.bin.builtin.SubAgentProfileStore;
import site.sorghum.loopra.web.model.SubAgentInfoDTO;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SubAgentControllerTest {

    private static SubAgentProfileConfig defaultProfile(String id) {
        return SubAgentProfileStore.defaults().stream()
                .filter(config -> config.id.equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("默认角色缺失: " + id));
    }

    @Test
    void readOnlyProfilesExposeOnlyReadOnlyNonDeniedTools() {
        FunctionToolDesc readOnly = tool("custom_read");
        readOnly.metaPut("readOnly", true);

        SubAgentInfoDTO info = SubAgentController.toSubAgentInfoDTO(
                defaultProfile("explore"),
                List.of(readOnly, tool("write"), tool("sub_agent")));

        assertEquals(List.of("custom_read"), info.tools());
        assertTrue(info.readOnly());
        assertFalse(info.systemPrompt().isBlank());
        assertEquals("探索", info.name());
    }

    @Test
    void writableProfilesExposeEnabledNonDeniedTools() {
        SubAgentInfoDTO info = SubAgentController.toSubAgentInfoDTO(
                defaultProfile("implement"),
                List.of(tool("write"), tool("read"), tool("goal_create")));

        assertEquals(List.of("read", "write"), info.tools());
        assertFalse(info.readOnly());
    }

    @Test
    void explicitAllowedToolsOverridesReadOnlyFilter() {
        SubAgentProfileConfig config = defaultProfile("explore");
        config.allowedTools = List.of("write");

        SubAgentInfoDTO info = SubAgentController.toSubAgentInfoDTO(
                config,
                List.of(tool("read"), tool("write")));

        assertEquals(List.of("write"), info.tools());
    }

    private static FunctionToolDesc tool(String name) {
        return new FunctionToolDesc(name)
                .description(name)
                .inputSchema("{\"type\":\"object\"}")
                .returnType(String.class)
                .doHandle(args -> "ok");
    }
}
