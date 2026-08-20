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
import site.sorghum.loopra.integration.cutin.plugin.preflight.LoopraPreflight;

/**
 * 将 Loopra 生命周期与会话提交统一接入 cutin。
 */
@AgentPlugin(id = "loopra-session", remark = "管理会话级状态与生命周期事件。")
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
    public void  register(LoopRegistrar registrar) {
        registrar.registerHook(new Hook() {
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
            }
        });
        registrar.registerHook(new Hook() {
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
        registrar.registerInterceptor(InterceptPoint.AFTER_STEP, 1000, this::onAfterSanitize);
        registrar.registerInterceptor(InterceptPoint.AFTER_TURN, 1000, this::onAfterTurn);
    }

    /**
     * 用户消息清洗（preflight-sanitize 节点）完成后触发回合开始回调：
     * 此时 {@code loopraUserMessage} 变量已由 sanitizer 写入，
     * 标题生成/会话名分配基于清洗后的消息文本。
     * HITL 人工审批重入从检查点直接进入 tool 节点，不经过 sanitize 节点，天然不会重复触发。
     */
    private InterceptDecision onAfterSanitize(InterceptContext context) {
        if (!LoopraPreflight.SANITIZE_NODE.equals(context.nodeId())) {
            return InterceptDecision.pass();
        }
        Object raw = context.context().variables().get("loopraUserMessage");
        host.beforeTurn(raw == null ? "" : String.valueOf(raw));
        return InterceptDecision.pass();
    }

    private InterceptDecision onAfterTurn(InterceptContext context) {
        if (context.payload() instanceof LoopResult result) {
            host.afterTurn();
        }
        return InterceptDecision.pass();
    }
}
