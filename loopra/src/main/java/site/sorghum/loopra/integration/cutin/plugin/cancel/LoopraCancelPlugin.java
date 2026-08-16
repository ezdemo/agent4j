package site.sorghum.loopra.integration.cutin.plugin.cancel;

import site.sorghum.cutin.core.event.Hook;
import site.sorghum.cutin.core.event.LoopEvent;
import site.sorghum.cutin.core.plugin.AgentPlugin;
import site.sorghum.cutin.core.plugin.LoopPlugin;
import site.sorghum.cutin.core.plugin.LoopRegistrar;

/**
  * 把引擎的 {@code ON_CANCEL} 事件桥接给 Loopra 宿主。
 */
@AgentPlugin(id = "loopra-cancel")
public final class LoopraCancelPlugin implements LoopPlugin {

    private final LoopraCancelHost host;

    public LoopraCancelPlugin(LoopraCancelHost host) {
        this.host = host;
    }

    @Override
    public String id() {
        return "loopra-cancel";
    }

    @Override
    public void register(LoopRegistrar registrar) {
        registrar.addHook(new Hook() {
            @Override
            public String id() {
                return "loopra-cancel";
            }

            @Override
            public boolean matches(LoopEvent event) {
                return "ON_CANCEL".equals(event.type());
            }

            @Override
            public void run(LoopEvent event) {
                Object reason = event.attributes().get("reason");
                host.onCutinCancel(reason == null ? "cancelled" : String.valueOf(reason));
            }
        });
    }
}
