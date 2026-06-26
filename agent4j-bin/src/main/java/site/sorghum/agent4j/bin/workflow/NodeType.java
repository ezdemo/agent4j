package site.sorghum.agent4j.bin.workflow;

/**
 * 节点类型枚举。
 *
 * @author Sorghum
 */
public enum NodeType {
    /** 执行动作 */
    ACTION,
    /** 条件判断 */
    CONDITION,
    /** 并行执行 */
    PARALLEL,
    /** 人工审批 */
    HITL,
    /** 子工作流 */
    SUBFLOW,
    /** 循环控制 */
    LOOP,
    /** 开始节点 */
    START,
    /** 结束节点 */
    END
}