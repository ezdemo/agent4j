package site.sorghum.loopra.integration.cutin.plugin.policy;

import site.sorghum.cutin.core.context.DefaultLoopContext;
import site.sorghum.cutin.core.model.ModelResponse;
import site.sorghum.cutin.core.tool.ToolCall;
import site.sorghum.cutin.core.tool.ToolResult;
import site.sorghum.loopra.bin.agent.model.PreparedMessages;
import site.sorghum.loopra.bin.agent.resilient.ReasonBreaker;
import site.sorghum.loopra.bin.agent.resilient.StormBreaker;
import site.sorghum.loopra.bin.model.LoopraModelProvider;
import site.sorghum.loopra.bin.tool.ToolRegistry;
import site.sorghum.loopra.tool.AgentOutput;

/**
  * Loopra 策略插件所依赖的 AgentLoop 切片。
 * <p>
  * 把它放到接口之后，策略插件就可以在独立 cutin 插件装配中运行，
  * 无需直接导入 AgentLoop。
 * </p>
 */
public interface LoopraPolicyHost {

    PreparedMessages prepareCutinMessages(DefaultLoopContext context, int step);

    AgentOutput getOutput();

    ReasonBreaker reasonBreaker();

    LoopraModelProvider getModelProvider();

    String interceptHITLFromCutin(ModelResponse response);

    ToolRegistry getToolRegistry();

    default site.sorghum.cutin.core.tool.ToolRegistry getCutinTools() { return null; }

    boolean isPlanMode();

    ToolResult rejectCutinTool(ToolCall call, String message, String reason);

    StormBreaker stormBreaker();

    void markCutinStormSuppressed();

    void injectReasonBreakReminder();
}
