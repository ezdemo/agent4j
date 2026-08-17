package site.sorghum.loopra.integration.cutin.plugin.preflight;

import org.junit.jupiter.api.Test;
import site.sorghum.cutin.core.context.DefaultLoopContext;
import site.sorghum.cutin.core.loop.DefaultLoopEngine;
import site.sorghum.cutin.core.loop.LoopProgram;
import site.sorghum.cutin.core.loop.LoopResult;
import site.sorghum.cutin.core.loop.NodeType;
import site.sorghum.cutin.core.loop.StepResult;
import site.sorghum.cutin.core.plugin.PluginBeanManager;
import site.sorghum.loopra.bin.agent.model.HitlState;
import site.sorghum.loopra.bin.agent.model.UserMessage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoopraPreflightPluginsTest {

    @Test
    void approvedHitlGoesToOutputBeforeUserMessageIsAppended() throws Exception {
        RecordingHost host = new RecordingHost(HitlState.APPROVED);
        Graph graph = graph(host);
        graph.context.putArtifact(LoopraPreflight.INPUT_ARTIFACT, UserMessage.of("continue"));

        LoopResult result = graph.engine.run(graph.program, graph.context).result().get(5, TimeUnit.SECONDS);

        assertEquals(LoopResult.Status.COMPLETED, result.status());
        assertEquals("resumed", result.finalSnapshot().variables().get(LoopraPreflight.RESULT_VARIABLE));
        assertEquals(List.of("sanitize", "text:continue", "resume"), host.calls);
        assertTrue(graph.context.messages().isEmpty());
    }

    @Test
    void normalInputFlowsThroughAllPreflightNodes() throws Exception {
        RecordingHost host = new RecordingHost(HitlState.NONE);
        host.sanitized = UserMessage.of("cleaned");
        Graph graph = graph(host);
        graph.context.putArtifact(LoopraPreflight.INPUT_ARTIFACT, UserMessage.of("raw"));

        LoopResult result = graph.engine.run(graph.program, graph.context).result().get(5, TimeUnit.SECONDS);

        assertEquals(LoopResult.Status.COMPLETED, result.status());
        assertEquals(List.of("sanitize", "text:cleaned", "append", "clear"), host.calls);
        assertEquals("cleaned", graph.context.messages().get(0).content());
    }

    private static Graph graph(RecordingHost host) {
        DefaultLoopEngine engine = new DefaultLoopEngine();
        PluginBeanManager manager = new PluginBeanManager(engine.registrar());
        LoopraMessageSanitizerPlugin sanitizer = new LoopraMessageSanitizerPlugin(host);
        LoopraHitlPlugin hitl = new LoopraHitlPlugin(host);
        LoopraUserMessagePlugin userMessage = new LoopraUserMessagePlugin(host);
        manager.registerPlugin(sanitizer);
        manager.registerPlugin(hitl);
        manager.registerPlugin(userMessage);
        manager.startAll();
        LoopProgram program = LoopProgram.builder("preflight")
            .node(LoopraPreflight.SANITIZE_NODE, NodeType.CODE, sanitizer::execute)
            .node(LoopraPreflight.HITL_NODE, NodeType.CODE, hitl::execute)
            .node(LoopraPreflight.USER_MESSAGE_NODE, NodeType.CODE, userMessage::execute)
            .node(LoopraPreflight.OUTPUT_NODE, NodeType.OUTPUT, ignored -> StepResult.Exit.INSTANCE)
            .next(LoopraPreflight.SANITIZE_NODE, LoopraPreflight.HITL_NODE)
            .next(LoopraPreflight.HITL_NODE, LoopraPreflight.USER_MESSAGE_NODE)
            .next(LoopraPreflight.USER_MESSAGE_NODE, LoopraPreflight.OUTPUT_NODE)
            .start(LoopraPreflight.SANITIZE_NODE)
            .build();
        return new Graph(engine, engine.newContext("preflight", Map.of()), program);
    }

    private record Graph(DefaultLoopEngine engine, DefaultLoopContext context, LoopProgram program) {
    }

    private static final class RecordingHost implements LoopraPreflightHost {

        private final HitlState hitlState;
        private final List<String> calls = new ArrayList<>();
        private UserMessage sanitized;

        private RecordingHost(HitlState hitlState) {
            this.hitlState = hitlState;
        }

        @Override
        public UserMessage sanitizePreflightMessage(UserMessage message) {
            calls.add("sanitize");
            return sanitized == null ? message : sanitized;
        }

        @Override
        public void setCurrentTurnUserText(String text) {
            calls.add("text:" + text);
        }

        @Override
        public void appendPreflightUserMessage(UserMessage message) {
            calls.add("append");
        }

        @Override
        public void clearSuspendedCutinState() {
            calls.add("clear");
        }

        @Override
        public HitlState hitlState() {
            return hitlState;
        }

        @Override
        public boolean hasSuspendedCutin() {
            return true;
        }

        @Override
        public boolean hasSandboxPending() {
            return false;
        }

        @Override
        public String resumeApprovedTurn() throws IOException {
            calls.add("resume");
            return "resumed";
        }

        @Override
        public String rejectTurn() {
            calls.add("reject");
            return "rejected";
        }
    }
}
