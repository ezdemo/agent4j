package site.sorghum.agent4j.bin.workspace;

/**
 * 工作区变更通知回调。
 *
 * @author Sorghum
 */
@FunctionalInterface
public interface WatchHandler {

    /**
     * 工作区变更事件回调。
     *
     * @param key  变更的键
     * @param type 事件类型
     * @param value 变更后的值
     */
    void onEvent(String key, EventType type, Object value);
}
