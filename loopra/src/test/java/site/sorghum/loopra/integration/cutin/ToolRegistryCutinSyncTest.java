package site.sorghum.loopra.integration.cutin;

import org.junit.jupiter.api.Test;
import org.noear.solon.ai.chat.tool.FunctionTool;
import site.sorghum.cutin.core.tool.ToolDefinition;
import site.sorghum.loopra.bin.tool.ToolRegistry;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryCutinSyncTest {

    @Test
    void registryChangesPropagateToCutinView() {
        ToolRegistry registry = new ToolRegistry();
        FunctionTool tool = simpleTool("ping");
        registry.register(tool);

        assertTrue(registry.cutinRegistry().find("ping").isPresent());

        registry.setForceDenyTools(Set.of("ping"));
        assertFalse(
            registry.cutinRegistry().find("ping").isPresent(),
            "force-deny must remove the tool from the cutin view"
        );
    }

    @Test
    void functionToolMetadataIsBridgedIntoCutinDefinition() {
        ToolRegistry registry = new ToolRegistry();
        FunctionTool tool = simpleTool("write_file");
        Map<String, Object> meta = tool.meta();
        meta.put("readOnly", false);
        meta.put("stormExempt", true);
        meta.put("timeoutMillis", 12345L);
        meta.put("roles", List.of("implement"));
        registry.register(tool);

        ToolDefinition definition = registry.cutinRegistry().find("write_file")
            .orElseThrow().definition();
        assertFalse(definition.metadata().readOnly());
        assertTrue(definition.metadata().stormExempt());
        assertEquals(12345L, definition.metadata().timeoutMillis());
        assertTrue(definition.metadata().roles().contains("implement"));
    }

    private static FunctionTool simpleTool(String name) {
        Map<String, Object> meta = new LinkedHashMap<>();
        return new FunctionTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String title() {
                return name;
            }

            @Override
            public String description() {
                return "Simple test tool.";
            }

            @Override
            public boolean returnDirect() {
                return false;
            }

            @Override
            public String inputSchema() {
                return "{\"type\":\"object\",\"properties\":{}}";
            }

            @Override
            public Type returnType() {
                return String.class;
            }

            @Override
            public Object handle(Map<String, Object> args) {
                return "pong";
            }

            @Override
            public Map<String, Object> meta() {
                return meta;
            }

            @Override
            public void metaPut(String key, Object value) {
                meta.put(key, value);
            }
        };
    }
}
