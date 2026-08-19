package site.sorghum.loopra.integration.cutin;

import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;
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

    @Test
    void modelVisibleSchemasExcludeRuntimeInjectedArguments() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(simpleTool("runtime_context_tool", """
            {
              "type": "object",
              "properties": {
                "path": {"type": "string"},
                "ctx": {"type": "object", "properties": {"large": {"type": "object"}}},
                "__cwd": {"type": "string"}
              },
              "required": ["path", "ctx", "__cwd"]
            }
            """));

        ONode openAiSchema = registry.toOpenAiTools()
            .get(0).get("function").get("parameters");
        Map<?, ?> openAiProperties = openAiSchema.get("properties").toBean(Map.class);
        assertEquals(Set.of("path"), openAiProperties.keySet());
        assertEquals(List.of("path"), openAiSchema.get("required").toBean(List.class));

        Map<String, Object> cutinSchema = registry.cutinRegistry()
            .find("runtime_context_tool").orElseThrow().definition().inputSchema();
        assertEquals(Set.of("path"), ((Map<?, ?>) cutinSchema.get("properties")).keySet());
        assertEquals(List.of("path"), cutinSchema.get("required"));
    }

    private static FunctionTool simpleTool(String name) {
        return simpleTool(name, "{\"type\":\"object\",\"properties\":{}}");
    }

    private static FunctionTool simpleTool(String name, String inputSchema) {
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
                return inputSchema;
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
