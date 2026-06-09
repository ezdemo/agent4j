package site.sorghum.agent4j.bin.goal;

/**
 * 步骤状态枚举。
 */
public enum StepStatus {
    /** 待执行 */
    PENDING,
    /** 执行中 */
    IN_PROGRESS,
    /** 已完成 */
    DONE,
    /** 失败（可重试） */
    FAILED,
    /** 用户手动跳过 */
    SKIPPED
}
