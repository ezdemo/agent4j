package site.sorghum.loopra.bin.goal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Goal — 会话目标。
 * <p>
 * 每个会话同时最多一个活跃目标。
 * </p>
 *
 * @author Sorghum
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Goal {
    /** UUID */
    private String id;
    /** 关联的会话 ID */
    private String sessionId;
    /** 工作区 hash（冗余，方便全局巡检） */
    private String workspaceHash;
    /** 一句话目标标题 */
    private String title;
    /** 详细描述 */
    private String description;
    /** 目标状态 */
    private GoalStatus status;
    /** 验证命令（如 "mvn test"），可为 null */
    private String verifyCommand;
    /** 目标完成时的验证摘要 */
    private String completionSummary;
    /** 阻塞原因，status=BLOCKED 时必填 */
    private String blockedReason;

    /** 步骤列表 */
    @Builder.Default
    private List<GoalStep> steps = new ArrayList<>();

    /** 创建时间 */
    private Instant createdAt;
    /** 最后更新时间 */
    private Instant updatedAt;
    /** 完成时间 */
    private Instant completedAt;

    /** 是否仍需继续推进。 */
    public boolean isOpen() {
        return status == GoalStatus.ACTIVE || status == GoalStatus.PAUSED || status == GoalStatus.BLOCKED;
    }

    /**
     * 生成进度文本：如 "3/6 (50%)"
     */
    public String progressText() {
        long done = steps.stream().filter(s -> s.getStatus() == StepStatus.DONE).count();
        long total = steps.size();
        long pct = total > 0 ? (done * 100 / total) : 0;
        return done + "/" + total + " (" + pct + "%)";
    }

    /**
     * 判断是否全部完成。
     */
    public boolean isAllDone() {
        return steps != null && !steps.isEmpty() && steps.stream().allMatch(
                s -> s.getStatus() == StepStatus.DONE || s.getStatus() == StepStatus.SKIPPED);
    }

    /** 返回第一个未完成步骤，没有则返回 null。 */
    public GoalStep nextOpenStep() {
        if (steps == null) return null;
        return steps.stream().filter(step -> step.getStatus() != StepStatus.DONE
                        && step.getStatus() != StepStatus.SKIPPED)
                .findFirst().orElse(null);
    }
}
