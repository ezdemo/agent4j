package site.sorghum.loopra.bin.checklist;

/** 检查项状态：待执行、执行中、已完成、已跳过、失败。 */
public enum StepStatus {
    PENDING,
    RUNNING,
    DONE,
    SKIPPED,
    FAILED
}
