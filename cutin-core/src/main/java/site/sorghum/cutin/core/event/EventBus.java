package site.sorghum.cutin.core.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 事件总线：向所有已注册处理器广播循环事件。
 */
public final class EventBus {

    /** 线程安全的事件处理器列表。 */
    private final List<EventHandler> handlers = new CopyOnWriteArrayList<>();

    /** 注册事件处理器。 */
    public void addHandler(EventHandler handler) {
        handlers.add(handler);
    }

    /** 向全部处理器广播事件。 */
    public void emit(LoopEvent event) {
        for (EventHandler handler : handlers) {
            handler.onEvent(event);
        }
    }

    /** 当前已注册的全部处理器。 */
    public List<EventHandler> handlers() {
        return List.copyOf(handlers);
    }
}
