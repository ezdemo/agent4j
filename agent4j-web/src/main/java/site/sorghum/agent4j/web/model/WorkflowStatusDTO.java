package site.sorghum.agent4j.web.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 工作流状态 DTO —— 替代旧的 WorkflowVisualizationDTO。
 * <p>
 * 简化版：只包含步骤列表和进度信息，不含 DAG 图结构。
 * </p>
 *
 * @author Sorghum
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowStatusDTO {
    /** 工作流 ID */
    private String workflowId;
    /** 标题 */
    private String title;
    /** 状态 ACTIVE | PAUSED | COMPLETED | FAILED */
    private String status;
    /** 当前步骤索引（从1开始） */
    private int currentStepIndex;
    /** 总步骤数 */
    private int totalSteps;
    /** 进度文本 "2/5 (40%)" */
    private String progress;
    /** 步骤列表 */
    private List<StepDTO> steps;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepDTO {
        private String id;
        private String description;
        private String kind;    // STEP | FORK | HITL
        private String status;  // PENDING | RUNNING | DONE | SKIPPED | FAILED
        private String result;
    }
}
