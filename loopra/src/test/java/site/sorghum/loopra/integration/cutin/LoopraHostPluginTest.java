package site.sorghum.loopra.integration.cutin;

import org.junit.jupiter.api.Test;
import site.sorghum.cutin.core.context.DefaultLoopContext;
import site.sorghum.cutin.core.context.Message;
import site.sorghum.cutin.core.context.Usage;
import site.sorghum.cutin.core.loop.*;
import site.sorghum.cutin.core.model.*;
import site.sorghum.cutin.core.plugin.PluginBeanManager;
import site.sorghum.loopra.bin.agent.model.PreparedMessages;
import site.sorghum.loopra.integration.cutin.plugin.compaction.LoopraCompactionHost;
import site.sorghum.loopra.integration.cutin.plugin.compaction.LoopraCompactionPlugin;
import site.sorghum.loopra.integration.cutin.plugin.session.LoopraSessionHost;
import site.sorghum.loopra.integration.cutin.plugin.session.LoopraSessionPlugin;
import site.sorghum.loopra.integration.cutin.plugin.usage.LoopraUsageHost;
import site.sorghum.loopra.integration.cutin.plugin.usage.LoopraUsagePlugin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoopraHostPluginTest {

    @Test
    void lifecyclePluginWiresPreAndPostLoopHooks() {
        DefaultLoopEngine engine = new DefaultLoopEngine();
        LifecycleStub host = new LifecycleStub();
        PluginBeanManager manager = new PluginBeanManager(engine.registrar());
        manager.registerPlugin(new LoopraSessionPlugin(host));
        manager.startAll();

        LoopProgram program = LoopProgram.builder("lifecycle")
            .node("out", NodeType.OUTPUT, Steps.finish())
            .start("out")
            .build();
        LoopResult result = engine.run(program, engine.newContext("ctx", Map.of())).result().join();

        assertEquals(LoopResult.Status.COMPLETED, result.status());
        assertEquals(1, host.begins.get());
        assertEquals(1, host.ends.get());
    }

    @Test
    void usagePluginReportsModelStepDelta() {
        DefaultLoopEngine engine = new DefaultLoopEngine();
        StubModelProvider provider = new StubModelProvider();
        engine.addModelProvider(provider);
        UsageStub host = new UsageStub();
        PluginBeanManager manager = new PluginBeanManager(engine.registrar());
        manager.registerPlugin(new LoopraUsagePlugin(host));
        manager.startAll();

        LoopProgram program = LoopProgram.builder("usage")
            .node("model", NodeType.MODEL, Steps.modelFromContext("fake"))
            .node("out", NodeType.OUTPUT, Steps.finish())
            .build();
        LoopResult result = engine.run(program, engine.newContext("ctx", Map.of())).result().join();

        assertEquals(LoopResult.Status.COMPLETED, result.status());
        assertEquals(1, host.usages.size());
        assertEquals(30L, host.usages.get(0).totalTokens());
        assertEquals(4L, host.usages.get(0).cacheReadTokens());
        assertEquals(1L, host.usages.get(0).cacheCreationTokens());
    }

    @Test
    void compactionPluginRunsBeforeModelPolicy() {
        DefaultLoopEngine engine = new DefaultLoopEngine();
        StubModelProvider provider = new StubModelProvider();
        engine.addModelProvider(provider);
        CompactionStub host = new CompactionStub();
        PluginBeanManager manager = new PluginBeanManager(engine.registrar());
        manager.registerPlugin(new LoopraCompactionPlugin(host));
        manager.startAll();

        LoopProgram program = LoopProgram.builder("compaction")
            .node("model", NodeType.MODEL, Steps.modelFromContext("fake"))
            .node("out", NodeType.OUTPUT, Steps.finish())
            .build();
        LoopResult result = engine.run(program, engine.newContext("ctx", Map.of())).result().join();

        assertEquals(LoopResult.Status.COMPLETED, result.status());
        assertEquals(List.of(new Message("system", "compacted")), provider.lastMessages);
    }

    private static final class LifecycleStub implements LoopraSessionHost {
        private final AtomicInteger begins = new AtomicInteger();
        private final AtomicInteger ends = new AtomicInteger();

        @Override
        public void beginCutinLoop() {
            begins.incrementAndGet();
        }

        @Override
        public void endCutinLoop() {
            ends.incrementAndGet();
        }

        @Override
        public void beforeTurn(String userMessage) {
        }

        @Override
        public void afterTurn() {
        }
    }

    private static final class UsageStub implements LoopraUsageHost {
        private final List<Usage> usages = new CopyOnWriteArrayList<>();

        @Override
        public void reportCutinUsage(Usage usage) {
            usages.add(usage);
        }
    }

    private static final class CompactionStub implements LoopraCompactionHost {

        @Override
        public PreparedMessages prepareCutinMessages(DefaultLoopContext context, int step) {
            context.replaceMessages(List.of(new Message("system", "compacted")));
            return null;
        }
    }

    private static final class StubModelProvider implements ModelProvider {

        private final List<Message> lastMessages = new CopyOnWriteArrayList<>();

        @Override
        public String id() {
            return "fake";
        }

        @Override
        public ModelResponse call(ModelCallRequest request) {
            lastMessages.clear();
            lastMessages.addAll(request.messages());
            return new ModelResponse(
                new Message("assistant", "ok"),
                new Usage(10, 20, 0, 4, 1),
                true
            );
        }

        @Override
        public Stream<StreamChunk> stream(ModelCallRequest request) {
            return Stream.of(new StreamChunk("ok", new Usage(10, 20, 0, 4, 1)));
        }

        @Override
        public ModelCapabilities capabilities() {
            return new ModelCapabilities(Set.of("fake"), true, true);
        }
    }
}
