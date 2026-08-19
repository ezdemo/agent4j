package site.sorghum.loopra.integration.cutin.plugin.usage;

import org.junit.jupiter.api.Test;
import site.sorghum.cutin.core.context.LoopContext;
import site.sorghum.cutin.core.context.Usage;
import site.sorghum.cutin.core.loop.InterceptContext;
import site.sorghum.cutin.core.loop.InterceptPoint;
import site.sorghum.cutin.core.model.StreamChunk;
import site.sorghum.cutin.core.plugin.DefaultLoopRegistrar;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class LoopraTokenSpeedPluginTest {

    @Test
    void accumulatesIncrementalUsageAndEmitsCompletionOnce() {
        AtomicLong clock = new AtomicLong(1_000_000_000L);
        List<SpeedEvent> events = new ArrayList<>();
        LoopraTokenSpeedPlugin plugin = new LoopraTokenSpeedPlugin(
            (tokens, realtime, average, done) -> events.add(new SpeedEvent(tokens, realtime, average, done)),
            clock::get
        );
        DefaultLoopRegistrar registrar = new DefaultLoopRegistrar();
        plugin.register(registrar);
        LoopContext context = context("speed-loop");

        run(registrar, InterceptPoint.BEFORE_MODEL, context, null);
        clock.set(1_100_000_000L);
        run(registrar, InterceptPoint.ON_MODEL_STREAM, context, new StreamChunk("a", new Usage(0, 2, 0)));
        clock.set(1_400_000_000L);
        run(registrar, InterceptPoint.ON_MODEL_STREAM, context, new StreamChunk("b", new Usage(0, 1, 0)));

        SpeedEvent realtime = events.get(1);
        assertEquals(3, realtime.tokens());
        assertEquals(1.0 / 0.3, realtime.realtimeTps(), 0.0001);
        assertEquals(3.0 / 0.4, realtime.averageTps(), 0.0001);
        assertNotEquals(realtime.realtimeTps(), realtime.averageTps());

        clock.set(1_500_000_000L);
        run(registrar, InterceptPoint.ON_MODEL_STREAM, context,
            new StreamChunk(null, null, List.of(), List.of(), Usage.ZERO, null, true));
        run(registrar, InterceptPoint.AFTER_MODEL, context, null);

        assertEquals(1, events.stream().filter(SpeedEvent::done).count());
        assertEquals(3, events.get(events.size() - 1).tokens());
    }

    @Test
    void estimatesTokensFromCharactersAndClearsStateOnError() {
        AtomicLong clock = new AtomicLong(1_000_000_000L);
        List<SpeedEvent> events = new ArrayList<>();
        LoopraTokenSpeedPlugin plugin = new LoopraTokenSpeedPlugin(
            (tokens, realtime, average, done) -> events.add(new SpeedEvent(tokens, realtime, average, done)),
            clock::get
        );
        DefaultLoopRegistrar registrar = new DefaultLoopRegistrar();
        plugin.register(registrar);
        LoopContext context = context("estimated-loop");

        run(registrar, InterceptPoint.BEFORE_MODEL, context, null);
        clock.set(1_200_000_000L);
        run(registrar, InterceptPoint.ON_MODEL_STREAM, context, new StreamChunk("abcd", Usage.ZERO));
        assertEquals(2, events.get(0).tokens());
        assertEquals(10.0, events.get(0).realtimeTps(), 0.0001);

        run(registrar, InterceptPoint.ON_MODEL_ERROR, context, new RuntimeException("failed"));
        clock.set(1_500_000_000L);
        run(registrar, InterceptPoint.ON_MODEL_STREAM, context, new StreamChunk("more", Usage.ZERO));
        assertEquals(1, events.size());
    }

    private static void run(DefaultLoopRegistrar registrar, InterceptPoint point, LoopContext context, Object payload) {
        registrar.interceptors().run(point, new InterceptContext(point, "model", null, context, payload));
    }

    private static LoopContext context(String id) {
        return (LoopContext) Proxy.newProxyInstance(
            LoopContext.class.getClassLoader(),
            new Class<?>[]{LoopContext.class},
            (proxy, method, arguments) -> switch (method.getName()) {
                case "id" -> id;
                case "usage" -> Usage.ZERO;
                case "stateVersion" -> 0L;
                case "toString" -> "TestLoopContext[" + id + "]";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                default -> null;
            }
        );
    }

    private record SpeedEvent(long tokens, double realtimeTps, double averageTps, boolean done) { }
}
