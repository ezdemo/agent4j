package site.sorghum.cutin.core.loop;

/** 循环图中的一个节点：id、类型、说明与要执行的 Step。 */
public record LoopNode(String id, NodeType type, String remark, Step step) {

    public LoopNode {
        remark = remark == null ? "" : remark;
    }
}
