package site.sorghum.loopra.integration.cutin.plugin.compaction;

import site.sorghum.cutin.core.context.DefaultLoopContext;
import site.sorghum.cutin.core.loop.InterceptContext;
import site.sorghum.cutin.core.loop.InterceptDecision;
import site.sorghum.cutin.core.loop.InterceptPoint;
import site.sorghum.cutin.core.plugin.AgentPlugin;
import site.sorghum.cutin.core.plugin.LoopPlugin;
import site.sorghum.cutin.core.plugin.LoopRegistrar;

/**
  * 在每次模型请求前执行 Loopra 的消息准备与上下文折叠。
 */
@AgentPlugin(id = "loopra-compaction")
public final class LoopraCompactionPlugin implements LoopPlugin {

    private final LoopraCompactionHost host;

    public LoopraCompactionPlugin(LoopraCompactionHost host) {
        this.host = host;
    }

    @Override
    public String id() {
        return "loopra-compaction";
    }

    @Override
    public void register(LoopRegistrar registrar) {
        registrar.registerInterceptor(InterceptPoint.BEFORE_MODEL, -200, this::prepareContext);
    }

    private InterceptDecision prepareContext(InterceptContext context) {
        if (!(context.context() instanceof DefaultLoopContext defaultContext)) {
            return InterceptDecision.pass();
        }
        Object raw = context.context().variables().getOrDefault("loopraStep", 0);
        int step = raw instanceof Number number ? number.intValue() : 0;
        host.prepareCutinMessages(defaultContext, step);
        return InterceptDecision.modified(defaultContext);
    }
}
