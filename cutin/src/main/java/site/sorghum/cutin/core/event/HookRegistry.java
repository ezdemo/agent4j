package site.sorghum.cutin.core.event;

import site.sorghum.cutin.core.plugin.Registration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Hook 注册表：按顺序执行匹配的 Hook，并作为 EventHandler 接入事件总线。 */
public final class HookRegistry implements EventHandler {
    private final List<HookRegistration> hooks = new ArrayList<>();
    private final AtomicLong sequence = new AtomicLong();

    public synchronized void add(Hook hook) {
        register(hook);
    }

    public synchronized Registration register(Hook hook) {
        HookRegistration registration = new HookRegistration(hook.order(), sequence.incrementAndGet(), hook);
        hooks.add(registration);
        hooks.sort(HookRegistration::compareTo);
        return new Registration() {
            private boolean closed;
            @Override
            public synchronized void close() {
                if (!closed) {
                    closed = true;
                    synchronized (HookRegistry.this) {
                        hooks.remove(registration);
                    }
                }
            }
        };
    }

    @Override
    public void onEvent(LoopEvent event) {
        for (HookRegistration registration : List.copyOf(hooks)) {
            if (registration.hook().matches(event)) {
                registration.hook().run(event);
            }
        }
    }

    public synchronized List<Hook> hooks() {
        return hooks.stream().map(HookRegistration::hook).toList();
    }

    private record HookRegistration(int order, long sequence, Hook hook)
        implements Comparable<HookRegistration> {
        @Override
        public int compareTo(HookRegistration other) {
            int orderCompare = Integer.compare(order, other.order);
            return orderCompare != 0 ? orderCompare : Long.compare(sequence, other.sequence);
        }
    }
}
