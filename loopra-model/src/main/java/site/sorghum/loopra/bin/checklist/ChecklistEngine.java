package site.sorghum.loopra.bin.checklist;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ChecklistEngine — 执行清单引擎。
 * <p>
 * 管理 Checklist 的创建、推进、标记完成/失败/跳过等操作。
 * 持久化通过 WorkspaceManager 的 KV store 完成（Checklist 本身是 POJO，直接 JSON 序列化）。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
@Component
public class ChecklistEngine {

    /**
     * 创建清单。
     */
    public Checklist createChecklist(String sessionId, String workspaceHash,
                                      String title, String description,
                                      List<StepDef> stepDefs) {
        List<ChecklistStep> steps = new ArrayList<>();
        for (int i = 0; i < stepDefs.size(); i++) {
            StepDef def = stepDefs.get(i);
            steps.add(ChecklistStep.builder()
                    .id("step-" + (i + 1))
                    .description(def.description)
                    .kind(def.kind != null ? def.kind : StepKind.STEP)
                    .status(StepStatus.PENDING)
                    .createdAt(Instant.now())
                    .build());
        }

        Checklist cl = Checklist.builder()
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
        ChecklistStep first = cl.currentStep();
        if (first != null) {
            first.setStatus(StepStatus.RUNNING);
        }

        log.info("[checklist] 创建清单: id={}, title={}, steps={}", cl.getId(), title, steps.size());
        return cl;
    }

    /**
     * 标记当前步骤完成。
     */
    public MarkResult markCurrentDone(Checklist cl, String result) {
        ChecklistStep step = cl.currentStep();
        if (step == null) {
            return MarkResult.error("没有活跃步骤");
        }
        step.setStatus(StepStatus.DONE);
        step.setResult(result);
        step.setCompletedAt(Instant.now());
        cl.setUpdatedAt(Instant.now());

        // 推进到下一步
        int next = cl.advance();
        if (next < 0) {
            cl.setStatus("COMPLETED");
            cl.setCompletedAt(Instant.now());
            log.info("[checklist] 清单完成: id={}", cl.getId());
            return MarkResult.completed("清单全部完成");
        }

        // 标记下一步为 RUNNING
        ChecklistStep nextStep = cl.currentStep();
        if (nextStep != null) {
            nextStep.setStatus(StepStatus.RUNNING);
        }

        return MarkResult.progress("步骤 " + (next - 1) + "/" + cl.getSteps().size() + " 完成，当前: 步骤 " + next);
    }

    /**
     * 标记当前步骤失败。
     */
    public MarkResult markCurrentFailed(Checklist cl, String error) {
        ChecklistStep step = cl.currentStep();
        if (step == null) {
            return MarkResult.error("没有活跃步骤");
        }
        step.setStatus(StepStatus.FAILED);
        step.setResult(error);
        step.setCompletedAt(Instant.now());
        cl.setStatus("FAILED");
        cl.setUpdatedAt(Instant.now());
        log.warn("[checklist] 清单失败: id={}, step={}, error={}", cl.getId(), step.getId(), error);
        return MarkResult.failed("步骤 " + cl.getCurrentStepIndex() + " 失败: " + error);
    }

    /**
     * 跳过当前步骤。
     */
    public MarkResult skipCurrent(Checklist cl, String reason) {
        ChecklistStep step = cl.currentStep();
        if (step == null) {
            return MarkResult.error("没有活跃步骤");
        }
        step.setStatus(StepStatus.SKIPPED);
        step.setResult(reason);
        step.setCompletedAt(Instant.now());
        cl.setUpdatedAt(Instant.now());

        int next = cl.advance();
        if (next < 0) {
            cl.setStatus("COMPLETED");
            cl.setCompletedAt(Instant.now());
            return MarkResult.completed("清单全部完成（有跳过步骤）");
        }

        ChecklistStep nextStep = cl.currentStep();
        if (nextStep != null) {
            nextStep.setStatus(StepStatus.RUNNING);
        }

        return MarkResult.progress("步骤 " + (next - 1) + " 已跳过");
    }

    /**
     * 构建状态 JSON（用于工具返回）。
     * 避免依赖 loopra-web 的 DTO 类。
     */
    public org.noear.snack4.ONode toStatusJson(Checklist cl) {
        var resp = new org.noear.snack4.ONode();
        resp.set("checklistId", cl.getId());
        resp.set("title", cl.getTitle());
        resp.set("status", cl.getStatus());
        resp.set("currentStepIndex", cl.getCurrentStepIndex());
        resp.set("totalSteps", cl.getSteps().size());
        resp.set("progress", cl.progressText());

        var stepsArr = org.noear.snack4.ONode.ofJson("[]").asArray();
        resp.set("steps", stepsArr);
        for (var step : cl.getSteps()) {
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
