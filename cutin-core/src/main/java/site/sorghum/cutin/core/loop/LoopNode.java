package site.sorghum.cutin.core.loop;

/**
 * 循环图中的一个节点：id、类型与要执行的 Step。
 */
public record LoopNode(String id, NodeType type, Step step) {
}
