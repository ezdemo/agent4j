package site.sorghum.agent4j.bin.goal;

/**
 * 步骤状态枚举。
 *
 * @author Sorghum
 */
public enum StepStatus {
    /** 待执行 */
    PENDING,
    /** 执行中 */
    IN_PROGRESS,
    /** 已完成并已记录证据 */
    DONE,
    /** 被外部条件阻塞 */
    BLOCKED,
    /** 用户手动跳过 */
    SKIPPED
}
