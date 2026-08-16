package site.sorghum.cutin.core.loop;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 拦截器排序测试：同一拦截点应按 order 升序执行。
 */
class InterceptorOrderingTest {

    /** order 较小的拦截器应先生成记录。 */
    @Test
    void ordersInterceptorsByPriorityAtBeforeStep() throws Exception {
        DefaultLoopEngine engine = new DefaultLoopEngine();
        List<String> order = new CopyOnWriteArrayList<>();

        engine.addInterceptor(InterceptPoint.BEFORE_STEP, 200, context -> {
            order.add("late");
            return InterceptDecision.pass();
        });
        engine.addInterceptor(InterceptPoint.BEFORE_STEP, 100, context -> {
            order.add("early");
            return InterceptDecision.pass();
        });

        LoopProgram program = LoopProgram.builder("ordering")
            .node("finish", NodeType.CODE, Steps.finish())
            .build();

        LoopHandle handle = engine.run(program, Map.of("order", order));
        LoopResult result = handle.result().get(5, TimeUnit.SECONDS);

        assertEquals(LoopResult.Status.COMPLETED, result.status());
        assertEquals(List.of("early", "late"), order);
    }
}
