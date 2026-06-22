package site.sorghum.agent4j.bin.workflow;

/**
 * 工作流状态枚举。
 *
 * @author Sorghum
 */
public enum WorkflowStatus {
    /** 草稿（用户正在编辑） */
    DRAFT,
    /** 活跃（正在执行） */
    ACTIVE,
    /** 暂停 */
    PAUSED,
    /** 已完成 */
    COMPLETED,
    /** 失败 */
    FAILED
}