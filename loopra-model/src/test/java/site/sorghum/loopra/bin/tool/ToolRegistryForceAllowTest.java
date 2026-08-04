package site.sorghum.loopra.bin.tool;

import org.junit.jupiter.api.Test;
import org.noear.solon.ai.chat.tool.FunctionToolDesc;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryForceAllowTest {

    @Test
    void forceAllowToolsRemovesPreviouslyRegisteredToolsAndFiltersNewTools() {
        ToolRegistry registry = new ToolRegistry().setDisabledTools(Set.of());
        registry.register(tool("read"));
        registry.register(tool("edit"));

        registry.setForceAllowTools(Set.of("read"));
        registry.register(tool("bash"));

        assertTrue(registry.has("read"));
        assertFalse(registry.has("edit"));
        assertFalse(registry.has("bash"));
        assertFalse(registry.isEnabled("edit"));
    }

    @Test
    void duplicateNameCannotReplaceFirstRegisteredTool() {
        ToolRegistry registry = new ToolRegistry().setDisabledTools(Set.of());
        FunctionToolDesc trustedRead = tool("read");
        FunctionToolDesc duplicateWrite = tool("read");
        ToolMetadata.applyReadOnlyOverride(trustedRead, true);
        ToolMetadata.applyReadOnlyOverride(duplicateWrite, false);

        registry.register(trustedRead);
        registry.register(duplicateWrite);

        assertSame(trustedRead, registry.get("read"));
        assertTrue(ToolMetadata.isReadOnly(registry.get("read")));
    }

    private static FunctionToolDesc tool(String name) {
        return new FunctionToolDesc(name)
                .description(name)
                .inputSchema("{\"type\":\"object\"}")
                .returnType(String.class)
                .doHandle(args -> "ok");
    }
}
