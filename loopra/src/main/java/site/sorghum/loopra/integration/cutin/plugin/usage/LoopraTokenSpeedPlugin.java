package site.sorghum.loopra.integration.cutin.plugin.usage;

import site.sorghum.cutin.core.context.Usage;
import site.sorghum.cutin.core.loop.*;
import site.sorghum.cutin.core.plugin.AgentPlugin;
import site.sorghum.cutin.core.plugin.LoopPlugin;
import site.sorghum.cutin.core.plugin.LoopRegistrar;

@AgentPlugin(id = "loopra-token-speed")
public final class LoopraTokenSpeedPlugin implements LoopPlugin {

    private static final String START_NANOS_KEY = "loopraTokenSpeedStartNanos";
    private static final String BASE_COMPLETION_KEY = "loopraTokenSpeedBaseCompletion";
    private static final String LAST_EMIT_NANOS_KEY = "loopraTokenSpeedLastEmitNanos";
    private static final String CHAR_COUNT_KEY = "loopraTokenSpeedCharCount";

    private static final long EMIT_INTERVAL_NANOS = 200_000_000L;

    private final LoopraTokenSpeedHost host;

    public LoopraTokenSpeedPlugin(LoopraTokenSpeedHost host) {
        this.host = host;
    }

    @Override
    public String id() {
        return "loopra-token-speed";
    }

    @Override
    public void register(LoopRegistrar registrar) {
        registrar.addInterceptor(InterceptPoint.BEFORE_MODEL, -900, this::onBeforeModel);
        registrar.addInterceptor(InterceptPoint.ON_MODEL_STREAM, 900, this::onStream);
        registrar.addInterceptor(InterceptPoint.AFTER_MODEL, 900, this::onAfterModel);
    }

    private InterceptDecision onBeforeModel(InterceptContext context) {
        context.context().putVariable(START_NANOS_KEY, System.nanoTime());
        context.context().putVariable(BASE_COMPLETION_KEY, context.context().usage().completionTokens());
        context.context().putVariable(LAST_EMIT_NANOS_KEY, 0L);
        context.context().putVariable(CHAR_COUNT_KEY, 0L);
        return InterceptDecision.pass();
    }

    private InterceptDecision onStream(InterceptContext context) {
        Object payload = context.payload();
        if (!(payload instanceof site.sorghum.cutin.core.model.StreamChunk chunk)) {
            return InterceptDecision.pass();
        }
        int deltaChars = 0;
        if (chunk.content() != null) deltaChars += chunk.content().length();
        if (chunk.reasoning() != null) deltaChars += chunk.reasoning().length();
        if (deltaChars > 0) {
            Object cur = context.context().variables().getOrDefault(CHAR_COUNT_KEY, 0L);
            long count = cur instanceof Number n ? n.longValue() : 0L;
            context.context().putVariable(CHAR_COUNT_KEY, count + deltaChars);
        }
        if (!chunk.terminal() && (chunk.content() == null || chunk.content().isEmpty())
            && (chunk.reasoning() == null || chunk.reasoning().isEmpty())
            && chunk.toolCalls().isEmpty()) {
            return InterceptDecision.pass();
        }
        long now = System.nanoTime();
        Object lastRaw = context.context().variables().getOrDefault(LAST_EMIT_NANOS_KEY, 0L);
        long lastEmit = lastRaw instanceof Number n ? n.longValue() : 0L;
        if (lastEmit != 0 && now - lastEmit < EMIT_INTERVAL_NANOS && !chunk.terminal()) {
            return InterceptDecision.pass();
        }
        context.context().putVariable(LAST_EMIT_NANOS_KEY, now);
        emit(context, chunk.terminal());
        return InterceptDecision.pass();
    }

    private InterceptDecision onAfterModel(InterceptContext context) {
        emit(context, true);
        return InterceptDecision.pass();
    }

    private void emit(InterceptContext context, boolean done) {
        Object startRaw = context.context().variables().get(START_NANOS_KEY);
        Object baseRaw = context.context().variables().get(BASE_COMPLETION_KEY);
        if (!(startRaw instanceof Number) || !(baseRaw instanceof Number)) {
            return;
        }
        long startNanos = ((Number) startRaw).longValue();
        long baseCompletion = ((Number) baseRaw).longValue();
        Usage total = context.context().usage();
        long deltaCompletion = Math.max(0, total.completionTokens() - baseCompletion);
        Object charRaw = context.context().variables().getOrDefault(CHAR_COUNT_KEY, 0L);
        long charCount = charRaw instanceof Number n ? n.longValue() : 0L;
        double elapsedSec = Math.max(0.001, (System.nanoTime() - startNanos) / 1_000_000_000.0);
        double tps;
        if (deltaCompletion > 0) {
            tps = deltaCompletion / elapsedSec;
        } else if (charCount > 0) {
            tps = (charCount / 2.0) / elapsedSec;
        } else {
            tps = 0;
        }
        if (!done && tps == 0) return;
        host.emitTokenSpeed(deltaCompletion, tps, done);
    }
}
