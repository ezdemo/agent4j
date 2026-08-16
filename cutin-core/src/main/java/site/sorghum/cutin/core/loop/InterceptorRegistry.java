package site.sorghum.cutin.core.loop;

import site.sorghum.cutin.core.context.LoopContext;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 拦截器注册表：按拦截点维护有序链并依次执行。
 *
 * <p>同一拦截点按 order 升序执行；order 相同时按注册顺序稳定执行。
 * 链中任一拦截器返回终止决策（如 RETRY/SUSPEND/ABORT/GOTO）后停止执行后续拦截器。</p>
 */
public final class InterceptorRegistry {

    /** 拦截点到注册列表的映射。 */
    private final Map<InterceptPoint, List<Registration>> chains = new EnumMap<>(InterceptPoint.class);
    /** 注册序号，用于保持同 order 下的稳定顺序。 */
    private final AtomicLong sequence = new AtomicLong();

    /** 在指定拦截点注册一个带顺序的拦截器。 */
    public void add(InterceptPoint point, int order, LoopInterceptor interceptor) {
        chains.computeIfAbsent(point, ignored -> new ArrayList<>())
            .add(new Registration(order, sequence.incrementAndGet(), interceptor));
        chains.get(point).sort(Registration::compareTo);
    }

    /**
     * 运行指定拦截点的整条链。
     *
     * <p>上下文与载荷会随链传递：MODIFIED 决策可更新上下文或载荷，
     * 一旦出现终止决策立即中断并返回。</p>
     */
    public InterceptionResult run(InterceptPoint point, InterceptContext interceptContext) {
        List<Registration> registrations = chains.getOrDefault(point, List.of());
        LoopContext current = interceptContext.context();
        InterceptDecision decision = InterceptDecision.pass();
        Object payload = interceptContext.payload();

        for (Registration registration : registrations) {
            InterceptDecision next = registration.interceptor().intercept(
                new InterceptContext(
                    point,
                    interceptContext.nodeId(),
                    interceptContext.node(),
                    current,
                    payload
                )
            );
            if (next.isModified() && next.context() != null) {
                current = next.context();
            }
            if (next.payload() != null) {
                payload = next.payload();
            }
            if (next.isTerminal()) {
                decision = next;
                break;
            }
        }
        return new InterceptionResult(current, decision, payload);
    }

    /** 返回指定拦截点已注册的拦截器数量。 */
    public int size(InterceptPoint point) {
        return chains.getOrDefault(point, List.of()).size();
    }

    /** 一条拦截器注册记录，按 order 与注册序号排序。 */
    private record Registration(int order, long sequence, LoopInterceptor interceptor)
        implements Comparable<Registration> {

        /** 按 order 排序，order 相同时按注册序号排序。 */
        @Override
        public int compareTo(Registration other) {
            int orderCompare = Integer.compare(order, other.order);
            return orderCompare != 0 ? orderCompare : Long.compare(sequence, other.sequence);
        }
    }
}
