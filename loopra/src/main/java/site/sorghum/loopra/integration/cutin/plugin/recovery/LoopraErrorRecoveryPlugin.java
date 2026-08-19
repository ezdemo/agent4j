package site.sorghum.loopra.integration.cutin.plugin.recovery;

import site.sorghum.cutin.core.loop.InterceptContext;
import site.sorghum.cutin.core.loop.InterceptDecision;
import site.sorghum.cutin.core.loop.InterceptPoint;
import site.sorghum.cutin.core.model.ModelCallError;
import site.sorghum.cutin.core.plugin.AgentPlugin;
import site.sorghum.cutin.core.plugin.LoopPlugin;
import site.sorghum.cutin.core.plugin.LoopRegistrar;
import site.sorghum.loopra.bin.model.ModelApiError;

/**
  * 通过折叠历史并重试同一模型节点，从服务端确认的上下文超限中恢复。
 */
@AgentPlugin(id = "loopra-error-recovery")
public final class LoopraErrorRecoveryPlugin implements LoopPlugin {

    private static final String RECOVERY_KEY = "loopraContextRecoveryAttempts";

    private final LoopraErrorRecoveryHost host;

    public LoopraErrorRecoveryPlugin(LoopraErrorRecoveryHost host) {
        this.host = host;
    }

    @Override
    public String id() {
        return "loopra-error-recovery";
    }

    @Override
    public void register(LoopRegistrar registrar) {
        registrar.registerInterceptor(InterceptPoint.ON_MODEL_ERROR, -100, this::onModelError);
    }

    private InterceptDecision onModelError(InterceptContext context) {
        if (!(context.payload() instanceof ModelCallError error)
                || !ModelApiError.isContextLengthExceeded(error.message())) {
            return InterceptDecision.pass();
        }
        int attempts = intVariable(context, RECOVERY_KEY) + 1;
        context.context().putVariable(RECOVERY_KEY, attempts);
        if (attempts <= host.maxContextRecoveries()
                && host.compactAfterContextOverflow(attempts)) {
            return InterceptDecision.retry("context overflow recovered");
        }
        context.context().putVariable(RECOVERY_KEY, 0);
        return InterceptDecision.pass();
    }

    private static int intVariable(InterceptContext context, String key) {
        Object value = context.context().variables().get(key);
        return value instanceof Number number ? number.intValue() : 0;
    }
}
