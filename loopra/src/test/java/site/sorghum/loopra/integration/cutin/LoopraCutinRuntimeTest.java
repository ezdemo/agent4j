package site.sorghum.loopra.integration.cutin;

import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;
import org.noear.solon.ai.chat.tool.FunctionTool;
import site.sorghum.loopra.bin.model.TestLoopraProvider;
import site.sorghum.loopra.bin.tool.ToolRegistry;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LoopraCutinRuntimeTest {

    @Test
    void runtimeOwnsEngineAndPluginLifecycle() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(echoTool());
        LoopraCutinRuntime runtime = LoopraCutinRuntime.create(
            TestLoopraProvider.builder().build(),
            registry
        );

        assertTrue(runtime.started());
        assertNotNull(runtime.plugins().getBean("loopra-cutin"));
        assertTrue(registry.cutinRegistry().find("echo").isPresent());

        runtime.stop();
        assertFalse(runtime.started());
    }

    private static FunctionTool echoTool() {
        return new FunctionTool() {
            @Override
            public String name() {
                return "echo";
            }

            @Override
            public String title() {
                return "echo";
            }

            @Override
            public String description() {
                return "Echo text.";
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
                return "echo";
            }
        };
    }

}
