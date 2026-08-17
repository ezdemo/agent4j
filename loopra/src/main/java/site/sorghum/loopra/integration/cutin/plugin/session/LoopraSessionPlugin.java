package site.sorghum.loopra.integration.cutin.plugin.session;

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
 * 将 Loopra 生命周期与会话提交统一接入 cutin。
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
                host.beginCutinLoop();
                Object raw = event.attributes().get("context");
                String userMessage = raw instanceof site.sorghum.cutin.core.context.DefaultLoopContext context
                        ? String.valueOf(context.variables().getOrDefault("loopraUserMessage", ""))
                        : "";
                host.beforeTurn(userMessage);
            }
        });
        registrar.addHook(new Hook() {
            @Override
            public String id() {
                return "loopra-session-post";
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
        registrar.addInterceptor(InterceptPoint.AFTER_TURN, 1000, this::onAfterTurn);
    }

    private InterceptDecision onAfterTurn(InterceptContext context) {
        if (context.payload() instanceof LoopResult result) {
            host.afterTurn();
        }
        return InterceptDecision.pass();
    }
}
