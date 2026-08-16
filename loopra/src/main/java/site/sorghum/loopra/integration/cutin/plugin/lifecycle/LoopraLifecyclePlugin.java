package site.sorghum.loopra.integration.cutin.plugin.lifecycle;

import site.sorghum.cutin.core.event.Hook;
import site.sorghum.cutin.core.event.LoopEvent;
import site.sorghum.cutin.core.plugin.AgentPlugin;
import site.sorghum.cutin.core.plugin.LoopPlugin;
import site.sorghum.cutin.core.plugin.LoopRegistrar;

/**
  * 把 Loopra 的回合重置与收尾接入 cutin 的 PRE_LOOP / POST_LOOP 事件。
 */
@AgentPlugin(id = "loopra-lifecycle")
public final class LoopraLifecyclePlugin implements LoopPlugin {

    private final LoopraLifecycleHost host;

    public LoopraLifecyclePlugin(LoopraLifecycleHost host) {
        this.host = host;
    }

    @Override
    public String id() {
        return "loopra-lifecycle";
    }

    @Override
    public void register(LoopRegistrar registrar) {
        registrar.addHook(new Hook() {
            @Override
            public String id() {
                return "loopra-lifecycle-pre";
            }

            @Override
            public boolean matches(LoopEvent event) {
                return "PRE_LOOP".equals(event.type());
            }

            @Override
            public void run(LoopEvent event) {
                host.beginCutinLoop();
            }
        });
        registrar.addHook(new Hook() {
            @Override
            public String id() {
                return "loopra-lifecycle-post";
            }

            @Override
            public boolean matches(LoopEvent event) {
                return "POST_LOOP".equals(event.type());
            }

            @Override
            public void run(LoopEvent event) {
                host.endCutinLoop();
            }
        });
    }
}
