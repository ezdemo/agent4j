package site.sorghum.agent4j.bin.goal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Goal 状态机。所有状态变化都必须经过这里，模型只能请求转换，不能直接篡改快照。
 */
public class GoalService {

    public Goal create(String sessionId, String workspaceHash, String objective,
                       List<String> requestedSteps, String verifyCommand) {
        if (objective == null || objective.isBlank()) {
            throw new IllegalArgumentException("目标不能为空");
        }
        List<String> descriptions = requestedSteps == null ? List.of() : requestedSteps.stream()
                .filter(step -> step != null && !step.isBlank()).map(String::trim).toList();
        if (descriptions.isEmpty()) descriptions = List.of(objective.trim());

        Instant now = Instant.now();
        List<GoalStep> steps = new ArrayList<>();
        for (int i = 0; i < descriptions.size(); i++) {
            steps.add(GoalStep.builder().index(i + 1).description(descriptions.get(i))
                    .status(StepStatus.PENDING).build());
        }
        return Goal.builder()
                .id(UUID.randomUUID().toString().substring(0, 8))
                .sessionId(sessionId)
                .workspaceHash(workspaceHash)
                .title(objective.length() > 72 ? objective.substring(0, 72) + "..." : objective)
                .description(objective)
                .status(GoalStatus.ACTIVE)
                .verifyCommand(blankToNull(verifyCommand))
                .steps(steps)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void updateStep(Goal goal, int stepIndex, StepStatus nextStatus, String evidence) {
        requireActive(goal);
        GoalStep step = goal.getSteps().stream().filter(item -> item.getIndex() == stepIndex)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("不存在步骤 " + stepIndex));
        if (nextStatus == null) throw new IllegalArgumentException("步骤状态不能为空");
        if (step.getStatus() == StepStatus.DONE && nextStatus != StepStatus.DONE) {
            throw new IllegalStateException("已完成步骤不可回退");
        }
        Instant now = Instant.now();
        if (nextStatus == StepStatus.IN_PROGRESS && step.getStartedAt() == null) {
            step.setStartedAt(now);
        }
        step.setStatus(nextStatus);
        step.setEvidence(blankToNull(evidence));
        if (nextStatus == StepStatus.DONE || nextStatus == StepStatus.SKIPPED) {
            step.setCompletedAt(now);
        }
        if (nextStatus == StepStatus.BLOCKED) {
            goal.setStatus(GoalStatus.BLOCKED);
            goal.setBlockedReason(blankToNull(evidence));
        }
        goal.setUpdatedAt(now);
    }

    public void complete(Goal goal, String summary) {
        requireActive(goal);
        if (!goal.isAllDone()) {
            throw new IllegalStateException("仍有未完成步骤，不能结束目标");
        }
        Instant now = Instant.now();
        goal.setStatus(GoalStatus.COMPLETED);
        goal.setCompletionSummary(blankToNull(summary));
        goal.setBlockedReason(null);
        goal.setCompletedAt(now);
        goal.setUpdatedAt(now);
    }

    public void pause(Goal goal) {
        requireStatus(goal, GoalStatus.ACTIVE);
        goal.setStatus(GoalStatus.PAUSED);
        goal.setUpdatedAt(Instant.now());
    }

    public void resume(Goal goal) {
        if (goal.getStatus() != GoalStatus.PAUSED && goal.getStatus() != GoalStatus.BLOCKED) {
            throw new IllegalStateException("只有暂停或阻塞的目标可以恢复");
        }
        goal.setStatus(GoalStatus.ACTIVE);
        goal.setBlockedReason(null);
        goal.setUpdatedAt(Instant.now());
    }

    public void block(Goal goal, String reason) {
        requireActive(goal);
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("阻塞原因不能为空");
        goal.setStatus(GoalStatus.BLOCKED);
        goal.setBlockedReason(reason.trim());
        goal.setUpdatedAt(Instant.now());
    }

    public void cancel(Goal goal, String reason) {
        if (!goal.isOpen()) throw new IllegalStateException("目标已经关闭");
        goal.setStatus(GoalStatus.CANCELLED);
        goal.setCompletionSummary(blankToNull(reason));
        goal.setUpdatedAt(Instant.now());
    }

    public String describe(Goal goal) {
        if (goal == null) return "当前会话没有 Goal。";
        StringBuilder result = new StringBuilder("Goal [").append(goal.getStatus()).append("] ")
                .append(goal.getTitle()).append("\n进度: ").append(goal.progressText());
        for (GoalStep step : goal.getSteps()) {
            result.append("\n").append(step.getIndex()).append(". [")
                    .append(step.getStatus()).append("] ").append(step.getDescription());
            if (step.getEvidence() != null) result.append(" — ").append(step.getEvidence());
        }
        if (goal.getBlockedReason() != null) result.append("\n阻塞: ").append(goal.getBlockedReason());
        return result.toString();
    }

    public String instruction(Goal goal) {
        if (goal == null || !goal.isOpen()) return "";
        GoalStep next = goal.nextOpenStep();
        StringBuilder result = new StringBuilder("## 当前 Goal\n")
                .append("目标：").append(goal.getTitle()).append("\n")
                .append("状态：").append(goal.getStatus()).append("，进度：")
                .append(goal.progressText()).append("\n");
        if (goal.getStatus() == GoalStatus.PAUSED) {
            return result.append("Goal 已暂停。等待用户使用 /goal resume 恢复，不要自行推进或结束。").toString();
        }
        if (goal.getStatus() == GoalStatus.BLOCKED) {
            return result.append("Goal 已阻塞：").append(goal.getBlockedReason())
                    .append("。向用户说明需要的信息；拿到信息后调用 goal_resume，再继续推进。").toString();
        }
        if (next != null) {
            result.append("当前应推进步骤 ").append(next.getIndex()).append("：")
                    .append(next.getDescription()).append("\n");
        }
        result.append("完成步骤后调用 goal_update_step；全部步骤完成并验证后调用 goal_complete。")
                .append("未完成 Goal 前不得调用 finish。");
        return result.toString();
    }

    private static void requireActive(Goal goal) {
        requireStatus(goal, GoalStatus.ACTIVE);
    }

    private static void requireStatus(Goal goal, GoalStatus expected) {
        if (goal == null) throw new IllegalStateException("当前会话没有 Goal");
        if (goal.getStatus() != expected) {
            throw new IllegalStateException("Goal 状态为 " + goal.getStatus() + "，不能执行此操作");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
