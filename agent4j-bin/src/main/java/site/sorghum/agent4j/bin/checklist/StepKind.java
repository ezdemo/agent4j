package site.sorghum.agent4j.bin.checklist;

/**
 * 步骤类型枚举。
 *
 * @author Sorghum
 */
public enum StepKind {
    /** 普通步骤（LLM 自行决定如何完成） */
    STEP,
    /** 分支步骤（LLM 从多个选项中选一个） */
    FORK,
    /** 人工审批步骤 */
    HITL
}
