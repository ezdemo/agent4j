package site.sorghum.loopra.bin.builtin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import site.sorghum.loopra.bin.agent.core.AgentLoop;
import site.sorghum.loopra.bin.tool.ToolRegistry;
import site.sorghum.loopra.tool.ToolContext;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SubmitPlanToolTest {

    @AfterEach
    void clearController() {
        ToolContext.clearCurrentController();
    }

    @Test
    void parsesJsonAndNumberedLineSteps() {
        assertEquals(List.of("inspect", "implement"),
                SubmitPlanTool.parseSteps("[\"inspect\",\"implement\"]"));
        assertEquals(List.of("inspect", "implement"),
                SubmitPlanTool.parseSteps("1. inspect\n2) implement"));
    }

    @Test
    void rejectsSubmissionOutsidePlanMode() {
        AgentLoop loop = loop();
        ToolContext.setCurrentController(loop);

        String result = new SubmitPlanTool().submitPlan(
                "[\"inspect\"]", "summary", "risk", context());

        assertTrue(result.contains("不在计划模式"));
        assertNull(loop.getPendingPlan());
    }

    @Test
    void submitsStructuredPlanInPlanMode() {
        AgentLoop loop = loop();
        loop.setPlanMode(true);
        ToolContext.setCurrentController(loop);

        String result = new SubmitPlanTool().submitPlan(
                "[\"inspect\",\"implement\"]", "Improve flow", "Keep compatibility", context());

        assertTrue(result.contains("submitted"));
        String plan = loop.consumePendingPlan();
        assertNotNull(plan);
        assertTrue(plan.contains("Improve flow"));
        assertTrue(plan.contains("1. inspect"));
        assertTrue(plan.contains("2. implement"));
        assertTrue(plan.contains("Keep compatibility"));
        assertNull(loop.consumePendingPlan());
    }

    private static AgentLoop loop() {
        ToolRegistry registry = new ToolRegistry().setDisabledTools(Set.of());
        registry.setRefreshContext(Paths.get(".").toAbsolutePath(), null, null, List.of());
        return new AgentLoop(null, registry, null);
    }

    private static ToolContext context() {
        return new ToolContext(Map.of(), Paths.get(".").toAbsolutePath().toString(), "test-session");
    }
}
