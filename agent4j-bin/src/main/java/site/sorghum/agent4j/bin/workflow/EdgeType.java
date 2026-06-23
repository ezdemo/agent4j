package site.sorghum.agent4j.bin.workflow;

/**
 * 边类型枚举。
 *
 * @author Sorghum
 */
public enum EdgeType {
    /** 普通依赖 */
    NORMAL,
    /** 条件为真时 */
    CONDITION_TRUE,
    /** 条件为假时 */
    CONDITION_FALSE,
    /** 默认路径 */
    DEFAULT,
    /** 条件选择边（N路分支，由 conditionResult 指定目标节点ID） */
    CONDITION_SELECT
}