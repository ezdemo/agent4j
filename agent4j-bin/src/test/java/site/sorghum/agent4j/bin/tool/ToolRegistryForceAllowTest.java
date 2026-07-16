package site.sorghum.agent4j.bin.tool;

import org.junit.jupiter.api.Test;
import org.noear.solon.ai.chat.tool.FunctionToolDesc;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private static FunctionToolDesc tool(String name) {
        return new FunctionToolDesc(name)
                .description(name)
                .inputSchema("{\"type\":\"object\"}")
                .returnType(String.class)
                .doHandle(args -> "ok");
    }
}
