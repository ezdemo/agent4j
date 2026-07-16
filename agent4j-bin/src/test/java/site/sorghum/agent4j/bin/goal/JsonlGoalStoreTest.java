package site.sorghum.agent4j.bin.goal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JsonlGoalStoreTest {

    @TempDir
    Path workspace;

    @Test
    void persistsEvidenceAndClosedStatus() throws Exception {
        GoalService service = new GoalService();
        Goal goal = service.create("session/a", "workspace", "验证持久化", List.of("写入", "读取"), null);
        service.updateStep(goal, 1, StepStatus.DONE, "写入成功");
        service.updateStep(goal, 2, StepStatus.SKIPPED, "已有覆盖");
        service.complete(goal, "快照往返成功");

        JsonlGoalStore store = new JsonlGoalStore(workspace);
        store.save(goal);
        Goal restored = store.findBySession("session/a");

        assertNotNull(restored);
        assertEquals(GoalStatus.COMPLETED, restored.getStatus());
        assertEquals("写入成功", restored.getSteps().get(0).getEvidence());
        assertEquals("快照往返成功", restored.getCompletionSummary());
    }
}
