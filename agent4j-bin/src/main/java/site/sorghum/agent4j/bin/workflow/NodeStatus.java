package site.sorghum.agent4j.bin.workflow;

/**
 * 节点状态枚举。
 *
 * @author Sorghum
 */
public enum NodeStatus {
    /** 待执行 */
    PENDING,
    /** 就绪（所有依赖已满足） */
    READY,
    /** 执行中 */
    RUNNING,
    /** 已完成 */
    DONE,
    /** 失败 */
    FAILED,
    /** 跳过 */
    SKIPPED,
    /** 等待中（如等待人工审批） */
    WAITING,
    /** 被阻塞 */
    BLOCKED
}