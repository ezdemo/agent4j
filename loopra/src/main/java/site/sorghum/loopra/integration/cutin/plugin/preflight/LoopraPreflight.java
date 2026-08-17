package site.sorghum.loopra.integration.cutin.plugin.preflight;

import site.sorghum.cutin.core.context.LoopContext;
import site.sorghum.loopra.bin.agent.model.UserMessage;

/** Loopra 主图前置阶段使用的节点与上下文键。 */
public final class LoopraPreflight {

    public static final String SANITIZE_NODE = "preflight-sanitize";
    public static final String HITL_NODE = "preflight-hitl";
    public static final String USER_MESSAGE_NODE = "preflight-user-message";
    public static final String OUTPUT_NODE = "output";
    public static final String INPUT_ARTIFACT = "loopraTurnInput";
    public static final String RESULT_VARIABLE = "loopraTurnResult";
    public static final String ERROR_ARTIFACT = "loopraTurnError";

    private LoopraPreflight() {
    }

    public static UserMessage input(LoopContext context) {
        Object input = context.artifacts().get(INPUT_ARTIFACT);
        return input instanceof UserMessage message ? message : null;
    }
}
