package site.sorghum.loopra.integration.cutin.plugin.reasoning;

import site.sorghum.cutin.core.loop.InterceptContext;
import site.sorghum.cutin.core.loop.InterceptDecision;
import site.sorghum.cutin.core.loop.InterceptPoint;
import site.sorghum.cutin.core.model.ModelStreamPhase;
import site.sorghum.cutin.core.model.StreamChunk;
import site.sorghum.cutin.core.plugin.AgentPlugin;
import site.sorghum.cutin.core.plugin.LoopPlugin;
import site.sorghum.cutin.core.plugin.LoopRegistrar;

/** 把 Cutin 归一化的思考开始阶段转换为用户可见状态，不泄露不可展示内容。 */
@AgentPlugin(id = "loopra-reasoning-started", remark = "在模型开始思考时通知前端，显示思考状态。")
public final class LoopraReasoningStartedPlugin implements LoopPlugin {

    private static final String EMITTED_VARIABLE = "loopraReasoningStartedEmitted";

    private final LoopraReasoningStartedHost host;

    public LoopraReasoningStartedPlugin(LoopraReasoningStartedHost host) {
        this.host = host;
    }

    @Override
    public String id() {
        return "loopra-reasoning-started";
    }

    @Override
    public void register(LoopRegistrar registrar) {
        registrar.registerInterceptor(InterceptPoint.BEFORE_MODEL, 0, this::reset);
        registrar.registerInterceptor(InterceptPoint.ON_MODEL_STREAM, -10, this::emitReasoningStarted);
    }

    private InterceptDecision reset(InterceptContext context) {
        context.context().putVariable(EMITTED_VARIABLE, false);
        return InterceptDecision.pass();
    }

    private InterceptDecision emitReasoningStarted(InterceptContext context) {
        if (!(context.payload() instanceof StreamChunk chunk)
            || !chunk.phases().contains(ModelStreamPhase.REASONING_STARTED)
            || Boolean.TRUE.equals(context.context().variables().get(EMITTED_VARIABLE))) {
            return InterceptDecision.pass();
        }
        context.context().putVariable(EMITTED_VARIABLE, true);
        try {
            host.getOutput().onReasoningStarted();
        } catch (Exception ignored) {
            // 输出连接断开不影响模型调用。
        }
        return InterceptDecision.pass();
    }
}
