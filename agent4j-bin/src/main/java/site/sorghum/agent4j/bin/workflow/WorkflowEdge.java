package site.sorghum.agent4j.bin.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WorkflowEdge — 工作流边。
 * <p>
 * 表示节点间的依赖关系，支持条件依赖。
 * </p>
 *
 * @author Sorghum
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowEdge {
    /** 边ID */
    private String id;
    /** 源节点ID */
    private String from;
    /** 目标节点ID */
    private String to;
    /** 条件表达式（可选） */
    private String condition;
    /** 边类型 */
    private EdgeType type;

    /**
     * 判断是否为条件边。
     */
    public boolean isConditional() {
        return type == EdgeType.CONDITION_TRUE || type == EdgeType.CONDITION_FALSE;
    }

    /**
     * 判断是否为普通边。
     */
    public boolean isNormal() {
        return type == EdgeType.NORMAL || type == EdgeType.DEFAULT;
    }
}