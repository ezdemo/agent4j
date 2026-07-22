package site.sorghum.loopra.web.controller;

import org.junit.jupiter.api.Test;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.ai.chat.tool.FunctionToolDesc;
import site.sorghum.loopra.bin.builtin.SubAgentProfile;
import site.sorghum.loopra.web.model.SubAgentInfoDTO;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubAgentControllerTest {

    @Test
    void readOnlyProfilesExposeOnlyReadOnlyNonDeniedTools() {
        FunctionToolDesc readOnly = tool("custom_read");
        readOnly.metaPut("readOnly", true);

        SubAgentInfoDTO info = SubAgentController.toSubAgentInfoDTO(
                SubAgentProfile.EXPLORE,
                List.of(readOnly, tool("write"), tool("sub_agent")));

        assertEquals(List.of("custom_read"), info.tools());
        assertTrue(info.readOnly());
        assertFalse(info.systemPrompt().isBlank());
    }

    @Test
    void writableProfilesExposeEnabledNonDeniedTools() {
        SubAgentInfoDTO info = SubAgentController.toSubAgentInfoDTO(
                SubAgentProfile.IMPLEMENT,
                List.of(tool("write"), tool("read"), tool("goal_create")));

        assertEquals(List.of("read", "write"), info.tools());
        assertFalse(info.readOnly());
    }

    private static FunctionToolDesc tool(String name) {
        return new FunctionToolDesc(name)
                .description(name)
                .inputSchema("{\"type\":\"object\"}")
                .returnType(String.class)
                .doHandle(args -> "ok");
    }
}
