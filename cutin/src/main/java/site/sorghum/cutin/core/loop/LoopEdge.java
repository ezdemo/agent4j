package site.sorghum.cutin.core.loop;

/** 循环图中的一条显式流转边。 */
public record LoopEdge(String fromNodeId, String toNodeId, String remark) {

    public LoopEdge {
        remark = remark == null ? "" : remark;
    }
}
