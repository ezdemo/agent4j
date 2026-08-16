package site.sorghum.loopra.bin.checklist;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * ChecklistStep — 清单步骤。
 * <p>
 * 每个步骤包含描述、类型、状态和执行结果。
 * </p>
 *
 * @author Sorghum
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChecklistStep {
    private String id;           // 示例："step-1"、"step-2"
    private String description;  // "分析需求文档，输出 API 接口规范"
    private StepKind kind;       // STEP（普通）| FORK（分支）| HITL（人工审批）
    private StepStatus status;   // PENDING（待执行）| RUNNING（执行中）| DONE（完成）| SKIPPED（跳过）| FAILED（失败）
    private String result;       // 执行结果摘要
    private Instant createdAt;
    private Instant completedAt;
}
