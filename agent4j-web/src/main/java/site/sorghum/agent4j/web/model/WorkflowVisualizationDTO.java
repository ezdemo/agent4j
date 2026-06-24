package site.sorghum.agent4j.web.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 工作流可视化 DTO —— 与 workflow_visualize 工具返回格式一致。
 *
 * @author Sorghum
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowVisualizationDTO {
    /** 工作流 ID */
    private String workflowId;
    /** 标题 */
    private String title;
    /** 描述 */
    private String description;
    /** 状态 */
    private String status;
    /** 进度文本 */
    private String progress;
    /** 节点列表 */
    private List<NodeDTO> nodes;
    /** 边列表 */
    private List<EdgeDTO> edges;
    /** 执行路径 */
    private List<PathNodeDTO> executionPath;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeDTO {
        private String id;
        private String description;
        private String type;
        private String status;
        private int retryCount;
        private String lastError;
        private String result;
        private String condition;
        private String conditionResult;
        private List<String> parallelBranches;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EdgeDTO {
        private String id;
        private String from;
        private String to;
        private String type;
        private String condition;
        private String label;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PathNodeDTO {
        private String id;
        private String description;
        private String status;
        private List<PathNodeDTO> next;
    }
}
