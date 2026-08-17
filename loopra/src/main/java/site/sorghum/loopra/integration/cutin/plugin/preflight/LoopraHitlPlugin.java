package site.sorghum.loopra.integration.cutin.plugin.preflight;

import site.sorghum.cutin.core.context.LoopContext;
import site.sorghum.cutin.core.loop.StepResult;
import site.sorghum.cutin.core.plugin.AgentPlugin;
import site.sorghum.cutin.core.plugin.LoopPlugin;
import site.sorghum.cutin.core.plugin.LoopRegistrar;
import site.sorghum.loopra.bin.agent.model.HitlState;

import java.io.IOException;

/** 在主图内处理上一轮 Cutin HITL 的审批或拒绝结果。 */
@AgentPlugin(id = "loopra-preflight-hitl")
public final class LoopraHitlPlugin implements LoopPlugin {

    private final LoopraPreflightHost host;

    public LoopraHitlPlugin(LoopraPreflightHost host) {
        this.host = host;
    }

    @Override
    public String id() {
        return "loopra-preflight-hitl";
    }

    public StepResult execute(LoopContext context) {
        HitlState state = host.hitlState();
        if (state != HitlState.APPROVED && state != HitlState.DENIED) {
            return StepResult.Continue.INSTANCE;
        }
        if (!host.hasSuspendedCutin()) {
            context.putArtifact(LoopraPreflight.ERROR_ARTIFACT,
                new IOException("[loop] cutin HITL 恢复状态丢失"));
            return new StepResult.Goto(LoopraPreflight.OUTPUT_NODE);
        }
        try {
            String result = state == HitlState.APPROVED
                ? host.resumeApprovedTurn()
                : host.rejectTurn();
            context.putVariable(LoopraPreflight.RESULT_VARIABLE, result);
        } catch (IOException exception) {
            context.putArtifact(LoopraPreflight.ERROR_ARTIFACT, exception);
        }
        return new StepResult.Goto(LoopraPreflight.OUTPUT_NODE);
    }

    @Override
    public void register(LoopRegistrar registrar) {
    }
}
