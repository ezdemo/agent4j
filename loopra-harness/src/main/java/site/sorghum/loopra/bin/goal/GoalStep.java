package site.sorghum.loopra.bin.goal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * GoalStep — 目标步骤。
 * <p>
 * 由 LLM 拆解生成，走完所有步骤目标即完成。
 * </p>
 *
 * @author Sorghum
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoalStep {
    /** 步骤序号（从 1 开始） */
    private int index;
    /** 步骤描述 */
    private String description;
    /** 步骤状态 */
    private StepStatus status;
    /** 完成或阻塞时记录的可验证证据 */
    private String evidence;
    /** 开始时间 */
    private Instant startedAt;
    /** 完成时间 */
    private Instant completedAt;

    /** 已完成或跳过的步骤不再需要推进。 */
    public boolean isClosed() {
        return status == StepStatus.DONE || status == StepStatus.SKIPPED;
    }
}
