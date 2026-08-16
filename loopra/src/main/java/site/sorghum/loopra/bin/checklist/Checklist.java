package site.sorghum.loopra.bin.checklist;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Checklist — 执行清单（轻量级步骤进度追踪）。
 * <p>
 * 替代旧的 DAG Workflow 模型。LLM 定义有序步骤列表，逐步执行并标记进度。
 * 每一步内 LLM 完全自由推理，不受图约束。
 * </p>
 *
 * @author Sorghum
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Checklist {
    private String id;
    private String sessionId;
    private String workspaceHash;
    private String title;
    private String description;

    @Builder.Default
    private List<ChecklistStep> steps = new ArrayList<>();

    private int currentStepIndex; // 当前执行到第几步（从1开始，0表示未开始）
    private String status;        // ACTIVE（活跃）| PAUSED（暂停）| COMPLETED（完成）| FAILED（失败）
    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;

    public ChecklistStep currentStep() {
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
