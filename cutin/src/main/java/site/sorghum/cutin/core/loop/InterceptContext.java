package site.sorghum.cutin.core.loop;

import site.sorghum.cutin.core.context.LoopContext;

/**
 * 一次拦截的上下文：当前拦截点、节点信息、循环上下文与载荷。
 */
public record InterceptContext(
    InterceptPoint point,
    String nodeId,
    LoopNode node,
    LoopContext context,
    Object payload
) {

    /** 快捷构造一个不带载荷的拦截上下文。 */
    public InterceptContext(InterceptPoint point, String nodeId, LoopNode node, LoopContext context) {
        this(point, nodeId, node, context, null);
    }
}
