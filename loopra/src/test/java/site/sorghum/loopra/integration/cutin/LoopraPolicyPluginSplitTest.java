package site.sorghum.loopra.integration.cutin;

import org.junit.jupiter.api.Test;
import site.sorghum.cutin.core.context.DefaultLoopContext;
import site.sorghum.cutin.core.context.Message;
import site.sorghum.cutin.core.loop.DefaultLoopEngine;
import site.sorghum.cutin.core.loop.InterceptContext;
import site.sorghum.cutin.core.loop.InterceptPoint;
import site.sorghum.cutin.core.loop.InterceptionResult;
import site.sorghum.cutin.core.model.ModelCallRequest;
import site.sorghum.cutin.core.model.ModelResponse;
import site.sorghum.cutin.core.plugin.DefaultLoopRegistrar;
import site.sorghum.cutin.core.tool.ToolCall;
import site.sorghum.cutin.core.tool.ToolResult;
import site.sorghum.loopra.bin.agent.model.PreparedMessages;
import site.sorghum.loopra.bin.agent.resilient.ReasonBreaker;
import site.sorghum.loopra.bin.agent.resilient.StormBreaker;
import site.sorghum.loopra.bin.model.LoopraModelProvider;
import site.sorghum.loopra.bin.tool.ToolRegistry;
import site.sorghum.loopra.integration.cutin.plugin.policy.LoopraPolicyHost;
import site.sorghum.loopra.integration.cutin.plugin.policy.LoopraModelPolicyPlugin;
import site.sorghum.loopra.integration.cutin.plugin.policy.LoopraToolPolicyPlugin;
import site.sorghum.loopra.integration.cutin.plugin.preflight.LoopraPreflight;
import site.sorghum.loopra.tool.AgentOutput;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoopraPolicyPluginSplitTest {

    @Test
    void splitPluginsRegisterModelAndToolPolicyInterceptors() {
        DefaultLoopEngine engine = new DefaultLoopEngine();
        DefaultLoopRegistrar registrar = (DefaultLoopRegistrar) engine.registrar();

        StubHost host = new StubHost();
        new LoopraModelPolicyPlugin(host).register(registrar);
        new LoopraToolPolicyPlugin(host).register(registrar);

        assertEquals(1, registrar.interceptors().size(InterceptPoint.BEFORE_MODEL));
        assertEquals(1, registrar.interceptors().size(InterceptPoint.ON_MODEL_STREAM));
        assertEquals(1, registrar.interceptors().size(InterceptPoint.AFTER_MODEL));
        assertEquals(1, registrar.interceptors().size(InterceptPoint.BEFORE_TOOL));
    }

    @Test
    void emptyOrSystemOnlyMessagesGotoOutputInsteadOfCallingProvider() {
        DefaultLoopEngine engine = new DefaultLoopEngine();
        DefaultLoopRegistrar registrar = (DefaultLoopRegistrar) engine.registrar();
        new LoopraModelPolicyPlugin(new StubHost()).register(registrar);

        InterceptionResult empty = runBeforeModel(engine, List.of());
        assertTrue(empty.decision().isGoto());
        assertEquals(LoopraPreflight.OUTPUT_NODE, empty.decision().targetNodeId());
        assertEquals("empty_input", empty.context().variables().get("loopraExitReason"));
        assertTrue(String.valueOf(empty.context().variables()
                .get(LoopraPreflight.RESULT_VARIABLE)).contains("没有可供模型处理的消息内容"));

        // 只有 system 指令也会让 responses 协议的 input 为空，同样需要拦截
        InterceptionResult systemOnly = runBeforeModel(
                engine, List.of(new Message("system", "你是助手")));
        assertTrue(systemOnly.decision().isGoto());
        assertEquals(LoopraPreflight.OUTPUT_NODE, systemOnly.decision().targetNodeId());
    }

    @Test
    void usableMessagesProceedWithEffectiveRequest() {
        DefaultLoopEngine engine = new DefaultLoopEngine();
        DefaultLoopRegistrar registrar = (DefaultLoopRegistrar) engine.registrar();
        new LoopraModelPolicyPlugin(new StubHost()).register(registrar);

        InterceptionResult result = runBeforeModel(
                engine, List.of(new Message("system", "你是助手"), new Message("user", "你好")));

        assertFalse(result.decision().isGoto());
        // MODIFIED 决策在拦截链中仅折叠上下文与载荷，最终决策回落到 PASS
        assertFalse(result.decision().isTerminal());
        assertTrue(result.payload() instanceof ModelCallRequest effective
                && effective.messages().size() == 2);
    }

    @Test
    void imageOnlyMessageCountsAsUsableInput() {
        DefaultLoopEngine engine = new DefaultLoopEngine();
        DefaultLoopRegistrar registrar = (DefaultLoopRegistrar) engine.registrar();
        new LoopraModelPolicyPlugin(new StubHost()).register(registrar);

        Message imageOnly = new Message(
                "user", null, null, List.of(), Map.of("images", List.of("https://example.com/a.png")));
        InterceptionResult result = runBeforeModel(engine, List.of(imageOnly));

        assertFalse(result.decision().isGoto());
        assertFalse(result.decision().isTerminal());
    }

    private static InterceptionResult runBeforeModel(DefaultLoopEngine engine, List<Message> messages) {
        DefaultLoopContext context = engine.newContext("model-policy", Map.of());
        context.replaceMessages(messages);
        DefaultLoopRegistrar registrar = (DefaultLoopRegistrar) engine.registrar();
        return registrar.interceptors().run(
                InterceptPoint.BEFORE_MODEL,
                new InterceptContext(
                        InterceptPoint.BEFORE_MODEL, "model", null, context,
                        new ModelCallRequest("test-model", messages, List.of(), Map.of())));
    }

    private static final class StubHost implements LoopraPolicyHost {

        @Override
        public PreparedMessages prepareCutinMessages(DefaultLoopContext context, int step) {
            return null;
        }

        @Override
        public AgentOutput getOutput() {
            return null;
        }

        @Override
        public ReasonBreaker reasonBreaker() {
            return null;
        }

        @Override
        public LoopraModelProvider getModelProvider() {
            return null;
        }

        @Override
        public String interceptHITLFromCutin(ModelResponse response) {
            return null;
        }

        @Override
        public ToolRegistry getToolRegistry() {
            return null;
        }

        @Override
        public boolean isPlanMode() {
            return false;
        }

        @Override
        public ToolResult rejectCutinTool(ToolCall call, String message, String reason) {
            return null;
        }

        @Override
        public StormBreaker stormBreaker() {
            return null;
        }

        @Override
        public void markCutinStormSuppressed() {
        }

        @Override
        public void injectReasonBreakReminder() {
        }
    }
}
