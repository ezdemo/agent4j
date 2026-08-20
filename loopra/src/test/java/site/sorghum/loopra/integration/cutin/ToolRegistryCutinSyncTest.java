package site.sorghum.loopra.integration.cutin;

import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;
import org.noear.solon.ai.chat.tool.FunctionTool;
import site.sorghum.cutin.core.tool.Tool;
import site.sorghum.cutin.core.tool.ToolDefinition;
import site.sorghum.loopra.bin.agent.environment.SessionEnvironment;
import site.sorghum.loopra.bin.tool.ToolRegistry;

import java.lang.reflect.Type;
import java.nio.file.Paths;
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

    @Test
    void clearToolsEmptiesLegacyAndCutinView() {
        ToolRegistry registry = new ToolRegistry();
        registry.setEnvironment(SessionEnvironment.local(Paths.get(System.getProperty("user.dir"))));
        registry.register(simpleTool("ping"));

        assertTrue(registry.cutinRegistry().find("ping").isPresent());
        assertFalse(registry.all().isEmpty());

        registry.clearTools();

        assertTrue(registry.all().isEmpty(), "legacy enabled map must be empty");
        assertTrue(registry.allScanned().isEmpty(), "scanned map must be empty");
        assertFalse(registry.cutinRegistry().find("ping").isPresent(), "cutin view must be empty");
        assertEquals(0, registry.toOpenAiTools().getArray().size(), "no tools to upload");
    }

    @Test
    void syncReplacementBreaksRegistrationCloseButClearToolsStillEmptiesView() {
        // 复现“禁用 tool-gateway 后工具仍上传”的根因：
        // gateway 注册时闭包捕获当时的 bridge 实例；refresh() 的 syncCutinRegistry 会
        // setTools 换成新 bridge 实例，导致 stop 时 unregister(旧实例) 实例不匹配而移除失败。
        ToolRegistry registry = new ToolRegistry();
        registry.setEnvironment(SessionEnvironment.local(Paths.get(System.getProperty("user.dir"))));
        registry.register(simpleTool("ping"));
        Tool bridgeAtRegister = registry.cutinRegistry().find("ping").orElseThrow();

        registry.refresh(); // 走“已注入”分支 → syncCutinRegistry → 视图换成新 bridge 实例
        Tool current = registry.cutinRegistry().find("ping").orElseThrow();
        assertNotSame(bridgeAtRegister, current, "sync 必须把视图换成新 bridge 实例");

        boolean removed = registry.cutinRegistry().unregister("ping", bridgeAtRegister);
        assertFalse(removed, "旧实例注销必然失败——网关 stop 无法移除视图里的工具");

        // 兜底：clearTools 整体清空，模型请求的 tools 为空
        registry.clearTools();
        assertFalse(registry.cutinRegistry().find("ping").isPresent());
        assertEquals(0, registry.toOpenAiTools().getArray().size());
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
