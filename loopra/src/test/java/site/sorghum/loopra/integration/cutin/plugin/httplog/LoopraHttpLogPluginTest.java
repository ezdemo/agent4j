package site.sorghum.loopra.integration.cutin.plugin.httplog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import site.sorghum.cutin.core.context.Budget;
import site.sorghum.cutin.core.context.Message;
import site.sorghum.cutin.core.context.Usage;
import site.sorghum.cutin.core.loop.DefaultLoopEngine;
import site.sorghum.cutin.core.loop.LoopProgram;
import site.sorghum.cutin.core.loop.NodeType;
import site.sorghum.cutin.core.loop.StepResult;
import site.sorghum.cutin.core.model.ModelCallRequest;
import site.sorghum.cutin.core.model.ModelCapabilities;
import site.sorghum.cutin.core.model.ModelProvider;
import site.sorghum.cutin.core.model.ModelResponse;
import site.sorghum.cutin.core.model.StreamChunk;
import site.sorghum.cutin.core.plugin.PluginBeanManager;
import site.sorghum.cutin.core.loop.Steps;
import site.sorghum.cutin.integrations.model.ModelProviderException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoopraHttpLogPluginTest {

    @TempDir
    Path tempDirectory;

    @Test
    void writesLatestTwoRequestsToTheSanitizedSessionLog() throws Exception {
        DefaultLoopEngine engine = new DefaultLoopEngine();
        engine.addModelProvider(new EchoProvider());
        PluginBeanManager manager = new PluginBeanManager(engine.registrar());
        manager.registerPlugin(new LoopraHttpLogPlugin(
            "https://example.test/v1/responses",
            request -> new ModelCallRequest(
                request.modelId(),
                request.messages(),
                request.tools(),
                Map.of("reasoningEffort", "high")
            ),
            tempDirectory,
            Clock.fixed(Instant.parse("2026-08-17T01:02:03Z"), ZoneOffset.UTC)
        ));
        manager.startAll();

        LoopProgram program = LoopProgram.builder("http-log")
            .node("model", NodeType.MODEL, Steps.modelFromContext("fake"))
            .node("out", NodeType.OUTPUT, ignored -> StepResult.Exit.INSTANCE)
            .next("model", "out")
            .start("model")
            .build();

        for (int index = 0; index < 3; index++) {
            engine.run(program, engine.newContext(
                "ctx-" + index,
                List.of(new Message("user", "message-" + index)),
                Map.of("sessionId", "session/a"),
                Budget.unlimited(),
                null
            )).result().join();
        }

        Path logFile = tempDirectory.resolve("session_a.log");
        String log = Files.readString(logFile);
        assertEquals(2, log.split("(?m)^={60,}$").length - 1);
        assertFalse(log.contains("message-0"));
        assertTrue(log.contains("message-1"));
        assertTrue(log.contains("message-2"));
        assertTrue(log.contains("https://example.test/v1/responses"));
        assertTrue(log.contains("\"reasoningEffort\":\"high\""));
    }

    @Test
    void writesErrorEntryWhenModelCallFails() throws Exception {
        DefaultLoopEngine engine = new DefaultLoopEngine();
        engine.addModelProvider(new FailingProvider());
        PluginBeanManager manager = new PluginBeanManager(engine.registrar());
        manager.registerPlugin(new LoopraHttpLogPlugin(
            "https://example.test/v1/responses",
            tempDirectory,
            Clock.fixed(Instant.parse("2026-08-17T01:02:03Z"), ZoneOffset.UTC)
        ));
        manager.startAll();

        LoopProgram program = LoopProgram.builder("http-log-error")
            .node("model", NodeType.MODEL, Steps.modelFromContext("fake"))
            .node("out", NodeType.OUTPUT, ignored -> StepResult.Exit.INSTANCE)
            .next("model", "out")
            .start("model")
            .build();

        engine.run(program, engine.newContext(
            "ctx-error",
            List.of(new Message("user", "boom")),
            Map.of("sessionId", "session/e"),
            Budget.unlimited(),
            null
        )).result().join();

        Path logFile = tempDirectory.resolve("session_e.log");
        String log = Files.readString(logFile);
        assertTrue(log.contains(">>> Request"), "请求记录应已写入");
        assertTrue(log.contains(">>> Error"), "错误记录应已写入");
        assertTrue(log.contains("provider returned HTTP 400"));
    }

    private static final class FailingProvider implements ModelProvider {

        @Override
        public String id() {
            return "fake";
        }

        @Override
        public ModelResponse call(ModelCallRequest request) {
            throw new ModelProviderException("provider returned HTTP 400: {\"error\":\"bad request\"}");
        }

        @Override
        public Stream<StreamChunk> stream(ModelCallRequest request) {
            throw new ModelProviderException("provider returned HTTP 400: {\"error\":\"bad request\"}");
        }

        @Override
        public ModelCapabilities capabilities() {
            return new ModelCapabilities(Set.of("fake"), true, true);
        }
    }

    private static final class EchoProvider implements ModelProvider {

        @Override
        public String id() {
            return "fake";
        }

        @Override
        public ModelResponse call(ModelCallRequest request) {
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
}
