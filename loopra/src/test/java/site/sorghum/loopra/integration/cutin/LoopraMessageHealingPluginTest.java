package site.sorghum.loopra.integration.cutin;

import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;
import site.sorghum.cutin.core.context.Budget;
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
import site.sorghum.cutin.core.plugin.PluginBeanManager;
import site.sorghum.loopra.integration.cutin.plugin.policy.LoopraMessageHealingPlugin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoopraMessageHealingPluginTest {

    @Test
    void addsContentToReasoningOnlyAssistantBeforeProviderCall() {
        AtomicReference<ModelCallRequest> captured = new AtomicReference<>();
        DefaultLoopEngine engine = new DefaultLoopEngine();
        engine.addModelProvider(new CapturingProvider(captured));
        PluginBeanManager plugins = new PluginBeanManager(engine.registrar());
        plugins.registerPlugin(new LoopraMessageHealingPlugin());
        plugins.startAll();

        LoopProgram program = LoopProgram.builder("message-healing")
            .node("model", NodeType.MODEL, Steps.modelFromContext("capture"))
            .node("out", NodeType.OUTPUT, ignored -> StepResult.Exit.INSTANCE)
            .next("model", "out")
            .start("model")
            .build();
        Message reasoningOnly = new Message("assistant", null)
            .withMetadata("reasoning_content", "thinking");

        engine.run(program, engine.newContext(
            "ctx",
            List.of(new Message("user", "hi"), reasoningOnly, new Message("user", "continue")),
            Map.of(),
            Budget.unlimited(),
            null
        )).result().join();

        Message healed = captured.get().messages().get(1);
        assertEquals("", healed.content());
        assertEquals("thinking", healed.metadata("reasoning_content"));
    }

    private record CapturingProvider(AtomicReference<ModelCallRequest> captured) implements ModelProvider {
        @Override public String id() { return "capture"; }
        @Override public ModelResponse call(ModelCallRequest request) {
            captured.set(request);
            return ModelResponse.of(new Message("assistant", "ok"), Usage.ZERO);
        }
        @Override public Stream<StreamChunk> stream(ModelCallRequest request) {
            captured.set(request);
            return Stream.of(new StreamChunk("ok", Usage.ZERO));
        }
        @Override public ModelCapabilities capabilities() {
            return new ModelCapabilities(Set.of("capture"), true, true);
        }

        @Override
        public ONode buildBody(ModelCallRequest request, boolean stream) {
            return null;
        }
    }
}
