package site.sorghum.agent4j.bin.workflow2;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowStep {
    private String id;           // "step-1", "step-2"
    private String description;  // "分析需求文档，输出 API 接口规范"
    private StepKind kind;       // STEP | FORK | HITL
    private StepStatus status;   // PENDING | RUNNING | DONE | SKIPPED | FAILED
    private String result;       // 执行结果摘要
    private Instant createdAt;
    private Instant completedAt;
}
