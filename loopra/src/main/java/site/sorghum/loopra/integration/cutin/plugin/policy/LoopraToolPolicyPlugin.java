package site.sorghum.loopra.integration.cutin.plugin.policy;

import org.noear.snack4.ONode;
import org.noear.solon.ai.chat.tool.FunctionTool;
import site.sorghum.cutin.core.loop.InterceptContext;
import site.sorghum.cutin.core.loop.InterceptDecision;
import site.sorghum.cutin.core.loop.InterceptPoint;
import site.sorghum.cutin.core.plugin.AgentPlugin;
import site.sorghum.cutin.core.plugin.LoopPlugin;
import site.sorghum.cutin.core.plugin.LoopRegistrar;
import site.sorghum.cutin.core.tool.ToolCall;
import site.sorghum.loopra.bin.agent.resilient.StormBreaker;
import site.sorghum.loopra.bin.tool.ToolMetadata;

/**
  * Loopra 面向工具的策略：工具执行前的计划模式只读约束与 StormBreaker 抑制。
 */
@AgentPlugin(id = "loopra-tool-policy", remark = "按安全策略检查和约束工具调用。")
public final class LoopraToolPolicyPlugin implements LoopPlugin {

    private final LoopraPolicyHost host;

    public LoopraToolPolicyPlugin(LoopraPolicyHost host) {
        this.host = host;
    }

    @Override
    public String id() {
        return "loopra-tool-policy";
    }

    @Override
    public void register(LoopRegistrar registrar) {
        registrar.registerInterceptor(InterceptPoint.BEFORE_TOOL, -100, this::guardToolCall);
    }

    private InterceptDecision guardToolCall(InterceptContext context) {
        if (!(context.payload() instanceof ToolCall call)) {
            return InterceptDecision.pass();
        }
        FunctionTool tool = host.getToolRegistry().get(call.toolId());
        if (host.isPlanMode() && tool != null && !ToolMetadata.isReadOnly(tool)) {
            return InterceptDecision.replace(host.rejectCutinTool(
                    call,
                    "当前处于计划模式，仅允许只读工具；本次调用已被拒绝。请继续使用只读工具探索，完成后用 submit_plan 提交计划。",
                    "plan_mode"
            ));
        }
        if (tool != null && !ToolMetadata.isStormExempt(tool)) {
            String argumentsJson = ONode.ofBean(call.arguments()).toJson();
            StormBreaker.SuppressResult suppression = host.stormBreaker().inspect(
                    call.toolId(), argumentsJson, ToolMetadata.isReadOnly(tool));
            if (suppression.suppressed()) {
                host.markCutinStormSuppressed();
                return InterceptDecision.replace(host.rejectCutinTool(
                        call, suppression.reason(), "storm"
                ));
            }
        }
        return InterceptDecision.pass();
    }
}
