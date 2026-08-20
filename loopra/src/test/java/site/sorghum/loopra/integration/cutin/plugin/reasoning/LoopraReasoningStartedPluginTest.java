package site.sorghum.loopra.integration.cutin.plugin.reasoning;

import org.junit.jupiter.api.Test;
import site.sorghum.cutin.core.context.LoopContext;
import site.sorghum.cutin.core.context.Usage;
import site.sorghum.cutin.core.loop.InterceptContext;
import site.sorghum.cutin.core.loop.InterceptPoint;
import site.sorghum.cutin.core.model.StreamChunk;
import site.sorghum.cutin.core.model.ModelStreamPhase;
import site.sorghum.cutin.core.plugin.DefaultLoopRegistrar;
import site.sorghum.loopra.tool.AgentOutput;
import site.sorghum.loopra.tool.ChoiceOption;
import site.sorghum.loopra.tool.LogLevel;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoopraReasoningStartedPluginTest {

    @Test
    void emitsReasoningStartedOncePerModelCall() {
        AtomicInteger events = new AtomicInteger();
        DefaultLoopRegistrar registrar = new DefaultLoopRegistrar();
        new LoopraReasoningStartedPlugin(() -> output(events)).register(registrar);
        LoopContext context = context();

        run(registrar, InterceptPoint.BEFORE_MODEL, context, null);
        StreamChunk reasoningStarted = new StreamChunk("", Usage.ZERO)
            .withPhase(ModelStreamPhase.REASONING_STARTED);
        run(registrar, InterceptPoint.ON_MODEL_STREAM, context, reasoningStarted);
        run(registrar, InterceptPoint.ON_MODEL_STREAM, context, reasoningStarted);
        assertEquals(1, events.get());

        run(registrar, InterceptPoint.BEFORE_MODEL, context, null);
        run(registrar, InterceptPoint.ON_MODEL_STREAM, context, reasoningStarted);
        assertEquals(2, events.get());
    }

    private static AgentOutput output(AtomicInteger events) {
        return new AgentOutput() {
            public void onContentDelta(String token) { }
            public void onContentComplete() { }
            public void onReasoningDelta(String token) { }
            public void onReasoning(String reasoning) { }
            public void onReasoningStarted() { events.incrementAndGet(); }
            public void onToolCall(String name, String args) { }
            public void onToolResult(String name, String result) { }
            public void onUsage(int prompt, int completion, int total, int hit, int miss) { }
            public void onError(String error) { }
            public void onLog(LogLevel level, String message) { }
            public void onChoice(List<ChoiceOption> options) { }
        };
    }

    private static void run(DefaultLoopRegistrar registrar, InterceptPoint point,
                            LoopContext context, Object payload) {
        registrar.interceptors().run(point,
            new InterceptContext(point, "model", null, context, payload));
    }

    private static LoopContext context() {
        Map<String, Object> variables = new HashMap<>();
        return (LoopContext) Proxy.newProxyInstance(
            LoopContext.class.getClassLoader(),
            new Class<?>[]{LoopContext.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "variables" -> variables;
                case "putVariable" -> variables.put(String.valueOf(args[0]), args[1]);
                case "usage" -> Usage.ZERO;
                case "stateVersion" -> 0L;
                case "id" -> "reasoning-started-test";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "ReasoningStartedTestContext";
                default -> null;
            }
        );
    }
}
