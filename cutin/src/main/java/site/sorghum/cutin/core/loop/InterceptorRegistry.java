package site.sorghum.cutin.core.loop;

import site.sorghum.cutin.core.context.LoopContext;
import site.sorghum.cutin.core.plugin.Registration;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/** 拦截器注册表：按拦截点维护有序链并依次执行。 */
public final class InterceptorRegistry {

    private final Map<InterceptPoint, CopyOnWriteArrayList<RegistrationEntry>> chains = new EnumMap<>(InterceptPoint.class);
    private final AtomicLong sequence = new AtomicLong();

    /** 注册一个带顺序的拦截器，并返回可注销句柄。 */
    public synchronized void add(InterceptPoint point, int order, LoopInterceptor interceptor) {
        register(point, order, interceptor);
    }

    public synchronized Registration register(InterceptPoint point, int order, LoopInterceptor interceptor) {
        CopyOnWriteArrayList<RegistrationEntry> chain = chains.computeIfAbsent(point, ignored -> new CopyOnWriteArrayList<>());
        RegistrationEntry entry = new RegistrationEntry(order, sequence.incrementAndGet(), interceptor);
        chain.add(entry);
        chain.sort(RegistrationEntry::compareTo);
        return new Registration() {
            private boolean closed;

            @Override
            public synchronized void close() {
                if (!closed) {
                    closed = true;
                    chain.remove(entry);
                }
            }
        };
    }

    /** 运行指定拦截点的整条链。 */
    public InterceptionResult run(InterceptPoint point, InterceptContext interceptContext) {
        List<RegistrationEntry> registrations = chains.getOrDefault(point, new CopyOnWriteArrayList<>());
        LoopContext current = interceptContext.context();
        InterceptDecision decision = InterceptDecision.pass();
        Object payload = interceptContext.payload();

        for (RegistrationEntry registration : registrations) {
            InterceptDecision next = registration.interceptor().intercept(
                new InterceptContext(point, interceptContext.nodeId(), interceptContext.node(), current, payload)
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

    public int size(InterceptPoint point) {
        return chains.getOrDefault(point, new CopyOnWriteArrayList<>()).size();
    }

    private record RegistrationEntry(int order, long sequence, LoopInterceptor interceptor)
        implements Comparable<RegistrationEntry> {
        @Override
        public int compareTo(RegistrationEntry other) {
            int orderCompare = Integer.compare(order, other.order);
            return orderCompare != 0 ? orderCompare : Long.compare(sequence, other.sequence);
        }
    }
}
