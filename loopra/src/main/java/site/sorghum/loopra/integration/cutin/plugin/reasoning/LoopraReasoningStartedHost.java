package site.sorghum.loopra.integration.cutin.plugin.reasoning;

import site.sorghum.loopra.tool.AgentOutput;

/** 思考开始状态插件所依赖的最小输出能力。 */
public interface LoopraReasoningStartedHost {

    AgentOutput getOutput();
}
