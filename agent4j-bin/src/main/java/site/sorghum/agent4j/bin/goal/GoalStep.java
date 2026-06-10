package site.sorghum.agent4j.bin.goal;

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
    /** 步骤序号（从 0 开始） */
    private int index;
    /** 步骤描述 */
    private String description;
    /** 步骤状态 */
    private StepStatus status;
    /** 已重试次数 */
    @Builder.Default
    private int retryCount = 0;
    /** 最后一次失败的错误信息 */
    private String lastError;
    /** 完成时间 */
    private Instant completedAt;
}
