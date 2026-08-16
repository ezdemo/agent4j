package site.sorghum.cutin.core.loop;

import org.junit.jupiter.api.Test;
import site.sorghum.cutin.core.context.DefaultLoopContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * BEFORE_OUTPUT 拦截点测试：验证输出节点前执行与中止决策。
 */
class BeforeOutputInterceptorTest {

    /** BEFORE_OUTPUT 拦截器应能看到上下文修改，随后输出节点正常执行。 */
    @Test
    void runsBeforeOutputNodeExecutes() {
        DefaultLoopEngine engine = new DefaultLoopEngine();
        engine.addInterceptor(InterceptPoint.BEFORE_OUTPUT, 0, context -> {
            context.context().putVariable("beforeOutput", true);
            return InterceptDecision.pass();
        });

        LoopProgram program = LoopProgram.builder("before-output")
            .node("out", NodeType.OUTPUT, context -> {
                context.putVariable("outputRan", true);
                return StepResult.Exit.INSTANCE;
            })
            .start("out")
            .build();
        DefaultLoopContext context = engine.newContext("ctx", Map.of());

        LoopResult result = engine.run(program, context).result().join();

        assertEquals(LoopResult.Status.COMPLETED, result.status());
        assertEquals(true, context.variables().get("beforeOutput"));
        assertEquals(true, context.variables().get("outputRan"));
    }

    /** BEFORE_OUTPUT 返回 ABORT 时应跳过输出节点。 */
    @Test
    void abortDecisionSkipsOutputNode() {
        DefaultLoopEngine engine = new DefaultLoopEngine();
        engine.addInterceptor(InterceptPoint.BEFORE_OUTPUT, 0, context ->
            InterceptDecision.abort("stop before output"));

        LoopProgram program = LoopProgram.builder("before-output-abort")
            .node("out", NodeType.OUTPUT, context -> {
                context.putVariable("outputRan", true);
                return StepResult.Exit.INSTANCE;
            })
            .start("out")
            .build();
        DefaultLoopContext context = engine.newContext("ctx", Map.of());

        LoopResult result = engine.run(program, context).result().join();

        assertEquals(LoopResult.Status.ABORTED, result.status());
        assertNull(context.variables().get("outputRan"));
    }
}
