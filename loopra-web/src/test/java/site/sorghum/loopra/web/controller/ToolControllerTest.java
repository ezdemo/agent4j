package site.sorghum.loopra.web.controller;

import org.junit.jupiter.api.Test;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.ai.chat.tool.FunctionToolDesc;
import site.sorghum.loopra.bin.tool.ToolMetadata;
import site.sorghum.loopra.web.model.ToolInfoDTO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolControllerTest {

    @Test
    void toolMetadataIsExposedWithoutMarkingEveryToolStormExempt() {
        FunctionToolDesc regular = tool("write");
        FunctionToolDesc readOnly = tool("read");
        readOnly.metaPut("stormExempt", true);

        ToolInfoDTO regularInfo = toToolInfoDTO(regular);
        ToolInfoDTO readOnlyInfo = toToolInfoDTO(readOnly);

        assertFalse(regularInfo.readOnly());
        assertFalse(regularInfo.stormExempt());
        assertTrue(readOnlyInfo.readOnly());
        assertTrue(readOnlyInfo.stormExempt());
        assertNull(readOnlyInfo.readOnlyOverride());

        ToolMetadata.applyReadOnlyOverride(readOnly, false);
        ToolInfoDTO overriddenInfo = toToolInfoDTO(readOnly);
        assertFalse(overriddenInfo.readOnly());
        assertEquals(Boolean.FALSE, overriddenInfo.readOnlyOverride());
    }

    private static FunctionToolDesc tool(String name) {
        return new FunctionToolDesc(name)
                .description(name)
                .inputSchema("{\"type\":\"object\"}")
                .returnType(String.class)
                .doHandle(args -> "ok");
    }

    private static ToolInfoDTO toToolInfoDTO(FunctionTool tool) {
        return ToolController.toToolInfoDTO(tool, true, false);
    }
}
