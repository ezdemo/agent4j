package site.sorghum.loopra.integration.cutin.plugin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import site.sorghum.cutin.core.context.Budget;
import site.sorghum.cutin.core.context.DefaultLoopContext;
import site.sorghum.cutin.core.context.Message;
import site.sorghum.cutin.core.context.Usage;
import site.sorghum.cutin.core.loop.DefaultLoopEngine;
import site.sorghum.cutin.core.loop.LoopProgram;
import site.sorghum.cutin.core.loop.NodeType;
import site.sorghum.cutin.core.loop.StepResult;
import site.sorghum.cutin.core.loop.Steps;
import site.sorghum.cutin.core.model.ModelCallRequest;
import site.sorghum.cutin.core.model.ModelCapabilities;
import site.sorghum.cutin.core.model.ModelProvider;
import site.sorghum.cutin.core.model.ModelResponse;
import site.sorghum.cutin.core.model.StreamChunk;
import site.sorghum.cutin.core.plugin.DefaultLoopRegistrar;
import site.sorghum.cutin.core.plugin.PluginBeanManager;
import site.sorghum.cutin.core.tool.Tool;
import site.sorghum.cutin.core.tool.ToolCall;
import site.sorghum.cutin.core.tool.ToolDefinition;
import site.sorghum.cutin.core.tool.ToolMetadata;
import site.sorghum.cutin.core.tool.ToolResult;
import site.sorghum.loopra.bin.agent.environment.SessionEnvironment;
import site.sorghum.loopra.bin.tool.ToolRegistry;
import site.sorghum.loopra.integration.cutin.plugin.prompt.LoopraPromptHost;
import site.sorghum.loopra.integration.cutin.plugin.prompt.LoopraPromptPlugin;
import site.sorghum.loopra.integration.cutin.plugin.prompt.PromptRegistry;
import site.sorghum.loopra.integration.cutin.plugin.prompt.PromptSlice;
import site.sorghum.loopra.integration.cutin.plugin.prompt.PromptSliceProvider;
import site.sorghum.loopra.integration.cutin.plugin.tool.LoopraToolGatewayPlugin;
import org.noear.solon.ai.chat.tool.FunctionTool;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class PluginPromptToolPluginTest {

    // ==================== PromptRegistry：组合 / 去重 / 重复注册防重 ====================

    @Test
    void promptRegistryComposesSlicesInOrderAndDeduplicatesById() {
        DefaultLoopEngine engine = new DefaultLoopEngine();
        DefaultLoopContext ctx = engine.newContext(
                "id", List.of(new Message("user", "hi")), Map.of(), Budget.unlimited());
        PromptRegistry registry = new PromptRegistry();
        registry.register(c -> PromptSlice.of("a", "HELLO-A", 100));
        registry.register(c -> PromptSlice.of("b", "HELLO-B", 300));
        // 同 id 注册两次：后者覆盖前者，输出只出现一次
        registry.register(c -> PromptSlice.of("a", "HELLO-A2", 200));

        assertEquals("HELLO-A2\n\n---\n\nHELLO-B", registry.assemble(ctx));
        assertEquals(2, registry.slices(ctx).size());
    }

    @Test
    void promptRegistryIgnoresDuplicateProviderInstance() {
        PromptRegistry registry = new PromptRegistry();
        PromptSliceProvider provider = c -> PromptSlice.of("x", "X", 100);
        registry.register(provider);
        registry.register(provider); // 同一实例重复注册不翻倍
        assertEquals(1, registry.size());
        assertEquals("X", registry.assemble(null));
    }

    // ==================== LoopraPromptPlugin：stop/start 重启幂等 ====================

    @Test
    void promptPluginStopStartKeepsBuiltInSlicesRegisteredOnce() {
        DefaultLoopEngine engine = new DefaultLoopEngine();
        PluginBeanManager manager = new PluginBeanManager(engine.registrar());
        FakePromptHost host = new FakePromptHost();
        manager.registerBean("loopraPromptHost", host);
        manager.registerBean("promptRegistry", host.promptRegistry());
        manager.registerPlugin(new LoopraPromptPlugin());
        manager.startAll();

        int afterStart = host.promptRegistry().size();
        assertTrue(afterStart > 0, "built-in slices must be registered on start");

        manager.stopPlugin("loopra-prompt");
        assertEquals(0, host.promptRegistry().size(), "stop must unregister built-in slices");

        manager.startPlugin("loopra-prompt");
        assertEquals(afterStart, host.promptRegistry().size(),
                "restart must not duplicate built-in slices");

        // 重启后拼接结果不得出现重复切片
        DefaultLoopContext ctx = new DefaultLoopEngine().newContext(
                "id", List.of(new Message("user", "hi")), Map.of(), Budget.unlimited());
        String assembled = host.promptRegistry().assemble(ctx);
        int occurrences = countOccurrences(assembled, "## 工具协作约定");
        assertEquals(1, occurrences, "tool-contract slice must appear exactly once");
    }

    // ==================== LoopraToolGatewayPlugin：注册 / 禁用过滤 / 注销 ====================

    @Test
    void toolGatewayRegistersToolsAndRespectsDisabled() {
        DefaultLoopEngine engine = new DefaultLoopEngine();
        PluginBeanManager manager = new PluginBeanManager(engine.registrar());
        ToolRegistry legacy = new ToolRegistry();
        legacy.setEnvironment(SessionEnvironment.local(Paths.get(System.getProperty("user.dir"))));
        legacy.setDisabledTools(Set.of("secret_tool"));
        legacy.register(simpleTool("secret_tool"));
        legacy.register(simpleTool("open_tool"));

        manager.registerBean("loopraToolHost", new LoopraToolGatewayPlugin.LoopraToolHost() {
            @Override public SessionEnvironment environment() { return legacy.getEnvironment(); }
            @Override public ToolRegistry toolRegistry() { return legacy; }
        });
        manager.registerPlugin(new LoopraToolGatewayPlugin());
        manager.startAll();

        var view = ((DefaultLoopRegistrar) engine.registrar()).tools();
        assertTrue(view.find("open_tool").isPresent(), "enabled tool must be visible in cutin view");
        assertFalse(view.find("secret_tool").isPresent(), "disabled tool must not be registered");
    }

    @Test
    void toolGatewayStopUnregistersCutinTools() {
        DefaultLoopEngine engine = new DefaultLoopEngine();
        PluginBeanManager manager = new PluginBeanManager(engine.registrar());
        ToolRegistry legacy = new ToolRegistry();
        legacy.setEnvironment(SessionEnvironment.local(Paths.get(System.getProperty("user.dir"))));
        legacy.register(simpleTool("open_tool"));

        manager.registerBean("loopraToolHost", new LoopraToolGatewayPlugin.LoopraToolHost() {
            @Override public SessionEnvironment environment() { return legacy.getEnvironment(); }
            @Override public ToolRegistry toolRegistry() { return legacy; }
        });
        manager.registerPlugin(new LoopraToolGatewayPlugin());
        manager.startAll();

        var view = ((DefaultLoopRegistrar) engine.registrar()).tools();
        assertTrue(view.find("open_tool").isPresent());

        manager.stopPlugin("loopra-tool-gateway");
        assertFalse(view.find("open_tool").isPresent(),
                "stopping the gateway must remove its tools from the cutin view");
    }

    // ==================== 真实引擎：BEFORE_MODEL 注入 system 切片 ====================

    @Test
    void promptPluginInjectsAssembledSystemAtBeforeModel() throws Exception {
        DefaultLoopEngine engine = new DefaultLoopEngine();
        CapturingProvider provider = new CapturingProvider();
        engine.addModelProvider(provider);
        PluginBeanManager manager = new PluginBeanManager(engine.registrar());
        FakePromptHost host = new FakePromptHost();
        manager.registerBean("loopraPromptHost", host);
        manager.registerBean("promptRegistry", host.promptRegistry());
        manager.registerPlugin(new LoopraPromptPlugin());
        manager.startAll();
        // 额外注册一个外部切片，验证动态贡献也会注入
        host.promptRegistry().register(c -> PromptSlice.of("ext", "EXT-MARKER", 800));

        LoopProgram program = LoopProgram.builder("prompt-inject")
                .node("model", NodeType.MODEL, Steps.modelFromContext("fake"))
                .node("out", NodeType.OUTPUT, ignored -> StepResult.Exit.INSTANCE)
                .next("model", "out")
                .start("model")
                .build();

        engine.run(program, engine.newContext(
                "ctx-1",
                List.of(new Message("user", "hi")),
                Map.of(),
                Budget.unlimited(),
                null
        )).result().join();

        assertNotNull(provider.lastRequest, "model must have been called");
        List<Message> messages = provider.lastRequest.messages();
        Message system = messages.stream()
                .filter(m -> "system".equals(m.role()))
                .findFirst().orElseThrow(() -> new AssertionError("no system message"));
        assertTrue(system.content().contains("## 工具协作约定"),
                "tool-contract slice must be injected");
        assertTrue(system.content().contains("EXT-MARKER"),
                "externally contributed slice must be injected");
        assertFalse(system.content().contains("EXT-MARKER\n\n---\n\nEXT-MARKER"),
                "slices must not be duplicated");
    }

    // ==================== 测试替身 ====================

    /** 最小宿主：不依赖 AgentLoop，隔离 prompt 插件自身逻辑。 */
    static final class FakePromptHost implements LoopraPromptHost {
        private final SessionEnvironment env = SessionEnvironment.local(Paths.get(System.getProperty("user.dir")));
        private final ToolRegistry tools = new ToolRegistry();
        private final PromptRegistry registry = new PromptRegistry();

        @Override public SessionEnvironment environment() { return env; }
        @Override public ToolRegistry toolRegistry() { return tools; }
        @Override public PromptRegistry promptRegistry() { return registry; }
    }

    /** 捕获模型实际收到的请求，用于断言 BEFORE_MODEL 注入结果。 */
    static final class CapturingProvider implements ModelProvider {
        volatile ModelCallRequest lastRequest;

        @Override public String id() { return "fake"; }

        @Override
        public ModelResponse call(ModelCallRequest request) {
            lastRequest = request;
            return ModelResponse.of(new Message("assistant", "ok"), Usage.ZERO);
        }

        @Override
        public Stream<StreamChunk> stream(ModelCallRequest request) {
            return Stream.of(new StreamChunk("ok", Usage.ZERO));
        }

        @Override
        public ModelCapabilities capabilities() {
            return new ModelCapabilities(Set.of("fake"), true, true);
        }
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int from = 0;
        while ((from = text.indexOf(needle, from)) >= 0) {
            count++;
            from += needle.length();
        }
        return count;
    }

    private static FunctionTool simpleTool(String name) {
        Map<String, Object> meta = new LinkedHashMap<>();
        return new FunctionTool() {
            @Override public String name() { return name; }
            @Override public String title() { return name; }
            @Override public String description() { return "Simple test tool."; }
            @Override public boolean returnDirect() { return false; }
            @Override public String inputSchema() { return "{\"type\":\"object\",\"properties\":{}}"; }
            @Override public Type returnType() { return String.class; }
            @Override public Object handle(Map<String, Object> args) { return "pong"; }
            @Override public Map<String, Object> meta() { return meta; }
            @Override public void metaPut(String key, Object value) { meta.put(key, value); }
        };
    }
}
