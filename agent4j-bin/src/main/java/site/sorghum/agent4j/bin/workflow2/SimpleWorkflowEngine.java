package site.sorghum.agent4j.bin.workflow2;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SimpleWorkflowEngine — 简化工作流引擎。
 * <p>
 * 管理 SimpleWorkflow 的创建、推进、标记完成/失败等操作。
 * 持久化通过 WorkspaceManager 的 KV store 完成（SimpleWorkflow 本身是 POJO，直接 JSON 序列化）。
 * </p>
 */
@Slf4j
@Component
public class SimpleWorkflowEngine {

    /**
     * 创建工作流。
     */
    public SimpleWorkflow createWorkflow(String sessionId, String workspaceHash,
                                          String title, String description,
                                          List<StepDef> stepDefs) {
        List<WorkflowStep> steps = new ArrayList<>();
        for (int i = 0; i < stepDefs.size(); i++) {
            StepDef def = stepDefs.get(i);
            steps.add(WorkflowStep.builder()
                    .id("step-" + (i + 1))
                    .description(def.description)
                    .kind(def.kind != null ? def.kind : StepKind.STEP)
                    .status(StepStatus.PENDING)
                    .createdAt(Instant.now())
                    .build());
        }

        SimpleWorkflow wf = SimpleWorkflow.builder()
                .id(UUID.randomUUID().toString().substring(0, 8))
                .sessionId(sessionId)
                .workspaceHash(workspaceHash)
                .title(title)
                .description(description)
                .steps(steps)
                .currentStepIndex(1) // 从第一步开始
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // 标记第一步为 RUNNING
        WorkflowStep first = wf.currentStep();
        if (first != null) {
            first.setStatus(StepStatus.RUNNING);
        }

        log.info("[workflow2] 创建工作流: id={}, title={}, steps={}", wf.getId(), title, steps.size());
        return wf;
    }

    /**
     * 标记当前步骤完成。
     */
    public MarkResult markCurrentDone(SimpleWorkflow wf, String result) {
        WorkflowStep step = wf.currentStep();
        if (step == null) {
            return MarkResult.error("没有活跃步骤");
        }
        step.setStatus(StepStatus.DONE);
        step.setResult(result);
        step.setCompletedAt(Instant.now());
        wf.setUpdatedAt(Instant.now());

        // 推进到下一步
        int next = wf.advance();
        if (next < 0) {
            wf.setStatus("COMPLETED");
            wf.setCompletedAt(Instant.now());
            log.info("[workflow2] 工作流完成: id={}", wf.getId());
            return MarkResult.completed("工作流全部完成");
        }

        // 标记下一步为 RUNNING
        WorkflowStep nextStep = wf.currentStep();
        if (nextStep != null) {
            nextStep.setStatus(StepStatus.RUNNING);
        }

        return MarkResult.progress("步骤 " + (next - 1) + "/" + wf.getSteps().size() + " 完成，当前: 步骤 " + next);
    }

    /**
     * 标记当前步骤失败。
     */
    public MarkResult markCurrentFailed(SimpleWorkflow wf, String error) {
        WorkflowStep step = wf.currentStep();
        if (step == null) {
            return MarkResult.error("没有活跃步骤");
        }
        step.setStatus(StepStatus.FAILED);
        step.setResult(error);
        step.setCompletedAt(Instant.now());
        wf.setStatus("FAILED");
        wf.setUpdatedAt(Instant.now());
        log.warn("[workflow2] 工作流失败: id={}, step={}, error={}", wf.getId(), step.getId(), error);
        return MarkResult.failed("步骤 " + wf.getCurrentStepIndex() + " 失败: " + error);
    }

    /**
     * 跳过当前步骤。
     */
    public MarkResult skipCurrent(SimpleWorkflow wf, String reason) {
        WorkflowStep step = wf.currentStep();
        if (step == null) {
            return MarkResult.error("没有活跃步骤");
        }
        step.setStatus(StepStatus.SKIPPED);
        step.setResult(reason);
        step.setCompletedAt(Instant.now());
        wf.setUpdatedAt(Instant.now());

        int next = wf.advance();
        if (next < 0) {
            wf.setStatus("COMPLETED");
            wf.setCompletedAt(Instant.now());
            return MarkResult.completed("工作流全部完成（有跳过步骤）");
        }

        WorkflowStep nextStep = wf.currentStep();
        if (nextStep != null) {
            nextStep.setStatus(StepStatus.RUNNING);
        }

        return MarkResult.progress("步骤 " + (next - 1) + " 已跳过");
    }

    /**
     * 构建状态 JSON（用于工具返回）。
     * 避免依赖 agent4j-web 的 DTO 类。
     */
    public org.noear.snack4.ONode toStatusJson(SimpleWorkflow wf) {
        var resp = new org.noear.snack4.ONode();
        resp.set("workflowId", wf.getId());
        resp.set("title", wf.getTitle());
        resp.set("status", wf.getStatus());
        resp.set("currentStepIndex", wf.getCurrentStepIndex());
        resp.set("totalSteps", wf.getSteps().size());
        resp.set("progress", wf.progressText());

        var stepsArr = org.noear.snack4.ONode.ofJson("[]").asArray();
        resp.set("steps", stepsArr);
        for (var step : wf.getSteps()) {
            var s = stepsArr.addNew();
            s.set("id", step.getId());
            s.set("description", step.getDescription());
            s.set("kind", step.getKind().name());
            s.set("status", step.getStatus().name());
            if (step.getResult() != null) s.set("result", step.getResult());
        }

        return resp;
    }

    // ==================== 内部类型 ====================

    /** 步骤定义（创建时传入） */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class StepDef {
        private String description;
        private StepKind kind;
    }

    /** 标记结果 */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class MarkResult {
        private String type;     // "progress" | "completed" | "failed" | "error"
        private String message;

        public static MarkResult progress(String msg) { return new MarkResult("progress", msg); }
        public static MarkResult completed(String msg) { return new MarkResult("completed", msg); }
        public static MarkResult failed(String msg) { return new MarkResult("failed", msg); }
        public static MarkResult error(String msg) { return new MarkResult("error", msg); }

        public boolean isError() { return "error".equals(type); }
        public boolean isCompleted() { return "completed".equals(type); }
        public boolean isFailed() { return "failed".equals(type); }
    }
}
