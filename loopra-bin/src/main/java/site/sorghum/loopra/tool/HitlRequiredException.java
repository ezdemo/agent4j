package site.sorghum.loopra.tool;

import lombok.Getter;

import java.util.Collections;
import java.util.Map;

/**
 * 工具执行中触发 HITL（Human-In-The-Loop）审批需求的异常。
 * <p>
 * 当工具检测到需要用户审批的操作（如路径越界）时抛出此异常，
 * 由 AgentLoop 捕获后进入 HITL 审批流程，而非直接返回失败。
 * </p>
 *
 * @author Sorghum
 */
@Getter
public class HitlRequiredException extends RuntimeException {

    /** 触发审批的工具名 */
    private final String toolName;
    /** 简短原因码（如 "SANDBOX_ESCAPE"） */
    private final String reason;
    /** 详细描述（给用户看的审批提示） */
    private final String details;
    /** 工具调用时的原始参数（审批通过后用于重放执行） */
    private final Map<String, Object> toolArgs;

    /**
     * @param toolName 触发审批的工具名，为 null 时使用 "unknown"
     * @param reason   简短原因码（如 "SANDBOX_ESCAPE"），为 null 时使用 "UNKNOWN"
     * @param details  详细描述（给用户看的审批提示），为 null 时使用空字符串
     * @param toolArgs 工具调用时的原始参数（审批通过后用于重放执行），为 null 时使用空 Map
     */
    public HitlRequiredException(String toolName, String reason, String details, Map<String, Object> toolArgs) {
        super("[" + (reason != null ? reason : "UNKNOWN") + "] "
                + (toolName != null ? toolName : "unknown") + ": "
                + (details != null ? details : ""));
        this.toolName = toolName;
        this.reason = reason;
        this.details = details;
        this.toolArgs = toolArgs != null ? toolArgs : Collections.emptyMap();
    }
}
