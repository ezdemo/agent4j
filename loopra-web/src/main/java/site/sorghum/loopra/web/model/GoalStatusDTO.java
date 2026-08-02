package site.sorghum.loopra.web.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Goal 状态 DTO。
 *
 * @author Sorghum
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoalStatusDTO {
    /** Goal ID */
    private String goalId;
    /** 标题 */
    private String title;
    /** 详细描述 */
    private String description;
    /** 状态 ACTIVE | PAUSED | BLOCKED | COMPLETED | CANCELLED */
    private String status;
    /** 进度文本 "2/5 (40%)" */
    private String progress;
    /** 总步骤数 */
    private int totalSteps;
    /** 已完成步骤数 */
    private int doneSteps;
    /** 验证命令 */
    private String verifyCommand;
    /** 阻塞原因 */
    private String blockedReason;
    /** 完成摘要 */
    private String completionSummary;
    /** 步骤列表 */
    private List<StepDTO> steps;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepDTO {
        private int index;
        private String description;
        private String status;  // PENDING | IN_PROGRESS | DONE | BLOCKED | SKIPPED
        private String evidence;
    }
}
