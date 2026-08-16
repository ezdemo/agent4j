package site.sorghum.loopra.integration.cutin.plugin.session;

import site.sorghum.cutin.core.context.DefaultLoopContext;
import site.sorghum.cutin.core.event.Hook;
import site.sorghum.cutin.core.event.LoopEvent;
import site.sorghum.cutin.core.loop.InterceptContext;
import site.sorghum.cutin.core.loop.InterceptDecision;
import site.sorghum.cutin.core.loop.InterceptPoint;
import site.sorghum.cutin.core.loop.LoopResult;
import site.sorghum.cutin.core.plugin.AgentPlugin;
import site.sorghum.cutin.core.plugin.LoopPlugin;
import site.sorghum.cutin.core.plugin.LoopRegistrar;

/**
  * 在每个 cutin 回合结束后触发宿主的会话提交。
 */
@AgentPlugin(id = "loopra-session")
public final class LoopraSessionPlugin implements LoopPlugin {

    private final LoopraSessionHost host;

    public LoopraSessionPlugin(LoopraSessionHost host) {
        this.host = host;
    }

    @Override
    public String id() {
        return "loopra-session";
    }

    @Override
    public void register(LoopRegistrar registrar) {
        registrar.addHook(new Hook() {
            @Override
            public String id() {
                return "loopra-session-before";
            }

            @Override
            public boolean matches(LoopEvent event) {
                return "PRE_LOOP".equals(event.type());
            }

            @Override
            public void run(LoopEvent event) {
                Object raw = event.attributes().get("context");
                if (raw instanceof DefaultLoopContext context) {
                    host.beforeTurn(context);
                }
            }
        });
        registrar.addInterceptor(InterceptPoint.AFTER_TURN, 1000, this::onAfterTurn);
    }

    private InterceptDecision onAfterTurn(InterceptContext context) {
        if (context.payload() instanceof LoopResult result) {
            host.afterTurn(result);
        }
        return InterceptDecision.pass();
    }
}
