package site.sorghum.agent4j.tool.plan;

import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.tool.ErrorMessages;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 计划服务 —— 管理计划提交、步骤完成标记、计划修订的状态机。
 *
 * @author Sorghum
 */
@Component
public class PlanService {

    private Map<String, PlanStep> currentPlan = null;
    private int currentPlanStepIndex = 0;

    @SuppressWarnings("unchecked")
    public String submitPlan(String summary, String planBody, List<Map<String, Object>> stepsRaw) {
        if (stepsRaw == null || stepsRaw.isEmpty())
            return ErrorMessages.SUBMIT_PLAN_REQUIRES_STEPS;
        currentPlan = new LinkedHashMap<>();
        currentPlanStepIndex = 0;
        for (Map<String, Object> s : stepsRaw) {
            String id = (String) s.getOrDefault("id", "step-" + (currentPlan.size() + 1));
            String title = (String) s.getOrDefault("title", id);
            String action = (String) s.getOrDefault("action", "");
            currentPlan.put(id, new PlanStep(id, title, action));
        }
        return "[Plan submitted: " + summary + " (" + currentPlan.size() + " steps)]";
    }

    public String markStepComplete(String stepId, String result,
                                   List<Map<String, Object>> evidence) {
        if (currentPlan == null) return ErrorMessages.NO_ACTIVE_PLAN;
        PlanStep step = currentPlan.get(stepId);
        if (step == null) return ErrorMessages.stepNotFound(stepId);
        step.completed = true;
        currentPlanStepIndex++;
        return "step " + stepId + " (" + step.title + ") marked complete: "
                + (result != null ? result : "");
    }

    public String revisePlan(String reason, List<Map<String, Object>> remainingSteps) {
        if (currentPlan == null) return ErrorMessages.NO_ACTIVE_PLAN;
        currentPlan.values().stream().filter(s -> !s.completed).forEach(s -> s.completed = true);
        for (Map<String, Object> s : remainingSteps) {
            String id = (String) s.getOrDefault("id", "step-" + (currentPlan.size() + 1));
            String title = (String) s.getOrDefault("title", id);
            String action = (String) s.getOrDefault("action", "");
            currentPlan.put(id, new PlanStep(id, title, action));
        }
        return "[Plan revised: " + reason + " (" + remainingSteps.size() + " remaining steps)]";
    }

    public static class PlanStep {
        public final String id;
        public final String title;
        public final String action;
        public boolean completed = false;

        PlanStep(String id, String title, String action) {
            this.id = id;
            this.title = title;
            this.action = action;
        }
    }
}
