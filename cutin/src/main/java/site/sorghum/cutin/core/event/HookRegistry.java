package site.sorghum.cutin.core.event;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Hook 注册表：按顺序执行匹配的 Hook，并作为 {@link EventHandler} 接入事件总线。
 */
public final class HookRegistry implements EventHandler {

    /** 已注册的 Hook 列表。 */
    private final List<Registration> hooks = new ArrayList<>();
    /** 注册序号，保证同 order 下稳定顺序。 */
    private final AtomicLong sequence = new AtomicLong();

    /** 注册一个 Hook，并按 order 与注册顺序排序。 */
    public synchronized void add(Hook hook) {
        hooks.add(new Registration(hook.order(), sequence.incrementAndGet(), hook));
        hooks.sort(Registration::compareTo);
    }

    /** 依次执行所有匹配该事件的 Hook。 */
    @Override
    public void onEvent(LoopEvent event) {
        for (Registration registration : List.copyOf(hooks)) {
            if (registration.hook().matches(event)) {
                registration.hook().run(event);
            }
        }
    }

    /** 返回当前全部 Hook。 */
    public synchronized List<Hook> hooks() {
        return hooks.stream().map(Registration::hook).toList();
    }

    /** Hook 注册记录。 */
    private record Registration(int order, long sequence, Hook hook)
        implements Comparable<Registration> {

        /** 按 order 排序，order 相同时按注册序号排序。 */
        @Override
        public int compareTo(Registration other) {
            int orderCompare = Integer.compare(order, other.order);
            return orderCompare != 0 ? orderCompare : Long.compare(sequence, other.sequence);
        }
    }
}
