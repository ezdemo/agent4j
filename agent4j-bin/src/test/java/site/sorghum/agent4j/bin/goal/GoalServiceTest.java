package site.sorghum.agent4j.bin.goal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GoalServiceTest {
    private final GoalService goals = new GoalService();

    @Test
    void completesOnlyAfterEveryStepHasEvidence() {
        Goal goal = goals.create("session", "workspace", "发布功能", List.of("实现", "验证"), "mvn test");

        assertThrows(IllegalStateException.class, () -> goals.complete(goal, "尚未开始"));

        goals.updateStep(goal, 1, StepStatus.DONE, "代码已提交");
        goals.updateStep(goal, 2, StepStatus.DONE, "mvn test 通过");
        goals.complete(goal, "mvn test 通过");

        assertEquals(GoalStatus.COMPLETED, goal.getStatus());
        assertEquals("mvn test 通过", goal.getCompletionSummary());
    }

    @Test
    void blockedGoalMustBeResumedBeforeStepsCanAdvance() {
        Goal goal = goals.create("session", "workspace", "修复错误", List.of("复现错误"), null);

        goals.updateStep(goal, 1, StepStatus.BLOCKED, "缺少复现数据");
        assertEquals(GoalStatus.BLOCKED, goal.getStatus());
        assertThrows(IllegalStateException.class,
                () -> goals.updateStep(goal, 1, StepStatus.DONE, "猜测已修复"));

        goals.resume(goal);
        goals.updateStep(goal, 1, StepStatus.DONE, "拿到复现数据并通过测试");
        goals.complete(goal, "验证完成");

        assertEquals(GoalStatus.COMPLETED, goal.getStatus());
    }
}
