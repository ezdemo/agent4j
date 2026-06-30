package site.sorghum.agent4j.bin.workflow2;

public enum StepKind {
    STEP,   // 普通步骤（LLM 自行决定如何完成）
    FORK,   // 分支步骤（LLM 从多个选项中选一个）
    HITL    // 人工审批步骤
}
