package site.sorghum.loopra.integration.cutin.plugin.plan;

import site.sorghum.cutin.core.event.Hook;
import site.sorghum.cutin.core.event.LoopEvent;
import site.sorghum.cutin.core.plugin.AgentPlugin;
import site.sorghum.cutin.core.plugin.LoopPlugin;
import site.sorghum.cutin.core.plugin.LoopRegistrar;

/**
  * 把待定计划持久化变为 cutin 插件边界。
 */
@AgentPlugin(id = "loopra-plan")
public final class LoopraPlanPlugin implements LoopPlugin {

    private final LoopraPlanHost host;

    public LoopraPlanPlugin(LoopraPlanHost host) {
        this.host = host;
    }

    @Override
    public String id() {
        return "loopra-plan";
    }

    @Override
    public void register(LoopRegistrar registrar) {
        registrar.registerHook(new Hook() {
            @Override
            public String id() {
                return "loopra-plan-persist";
            }

            @Override
            public boolean matches(LoopEvent event) {
                return LoopraPlanHost.PLAN_SUBMITTED.equals(event.type())
                        || LoopraPlanHost.PLAN_CLEARED.equals(event.type());
            }

            @Override
            public void run(LoopEvent event) {
                Object raw = event.attributes().get("plan");
                String plan = raw == null || String.valueOf(raw).isBlank()
                        ? null
                        : String.valueOf(raw);
                host.persistPendingPlan(plan);
            }
        });
    }
}
