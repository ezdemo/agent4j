package site.sorghum.loopra.integration.cutin.plugin.usage;

import site.sorghum.cutin.core.context.Usage;
import site.sorghum.cutin.core.loop.InterceptContext;
import site.sorghum.cutin.core.loop.InterceptDecision;
import site.sorghum.cutin.core.loop.InterceptPoint;
import site.sorghum.cutin.core.loop.NodeType;
import site.sorghum.cutin.core.plugin.AgentPlugin;
import site.sorghum.cutin.core.plugin.LoopPlugin;
import site.sorghum.cutin.core.plugin.LoopRegistrar;

/**
  * 把每个模型步骤新增的 token 用量上报给 Loopra 宿主。
 */
@AgentPlugin(id = "loopra-usage")
public final class LoopraUsagePlugin implements LoopPlugin {

    private static final String LAST_REPORTED_KEY = "loopraLastReportedUsage";

    private final LoopraUsageHost host;

    public LoopraUsagePlugin(LoopraUsageHost host) {
        this.host = host;
    }

    @Override
    public String id() {
        return "loopra-usage";
    }

    @Override
    public void register(LoopRegistrar registrar) {
        registrar.addInterceptor(InterceptPoint.AFTER_STEP, 1000, this::reportModelUsage);
    }

    private InterceptDecision reportModelUsage(InterceptContext context) {
        if (context.node() == null || context.node().type() != NodeType.MODEL) {
            return InterceptDecision.pass();
        }
        Usage total = context.context().usage();
        Object raw = context.context().variables().getOrDefault(LAST_REPORTED_KEY, Usage.ZERO);
        Usage previous = raw instanceof Usage usage ? usage : Usage.ZERO;
        if (total.equals(previous)) {
            return InterceptDecision.pass();
        }
        context.context().putVariable(LAST_REPORTED_KEY, total);
        host.reportCutinUsage(new Usage(
            total.promptTokens() - previous.promptTokens(),
            total.completionTokens() - previous.completionTokens(),
            total.costMicros() - previous.costMicros(),
            total.cacheReadTokens() - previous.cacheReadTokens(),
            total.cacheCreationTokens() - previous.cacheCreationTokens()
        ));
        return InterceptDecision.pass();
    }
}
