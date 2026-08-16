package site.sorghum.loopra.integration.cutin.plugin.toolbatch;

import site.sorghum.cutin.core.context.DefaultLoopContext;
import site.sorghum.cutin.core.loop.InterceptContext;
import site.sorghum.cutin.core.loop.InterceptDecision;
import site.sorghum.cutin.core.loop.InterceptPoint;
import site.sorghum.cutin.core.plugin.AgentPlugin;
import site.sorghum.cutin.core.plugin.LoopPlugin;
import site.sorghum.cutin.core.plugin.LoopRegistrar;
import site.sorghum.loopra.bin.agent.model.ToolExecutionResult;
import site.sorghum.loopra.tool.AgentOutput;
import site.sorghum.loopra.tool.LogLevel;

/**
  * 工具批次生命周期插件：批次边界对其他插件可见，
  * 超时/取消事件走同一条拦截链。
 */
@AgentPlugin(id = "loopra-tool-batch")
public final class LoopraToolBatchPlugin implements LoopPlugin {

    private final LoopraToolBatchHost host;

    public LoopraToolBatchPlugin(LoopraToolBatchHost host) {
        this.host = host;
    }

    @Override
    public String id() {
        return "loopra-tool-batch";
    }

    @Override
    public void register(LoopRegistrar registrar) {
        registrar.addInterceptor(InterceptPoint.BEFORE_TOOL_BATCH, -50, this::onBatchStart);
        registrar.addInterceptor(InterceptPoint.AFTER_TOOL_BATCH, 50, this::onBatchEnd);
        registrar.addInterceptor(InterceptPoint.ON_TOOL_TIMEOUT, 0, this::onTimeout);
        registrar.addInterceptor(InterceptPoint.ON_TOOL_CANCEL, 0, this::onCancel);
    }

    private InterceptDecision onBatchStart(InterceptContext context) {
        return InterceptDecision.pass();
    }

    private InterceptDecision onBatchEnd(InterceptContext context) {
        if (!(context.context() instanceof DefaultLoopContext defaultContext)) {
            return InterceptDecision.pass();
        }
        String hitlPrompt = host.suspendSandboxHITLIfPending(defaultContext);
        if (hitlPrompt != null) {
            return InterceptDecision.suspend(hitlPrompt);
        }
        if (context.payload() instanceof ToolExecutionResult result) {
            host.applySelfCorrection(defaultContext, result);
        }
        return InterceptDecision.pass();
    }

    private InterceptDecision onTimeout(InterceptContext context) {
        if (!(context.payload() instanceof LoopraToolBatchEvent event)) {
            return InterceptDecision.pass();
        }
        String label = event.subAgent() ? "子代理" : "工具";
        AgentOutput output = host.getOutput();
        if (output != null) {
            output.onLog(LogLevel.WARN, "[tool] " + label + "执行超时（"
                    + event.timeoutSeconds() + "s），已请求停止: " + event.toolName());
        }
        return InterceptDecision.pass();
    }

    private InterceptDecision onCancel(InterceptContext context) {
        if (!(context.payload() instanceof LoopraToolBatchEvent event)) {
            return InterceptDecision.pass();
        }
        AgentOutput output = host.getOutput();
        if (output != null && event.cancelledCount() > 0) {
            output.onLog(LogLevel.WARN, "[tool] 用户中断，已取消 " + event.cancelledCount() + " 个工具任务");
        }
        return InterceptDecision.pass();
    }
}
