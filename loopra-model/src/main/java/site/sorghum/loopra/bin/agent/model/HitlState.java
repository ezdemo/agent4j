package site.sorghum.loopra.bin.agent.model;

/**
 * HITL（Human-In-The-Loop）审批状态枚举。
 * <p>
 * 从 AgentLoop 提取为独立类型。
 * </p>
 *
 * @author Sorghum
 */
public enum HitlState {
    /**
     * 无待审批
     */
    NONE,
    /**
     * 等待用户审批
     */
    PENDING,
    /**
     * 用户已批准
     */
    APPROVED,
    /**
     * 用户已拒绝
     */
    DENIED
}
