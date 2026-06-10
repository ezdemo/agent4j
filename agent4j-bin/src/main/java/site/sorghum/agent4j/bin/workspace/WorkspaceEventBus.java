package site.sorghum.agent4j.bin.workspace;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * 工作区事件总线 —— 支持通配符路径订阅/发布模式。
 * <p>
 * 通配符规则：
 * <ul>
 *   <li><code>*</code> 匹配单级路径（不含 /），正则：{@code [^/]+}</li>
 *   <li><code>**</code> 匹配多级路径（含 /），正则：{@code .*}</li>
 * </ul>
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
public class WorkspaceEventBus {

    private final ConcurrentHashMap<String, Watcher> watchers = new ConcurrentHashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(1);

    /**
     * 订阅指定通配符模式的事件。
     *
     * @param keyPattern 通配符路径模式，支持 {@code *} 和 {@code **}
     * @param handler    事件回调
     * @return 订阅 ID，格式 "ws_watch_{n}"
     */
    public String subscribe(String keyPattern, WatchHandler handler) {
        Pattern pattern = toPattern(keyPattern);
        String subscriptionId = "ws_watch_" + idCounter.getAndIncrement();
        Watcher watcher = new Watcher(keyPattern, pattern, handler, System.currentTimeMillis());
        watchers.put(subscriptionId, watcher);
        log.info("[WorkspaceEventBus] 订阅事件: id={}, pattern={}", subscriptionId, keyPattern);
        return subscriptionId;
    }

    /**
     * 取消订阅。
     *
     * @param subscriptionId 订阅 ID
     */
    public void unsubscribe(String subscriptionId) {
        Watcher removed = watchers.remove(subscriptionId);
        if (removed != null) {
            log.info("[WorkspaceEventBus] 取消订阅: id={}, pattern={}", subscriptionId, removed.keyPattern);
        } else {
            log.warn("[WorkspaceEventBus] 取消订阅失败，未找到订阅: id={}", subscriptionId);
        }
    }

    /**
     * 发布事件。
     *
     * @param key   变更的键
     * @param type  事件类型
     * @param value 变更后的值
     */
    public void publish(String key, EventType type, Object value) {
        for (var entry : watchers.entrySet()) {
            String subscriptionId = entry.getKey();
            Watcher watcher = entry.getValue();
            if (watcher.pattern().matcher(key).matches()) {
                try {
                    watcher.handler().onEvent(key, type, value);
                } catch (Exception e) {
                    log.warn("[WorkspaceEventBus] 事件处理异常: id={}, pattern={}, key={}, type={}",
                            subscriptionId, watcher.keyPattern(), key, type, e);
                }
            }
        }
    }

    /**
     * 返回当前活跃订阅数。
     *
     * @return 订阅数量
     */
    public int watcherCount() {
        return watchers.size();
    }

    /**
     * 将通配符路径模式编译为正则表达式。
     */
    private static Pattern toPattern(String keyPattern) {
        // 转义除 * / 之外的正则特殊字符
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keyPattern.length(); i++) {
            char c = keyPattern.charAt(i);
            switch (c) {
                case '.':
                case '+':
                case '^':
                case '$':
                case '{':
                case '}':
                case '(':
                case ')':
                case '|':
                case '[':
                case ']':
                case '\\':
                    sb.append('\\').append(c);
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }

        // 转换通配符：** 必须优先于 * 处理
        String regex = sb.toString()
                .replace("**", "___DOUBLESTAR___")
                .replace("*", "[^/]+")
                .replace("___DOUBLESTAR___", ".*");

        return Pattern.compile(regex);
    }

    /**
     * 内部 Watcher 记录 —— 保存订阅信息。
     *
     * @param keyPattern 原始通配符模式
     * @param pattern    编译后的正则表达式
     * @param handler    事件回调
     * @param createdAt  创建时间戳
     */
    public record Watcher(String keyPattern, Pattern pattern, WatchHandler handler, long createdAt) {
    }
}
