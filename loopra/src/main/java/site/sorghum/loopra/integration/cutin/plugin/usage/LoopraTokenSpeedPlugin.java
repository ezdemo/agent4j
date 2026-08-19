package site.sorghum.loopra.integration.cutin.plugin.usage;

import site.sorghum.cutin.core.loop.InterceptContext;
import site.sorghum.cutin.core.loop.InterceptDecision;
import site.sorghum.cutin.core.loop.InterceptPoint;
import site.sorghum.cutin.core.model.StreamChunk;
import site.sorghum.cutin.core.plugin.AgentPlugin;
import site.sorghum.cutin.core.plugin.LoopPlugin;
import site.sorghum.cutin.core.plugin.LoopRegistrar;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** 计算模型流的实时速度与本轮平均速度。 */
@AgentPlugin(id = "loopra-token-speed")
public final class LoopraTokenSpeedPlugin implements LoopPlugin {
    private static final long EMIT_INTERVAL_NANOS = 200_000_000L;
    private final LoopraTokenSpeedHost host;
    private final LongSupplier nanoTime;
    private final Map<String, SpeedState> states = new ConcurrentHashMap<>();

    public LoopraTokenSpeedPlugin(LoopraTokenSpeedHost host) {
        this(host, System::nanoTime);
    }

    LoopraTokenSpeedPlugin(LoopraTokenSpeedHost host, LongSupplier nanoTime) {
        this.host = host;
        this.nanoTime = nanoTime;
    }

    @Override
    public String id() {
        return "loopra-token-speed";
    }

    @Override
    public void register(LoopRegistrar registrar) {
        registrar.registerInterceptor(InterceptPoint.BEFORE_MODEL, -900, this::onBeforeModel);
        registrar.registerInterceptor(InterceptPoint.ON_MODEL_STREAM, 900, this::onStream);
        registrar.registerInterceptor(InterceptPoint.AFTER_MODEL, 900, this::onAfterModel);
        registrar.registerInterceptor(InterceptPoint.ON_MODEL_ERROR, 900, this::onError);
    }

    @Override
    public void stop() {
        states.clear();
    }

    private InterceptDecision onBeforeModel(InterceptContext context) {
        states.put(context.context().id(), new SpeedState(nanoTime.getAsLong()));
        return InterceptDecision.pass();
    }

    private InterceptDecision onStream(InterceptContext context) {
        if (!(context.payload() instanceof StreamChunk chunk)) {
            return InterceptDecision.pass();
        }
        SpeedState state = states.get(context.context().id());
        if (state == null) {
            return InterceptDecision.pass();
        }
        state.observe(chunk);
        long now = nanoTime.getAsLong();
        if (!chunk.terminal() && state.lastEmitNanos != 0
            && now - state.lastEmitNanos < EMIT_INTERVAL_NANOS) {
            return InterceptDecision.pass();
        }
        if (state.hasOutput() && (chunk.terminal() || state.lastEmitNanos == 0
            || now - state.lastEmitNanos >= EMIT_INTERVAL_NANOS)) {
            emit(state, now, chunk.terminal());
        }
        return InterceptDecision.pass();
    }

    private InterceptDecision onAfterModel(InterceptContext context) {
        SpeedState state = states.get(context.context().id());
        if (state != null) {
            if (!state.done) {
                emit(state, nanoTime.getAsLong(), true);
            }
            states.remove(context.context().id(), state);
        }
        return InterceptDecision.pass();
    }

    private InterceptDecision onError(InterceptContext context) {
        states.remove(context.context().id());
        return InterceptDecision.pass();
    }

    private void emit(SpeedState state, long now, boolean done) {
        if (state.lastEmitNanos != 0 && !done && now - state.lastEmitNanos < EMIT_INTERVAL_NANOS) {
            return;
        }
        double elapsedSeconds = Math.max(0.001, (now - state.startNanos) / 1_000_000_000.0);
        double realtimeElapsed = state.lastEmitNanos == 0
            ? elapsedSeconds
            : Math.max(0.001, (now - state.lastEmitNanos) / 1_000_000_000.0);
        long totalUnits = state.generatedUnits();
        long deltaUnits = Math.max(0, totalUnits - state.lastUnits);
        double realtimeTps = deltaUnits / 2.0 / realtimeElapsed;
        double averageTps = totalUnits / 2.0 / elapsedSeconds;
        if (state.hasProviderTokens()) {
            realtimeTps = deltaUnits / realtimeElapsed;
            averageTps = totalUnits / elapsedSeconds;
        }
        host.emitTokenSpeed(state.generatedTokenCount(), realtimeTps, averageTps, done);
        state.lastEmitNanos = now;
        state.lastUnits = totalUnits;
        state.done = done;
    }

    private static final class SpeedState {
        private final long startNanos;
        private long generatedTokens;
        private long generatedChars;
        private long lastUnits;
        private long lastEmitNanos;
        private boolean providerTokens;
        private boolean done;

        private SpeedState(long startNanos) {
            this.startNanos = startNanos;
        }

        private void observe(StreamChunk chunk) {
            if (chunk.content() != null) {
                generatedChars += chunk.content().length();
            }
            if (chunk.reasoning() != null) {
                generatedChars += chunk.reasoning().length();
            }
            long completionTokens = chunk.usage().completionTokens();
            if (completionTokens > 0) {
                generatedTokens += completionTokens;
                providerTokens = true;
            }
        }

        private boolean hasOutput() {
            return generatedChars > 0 || generatedTokens > 0;
        }

        private boolean hasProviderTokens() {
            return providerTokens;
        }

        private long generatedUnits() {
            return providerTokens ? generatedTokens : generatedChars;
        }

        private long generatedTokenCount() {
            return providerTokens ? generatedTokens : generatedChars / 2;
        }
    }
}
