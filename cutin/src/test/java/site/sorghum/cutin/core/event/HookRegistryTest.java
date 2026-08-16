package site.sorghum.cutin.core.event;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Hook 注册表测试：验证匹配过滤与顺序执行。
 */
class HookRegistryTest {

    /** 应只执行匹配事件类型的 Hook，并按 order 排序。 */
    @Test
    void runsMatchingHooksInOrder() {
        EventBus bus = new EventBus();
        HookRegistry registry = new HookRegistry();
        bus.addHandler(registry);
        List<String> calls = new CopyOnWriteArrayList<>();

        registry.add(new Hook() {
            @Override
            public String id() {
                return "late";
            }

            @Override
            public int order() {
                return 200;
            }

            @Override
            public void run(LoopEvent event) {
                calls.add("late");
            }
        });
        registry.add(new Hook() {
            @Override
            public String id() {
                return "early";
            }

            @Override
            public int order() {
                return 100;
            }

            @Override
            public boolean matches(LoopEvent event) {
                return event.type().equals("MATCH");
            }

            @Override
            public void run(LoopEvent event) {
                calls.add("early");
            }
        });

        bus.emit(new LoopEvent("MATCH", "loop-1", "node", Map.of()));

        assertEquals(List.of("early", "late"), calls);
    }
}
