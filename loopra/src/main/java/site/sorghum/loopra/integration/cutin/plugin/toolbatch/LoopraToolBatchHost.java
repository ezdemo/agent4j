package site.sorghum.loopra.integration.cutin.plugin.toolbatch;

import site.sorghum.cutin.core.context.DefaultLoopContext;
import site.sorghum.loopra.bin.agent.model.ToolExecutionResult;
import site.sorghum.loopra.tool.AgentOutput;

/**
  * 向 cutin 插件暴露工具批次可观测性的宿主切片。
 */
public interface LoopraToolBatchHost {

    AgentOutput getOutput();

    /**
      * 当已完成工具批次需要等待沙箱审批时返回人工审批提示，
      * 批次可以正常继续时返回 {@code null}。
     */
    String suspendSandboxHITLIfPending(DefaultLoopContext context);

    void applySelfCorrection(DefaultLoopContext context, ToolExecutionResult result);
}
