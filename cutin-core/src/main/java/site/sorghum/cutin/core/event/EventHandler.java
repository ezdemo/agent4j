package site.sorghum.cutin.core.event;

/**
 * 事件处理器函数式接口，用于订阅 {@link EventBus} 上的循环事件。
 */
@FunctionalInterface
public interface EventHandler {

    /** 处理一个循环事件。 */
    void onEvent(LoopEvent event);
}
