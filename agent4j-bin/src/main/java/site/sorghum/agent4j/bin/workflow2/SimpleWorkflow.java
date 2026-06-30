package site.sorghum.agent4j.bin.workflow2;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimpleWorkflow {
    private String id;
    private String sessionId;
    private String workspaceHash;
    private String title;
    private String description;

    @Builder.Default
    private List<WorkflowStep> steps = new ArrayList<>();

    private int currentStepIndex; // 当前执行到第几步（从1开始，0表示未开始）
    private String status;        // ACTIVE | PAUSED | COMPLETED | FAILED
    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;

    public WorkflowStep currentStep() {
        if (currentStepIndex < 1 || currentStepIndex > steps.size()) return null;
        return steps.get(currentStepIndex - 1);
    }

    public boolean isAllDone() {
        return steps.stream().allMatch(s -> s.getStatus() == StepStatus.DONE);
    }

    public boolean hasFailed() {
        return steps.stream().anyMatch(s -> s.getStatus() == StepStatus.FAILED);
    }

    public boolean isRunning() {
        return "ACTIVE".equals(status) && !isAllDone() && !hasFailed();
    }

    public String progressText() {
        long done = steps.stream().filter(s -> s.getStatus() == StepStatus.DONE).count();
        int total = steps.size();
        int pct = total > 0 ? (int) (done * 100 / total) : 0;
        return done + "/" + total + " (" + pct + "%)";
    }

    /** 推进到下一步（返回新的 currentStepIndex，-1 表示已完成） */
    public int advance() {
        if (currentStepIndex < steps.size()) {
            currentStepIndex++;
            return currentStepIndex;
        }
        return -1;
    }
}
