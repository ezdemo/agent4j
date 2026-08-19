package site.sorghum.cutin.core.event;

import site.sorghum.cutin.core.plugin.Registration;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 事件总线：向所有已注册处理器广播循环事件。 */
public final class EventBus {
    private final List<EventHandler> handlers = new CopyOnWriteArrayList<>();

    public void addHandler(EventHandler handler) {
        registerHandler(handler);
    }

    public Registration registerHandler(EventHandler handler) {
        handlers.add(handler);
        return new Registration() {
            private boolean closed;
            @Override
            public synchronized void close() {
                if (!closed) {
                    closed = true;
                    handlers.remove(handler);
                }
            }
        };
    }

    public void emit(LoopEvent event) {
        for (EventHandler handler : handlers) {
            handler.onEvent(event);
        }
    }

    public List<EventHandler> handlers() {
        return List.copyOf(handlers);
    }
}
