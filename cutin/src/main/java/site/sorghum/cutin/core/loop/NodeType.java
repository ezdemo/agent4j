package site.sorghum.cutin.core.loop;

/**
 * 循环节点类型，供引擎与插件识别节点语义。
 */
public enum NodeType {
    /** 模型调用节点。 */
    MODEL,
    /** 工具调用节点。 */
    TOOL,
    /** 纯代码逻辑节点。 */
    CODE,
    /** 条件判断节点。 */
    CONDITION,
    /** 子代理节点。 */
    SUBAGENT,
    /** 等待节点。 */
    WAIT,
    /** 输出节点，通常是循环结束的出口。 */
    OUTPUT
}
