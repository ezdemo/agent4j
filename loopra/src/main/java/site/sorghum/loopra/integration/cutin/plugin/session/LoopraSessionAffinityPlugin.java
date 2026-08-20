package site.sorghum.loopra.integration.cutin.plugin.session;

import site.sorghum.cutin.core.loop.InterceptContext;
import site.sorghum.cutin.core.loop.InterceptDecision;
import site.sorghum.cutin.core.loop.InterceptPoint;
import site.sorghum.cutin.core.model.ModelCallRequest;
import site.sorghum.cutin.core.plugin.AgentPlugin;
import site.sorghum.cutin.core.plugin.LoopPlugin;
import site.sorghum.cutin.core.plugin.LoopRegistrar;

import java.util.HashMap;

/** 将宿主会话亲和键作为请求级选项注入，不污染 AgentLoop 的模型请求构造。 */
@AgentPlugin(id = "loopra-session-affinity", order = -950, remark = "为请求注入会话亲和信息，保持多轮调用路由一致。")
public final class LoopraSessionAffinityPlugin implements LoopPlugin {
    private final LoopraSessionAffinityHost host;

    public LoopraSessionAffinityPlugin(LoopraSessionAffinityHost host) {
        this.host = host;
    }

    @Override
    public String id() {
        return "loopra-session-affinity";
    }

    @Override
    public void register(LoopRegistrar registrar) {
        registrar.registerInterceptor(InterceptPoint.BEFORE_MODEL, -950, this::injectAffinity);
    }

    private InterceptDecision injectAffinity(InterceptContext context) {
        if (!(context.payload() instanceof ModelCallRequest request)) {
            return InterceptDecision.pass();
        }
        String affinity = host.sessionAffinity();
        if (affinity == null || affinity.isBlank() || request.options().containsKey("sessionAffinity")) {
            return InterceptDecision.pass();
        }
        HashMap<String, Object> options = new HashMap<>(request.options());
        options.put("sessionAffinity", affinity);
        return InterceptDecision.replace(new ModelCallRequest(
            request.modelId(),
            request.messages(),
            request.tools(),
            options
        ));
    }
}
