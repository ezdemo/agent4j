package site.sorghum.loopra.integration.cutin;

import org.junit.jupiter.api.Test;
import site.sorghum.cutin.core.context.DefaultLoopContext;
import site.sorghum.cutin.core.loop.DefaultLoopEngine;
import site.sorghum.cutin.core.loop.InterceptPoint;
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
import site.sorghum.loopra.tool.AgentOutput;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
