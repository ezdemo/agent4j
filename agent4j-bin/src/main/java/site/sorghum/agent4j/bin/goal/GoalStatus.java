package site.sorghum.agent4j.bin.goal;

/**
 * 目标状态枚举。
 *
 * @author Sorghum
 */
public enum GoalStatus {
    /** 活跃：正在执行中 */
    ACTIVE,
    /** 暂停：用户手动暂停 */
    PAUSED,
    /** 已完成：所有步骤 DONE */
    COMPLETED,
    /** 失败：某步骤超重试次数 */
    FAILED
}
