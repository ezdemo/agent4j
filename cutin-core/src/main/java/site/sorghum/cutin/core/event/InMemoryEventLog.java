package site.sorghum.cutin.core.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 内存事件日志：把所有循环事件追加到列表，供测试与诊断使用。
 */
public final class InMemoryEventLog implements EventHandler {

    /** 追加式事件列表。 */
    private final List<LoopEvent> events = new CopyOnWriteArrayList<>();

    /** 记录一个事件。 */
    @Override
    public void onEvent(LoopEvent event) {
        events.add(event);
    }

    /** 返回只读的全部事件。 */
    public List<LoopEvent> events() {
        return List.copyOf(events);
    }
}
