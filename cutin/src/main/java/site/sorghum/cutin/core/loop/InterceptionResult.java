package site.sorghum.cutin.core.loop;

import site.sorghum.cutin.core.context.LoopContext;

/**
 * 一次拦截链执行后的结果：最终上下文、决策与载荷。
 */
public record InterceptionResult(
    LoopContext context,
    InterceptDecision decision,
    Object payload
) {

    /** 快捷构造一个不带载荷的拦截结果。 */
    public InterceptionResult(LoopContext context, InterceptDecision decision) {
        this(context, decision, null);
    }
}
