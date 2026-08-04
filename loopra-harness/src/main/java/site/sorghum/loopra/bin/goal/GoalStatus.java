package site.sorghum.loopra.bin.goal;

/**
 * 目标状态枚举。
 *
 * @author Sorghum
 */
public enum GoalStatus {
    /** 活跃：允许推进步骤 */
    ACTIVE,
    /** 暂停：用户手动暂停 */
    PAUSED,
    /** 阻塞：需要用户或外部条件才能继续 */
    BLOCKED,
    /** 已完成：所有步骤已验证 */
    COMPLETED,
    /** 已取消：用户明确放弃本目标 */
    CANCELLED
}
