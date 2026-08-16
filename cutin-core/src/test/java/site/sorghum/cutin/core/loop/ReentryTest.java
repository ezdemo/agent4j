package site.sorghum.cutin.core.loop;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 重入测试：基于最新快照回到任意节点继续执行。
 */
class ReentryTest {

    /** 重入指定节点后应基于快照状态继续累加，并生成更高版本的新快照。 */
    @Test
    void reentersAnyNodeFromLatestSnapshot() throws Exception {
        DefaultLoopEngine engine = new DefaultLoopEngine();

        LoopProgram program = LoopProgram.builder("reentry")
            .node("increment", NodeType.CODE, context -> {
                int count = (int) context.variables().getOrDefault("count", 0);
                context.putVariable("count", count + 1);
                return StepResult.Exit.INSTANCE;
            })
            .build();

        LoopHandle handle = engine.run(program, Map.of("count", 0));
        LoopResult first = handle.result().get(5, TimeUnit.SECONDS);

        assertEquals(LoopResult.Status.COMPLETED, first.status());
        assertEquals(1, first.finalSnapshot().variables().get("count"));

        var snapshot = handle.snapshot();
        LoopResult second = handle.reenter(new ReentryRequest(
            "increment",
            snapshot.stateVersion(),
            Map.of(),
            "retry increment"
        )).get(5, TimeUnit.SECONDS);

        assertEquals(LoopResult.Status.COMPLETED, second.status());
        assertEquals(2, second.finalSnapshot().variables().get("count"));
        assertTrue(second.finalSnapshot().stateVersion() > snapshot.stateVersion());
    }
}
