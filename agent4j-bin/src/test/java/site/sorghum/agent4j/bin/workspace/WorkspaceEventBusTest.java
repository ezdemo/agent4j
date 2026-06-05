package site.sorghum.agent4j.bin.workspace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link WorkspaceEventBus} 的单元测试。
 * <p>
 * 覆盖精确匹配、单级通配符 {@code *}、多级通配符 {@code **}、
 * 不匹配场景、取消订阅、多订阅者、订阅计数以及事件类型传播。
 * </p>
 *
 * @author Sorghum
 */
class WorkspaceEventBusTest {

    private WorkspaceEventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = new WorkspaceEventBus();
    }

    // ==================== testExactMatch ====================

    @Test
    void testExactMatch() {
        AtomicReference<String> capturedKey = new AtomicReference<>();
        AtomicReference<EventType> capturedType = new AtomicReference<>();
        AtomicReference<Object> capturedValue = new AtomicReference<>();

        eventBus.subscribe("task/status", (key, type, value) -> {
            capturedKey.set(key);
            capturedType.set(type);
            capturedValue.set(value);
        });

        eventBus.publish("task/status", EventType.WRITE, "running");

        assertEquals("task/status", capturedKey.get(), "精确匹配应捕获正确的 key");
        assertEquals(EventType.WRITE, capturedType.get(), "事件类型应正确传递");
        assertEquals("running", capturedValue.get(), "事件 value 应正确传递");
    }

    // ==================== testSingleLevelWildcard ====================

    @Test
    void testSingleLevelWildcard() {
        List<String> capturedKeys = new ArrayList<>();

        eventBus.subscribe("task/*/status", (key, type, value) -> capturedKeys.add(key));

        eventBus.publish("task/module-a/status", EventType.UPDATE, "completed");
        eventBus.publish("task/module-b/status", EventType.UPDATE, "running");
        // 以下不应匹配（两级通配符 * 不匹配含 / 的路径段）
        eventBus.publish("task/module-a/sub/status", EventType.UPDATE, "failed");

        assertEquals(2, capturedKeys.size(), "应精确匹配两个单级通配符路径");
        assertTrue(capturedKeys.contains("task/module-a/status"));
        assertTrue(capturedKeys.contains("task/module-b/status"));
    }

    // ==================== testMultiLevelWildcard ====================

    @Test
    void testMultiLevelWildcard() {
        List<String> capturedKeys = new ArrayList<>();

        eventBus.subscribe("task/**", (key, type, value) -> capturedKeys.add(key));

        eventBus.publish("task/a", EventType.WRITE, 1);
        eventBus.publish("task/a/b", EventType.WRITE, 2);
        eventBus.publish("task/a/b/c", EventType.WRITE, 3);
        // 不应匹配与模式前缀不同的 key
        eventBus.publish("other/task", EventType.WRITE, 4);

        assertEquals(3, capturedKeys.size(), "多级通配符应匹配所有以 task/ 开头的路径");
        assertTrue(capturedKeys.contains("task/a"));
        assertTrue(capturedKeys.contains("task/a/b"));
        assertTrue(capturedKeys.contains("task/a/b/c"));
    }

    // ==================== testNonMatching ====================

    @Test
    void testNonMatching() {
        AtomicInteger invokedCount = new AtomicInteger(0);

        eventBus.subscribe("task/*", (key, type, value) -> invokedCount.incrementAndGet());

        // 发布到不匹配的模式
        eventBus.publish("shared/context", EventType.WRITE, "data");
        eventBus.publish("task", EventType.WRITE, "data");       // 无斜杠，不匹配 task/*
        eventBus.publish("tasks/other", EventType.WRITE, "data"); // 前缀不同

        assertEquals(0, invokedCount.get(), "不匹配的 key 不应触发订阅回调");
    }

    // ==================== testUnsubscribe ====================

    @Test
    void testUnsubscribe() {
        AtomicInteger invokedCount = new AtomicInteger(0);
        String subId = eventBus.subscribe("test/key", (key, type, value) -> invokedCount.incrementAndGet());

        // 取消订阅前应正常触发
        eventBus.publish("test/key", EventType.WRITE, "v1");
        assertEquals(1, invokedCount.get(), "取消订阅前应触发事件");

        // 取消订阅
        eventBus.unsubscribe(subId);

        // 取消后不应再触发
        eventBus.publish("test/key", EventType.WRITE, "v2");
        assertEquals(1, invokedCount.get(), "取消订阅后不应再触发事件");
    }

    // ==================== testMultipleSubscribers ====================

    @Test
    void testMultipleSubscribers() {
        AtomicInteger count1 = new AtomicInteger(0);
        AtomicInteger count2 = new AtomicInteger(0);
        AtomicInteger count3 = new AtomicInteger(0);

        eventBus.subscribe("shared/*", (key, type, value) -> count1.incrementAndGet());
        eventBus.subscribe("shared/*", (key, type, value) -> count2.incrementAndGet());
        eventBus.subscribe("shared/*", (key, type, value) -> count3.incrementAndGet());

        eventBus.publish("shared/config", EventType.UPDATE, "new-value");

        assertEquals(1, count1.get(), "订阅者1 应收到事件");
        assertEquals(1, count2.get(), "订阅者2 应收到事件");
        assertEquals(1, count3.get(), "订阅者3 应收到事件");
    }

    // ==================== testWatcherCount ====================

    @Test
    void testWatcherCount() {
        assertEquals(0, eventBus.watcherCount(), "初始化时 watcher 数应为 0");

        String sub1 = eventBus.subscribe("a/*", (k, t, v) -> {});
        assertEquals(1, eventBus.watcherCount(), "添加一个订阅后 watcher 数应为 1");

        String sub2 = eventBus.subscribe("b/*", (k, t, v) -> {});
        assertEquals(2, eventBus.watcherCount(), "添加第二个订阅后 watcher 数应为 2");

        eventBus.unsubscribe(sub1);
        assertEquals(1, eventBus.watcherCount(), "取消一个订阅后 watcher 数应为 1");

        eventBus.unsubscribe(sub2);
        assertEquals(0, eventBus.watcherCount(), "取消所有订阅后 watcher 数应为 0");
    }

    // ==================== testEventTypePropagation ====================

    @Test
    void testEventTypePropagation() {
        List<EventType> capturedTypes = new ArrayList<>();

        eventBus.subscribe("data/**", (key, type, value) -> capturedTypes.add(type));

        eventBus.publish("data/item", EventType.WRITE, "create");
        eventBus.publish("data/item", EventType.UPDATE, "modify");
        eventBus.publish("data/item", EventType.DELETE, "remove");

        assertEquals(3, capturedTypes.size(), "应捕获 3 个事件");
        assertEquals(EventType.WRITE, capturedTypes.get(0), "第一个事件类型应为 WRITE");
        assertEquals(EventType.UPDATE, capturedTypes.get(1), "第二个事件类型应为 UPDATE");
        assertEquals(EventType.DELETE, capturedTypes.get(2), "第三个事件类型应为 DELETE");
    }
}
