package site.sorghum.loopra.integration.cutin.plugin.rawlog;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;
import org.slf4j.LoggerFactory;
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
import site.sorghum.cutin.integrations.model.ModelProviderException;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoopraRawLogPluginTest {

    @Test
    void printsProviderRawBody() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Logger logger = (Logger) LoggerFactory.getLogger(LoopraRawLogPlugin.class);
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);

        try {
            DefaultLoopEngine engine = new DefaultLoopEngine();
            engine.addModelProvider(new RawProvider());
            PluginBeanManager manager = new PluginBeanManager(engine.registrar());
            manager.registerPlugin(new LoopraRawLogPlugin());
            manager.startAll();

            LoopProgram program = LoopProgram.builder("raw-log")
                .node("model", NodeType.MODEL, Steps.modelFromContext("fake"))
                .node("out", NodeType.OUTPUT, ignored -> StepResult.Exit.INSTANCE)
                .next("model", "out")
                .start("model")
                .build();

            engine.run(program, engine.newContext(
                "ctx-raw",
                List.of(new Message("user", "hello")),
                java.util.Map.of(),
                Budget.unlimited(),
                null
            )).result().join();

            List<String> messages = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
            assertTrue(messages.stream().anyMatch(m -> m.contains("provider 原始响应体")));
            assertTrue(messages.stream().anyMatch(m -> m.contains("\"model\":\"fake\"")));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void printsModelErrorOnFailure() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Logger logger = (Logger) LoggerFactory.getLogger(LoopraRawLogPlugin.class);
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);

        try {
            DefaultLoopEngine engine = new DefaultLoopEngine();
            engine.addModelProvider(new FailingProvider());
            PluginBeanManager manager = new PluginBeanManager(engine.registrar());
            manager.registerPlugin(new LoopraRawLogPlugin());
            manager.startAll();

            LoopProgram program = LoopProgram.builder("raw-log-error")
                .node("model", NodeType.MODEL, Steps.modelFromContext("fake"))
                .node("out", NodeType.OUTPUT, ignored -> StepResult.Exit.INSTANCE)
                .next("model", "out")
                .start("model")
                .build();

            engine.run(program, engine.newContext(
                "ctx-error",
                List.of(new Message("user", "boom")),
                java.util.Map.of(),
                Budget.unlimited(),
                null
            )).result().join();

            List<String> messages = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
            assertTrue(messages.stream().anyMatch(m -> m.contains("模型调用失败")));
            assertTrue(messages.stream().anyMatch(m -> m.contains("provider returned HTTP 400")));
        } finally {
            logger.detachAppender(appender);
        }
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

        @Override
        public ONode buildBody(ModelCallRequest request, boolean stream) {
            return null;
        }
    }

    private static final class RawProvider implements ModelProvider {

        @Override
        public String id() {
            return "fake";
        }

        @Override
        public ModelResponse call(ModelCallRequest request) {
            return new ModelResponse(
                new Message("assistant", "ok"),
                Usage.ZERO,
                true,
                "{\"model\":\"fake\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}]}"
            );
        }

        @Override
        public Stream<StreamChunk> stream(ModelCallRequest request) {
            return Stream.of(new StreamChunk("ok", Usage.ZERO));
        }

        @Override
        public ModelCapabilities capabilities() {
            return new ModelCapabilities(Set.of("fake"), true, true);
        }

        @Override
        public ONode buildBody(ModelCallRequest request, boolean stream) {
            return null;
        }
    }
}
