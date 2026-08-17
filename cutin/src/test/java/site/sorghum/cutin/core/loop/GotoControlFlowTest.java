package site.sorghum.cutin.core.loop;

import org.junit.jupiter.api.Test;
import site.sorghum.cutin.core.context.Budget;
import site.sorghum.cutin.core.context.BudgetLimit;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * GOTO 与有环图控制流测试。
 *
 * <p>覆盖插件拦截点、Step 控制结果、控制流异常以及预算对无限轮转的保护。</p>
 */
class GotoControlFlowTest {

    @Test
    void beforeStepPluginCanGotoAnotherNode() {
        DefaultLoopEngine engine = new DefaultLoopEngine();
        AtomicInteger skippedRuns = new AtomicInteger();
        AtomicInteger targetRuns = new AtomicInteger();
        engine.addInterceptor(InterceptPoint.BEFORE_STEP, 0, context ->
            "start".equals(context.nodeId())
                ? InterceptDecision.gotoNode("target")
                : InterceptDecision.pass()
        );

        LoopProgram program = LoopProgram.builder("before-step-goto")
            .node("start", NodeType.CODE, ignored -> {
                skippedRuns.incrementAndGet();
                return StepResult.Exit.INSTANCE;
            })
            .node("target", NodeType.CODE, ignored -> {
                targetRuns.incrementAndGet();
                return StepResult.Exit.INSTANCE;
            })
            .start("start")
            .build();

        LoopResult result = engine.run(program, engine.newContext("ctx", Map.of())).result().join();

        assertEquals(LoopResult.Status.COMPLETED, result.status());
        assertEquals(0, skippedRuns.get());
        assertEquals(1, targetRuns.get());
    }

    @Test
    void beforeOutputPluginCanGotoCleanupNode() {
        DefaultLoopEngine engine = new DefaultLoopEngine();
        AtomicInteger cleanupRuns = new AtomicInteger();
        engine.addInterceptor(InterceptPoint.BEFORE_OUTPUT, 0, context ->
            InterceptDecision.gotoNode("cleanup")
        );

        LoopProgram program = LoopProgram.builder("before-output-goto")
            .node("out", NodeType.OUTPUT, Steps.finish())
            .node("cleanup", NodeType.CODE, ignored -> {
                cleanupRuns.incrementAndGet();
                return StepResult.Exit.INSTANCE;
            })
            .start("out")
            .build();

        LoopResult result = engine.run(program, engine.newContext("ctx", Map.of())).result().join();

        assertEquals(LoopResult.Status.COMPLETED, result.status());
        assertEquals(1, cleanupRuns.get());
    }

    @Test
    void beforeRetryPluginCanGotoRecoveryNode() {
        DefaultLoopEngine engine = new DefaultLoopEngine();
        AtomicInteger recoveryRuns = new AtomicInteger();
        engine.addInterceptor(InterceptPoint.BEFORE_RETRY, 0, context ->
            InterceptDecision.gotoNode("recovery")
        );

        LoopProgram program = LoopProgram.builder("before-retry-goto")
            .node("unstable", NodeType.CODE, ignored -> {
                throw new LoopRetryException("retry");
            })
            .node("recovery", NodeType.CODE, ignored -> {
                recoveryRuns.incrementAndGet();
                return StepResult.Exit.INSTANCE;
            })
            .start("unstable")
            .build();

        LoopResult result = engine.run(program, engine.newContext("ctx", Map.of())).result().join();

        assertEquals(LoopResult.Status.COMPLETED, result.status());
        assertEquals(1, recoveryRuns.get());
    }

    @Test
    void stepAndGotoExceptionCanJumpAroundGraph() {
        DefaultLoopEngine engine = new DefaultLoopEngine();
        AtomicInteger stepGotoRuns = new AtomicInteger();
        AtomicInteger exceptionGotoRuns = new AtomicInteger();

        LoopProgram stepProgram = LoopProgram.builder("step-goto")
            .node("start", NodeType.CODE, ignored -> new StepResult.Goto("target"))
            .node("target", NodeType.CODE, ignored -> {
                stepGotoRuns.incrementAndGet();
                return StepResult.Exit.INSTANCE;
            })
            .start("start")
            .build();
        LoopResult stepResult = engine.run(stepProgram, engine.newContext("step", Map.of())).result().join();

        LoopProgram exceptionProgram = LoopProgram.builder("exception-goto")
            .node("start", NodeType.CODE, ignored -> {
                throw new LoopGotoException("target", "jump");
            })
            .node("target", NodeType.CODE, ignored -> {
                exceptionGotoRuns.incrementAndGet();
                return StepResult.Exit.INSTANCE;
            })
            .start("start")
            .build();
        LoopResult exceptionResult = engine.run(
            exceptionProgram,
            engine.newContext("exception", Map.of())
        ).result().join();

        assertEquals(LoopResult.Status.COMPLETED, stepResult.status());
        assertEquals(LoopResult.Status.COMPLETED, exceptionResult.status());
        assertEquals(1, stepGotoRuns.get());
        assertEquals(1, exceptionGotoRuns.get());
    }

    @Test
    void cyclicGraphStopsAtStepBudgetInsteadOfRunningForever() {
        DefaultLoopEngine engine = new DefaultLoopEngine();
        AtomicInteger runs = new AtomicInteger();
        LoopProgram program = LoopProgram.builder("bounded-cycle")
            .node("loop", NodeType.CODE, ignored -> {
                runs.incrementAndGet();
                return StepResult.Repeat.INSTANCE;
            })
            .start("loop")
            .build();

        LoopResult result = engine.run(
            program,
            engine.newContext(
                "ctx",
                Map.of("budget", new Budget(BudgetLimit.steps(5)))
            )
        ).result().join();

        assertEquals(LoopResult.Status.FAILED, result.status());
        assertEquals("step budget exceeded", result.message());
        assertEquals(5, runs.get());
    }
}
