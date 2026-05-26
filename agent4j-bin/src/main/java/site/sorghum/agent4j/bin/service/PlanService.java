package site.sorghum.agent4j.bin.service;

import org.noear.solon.annotation.Component;

import java.util.*;

/**
 * 计划服务 —— 管理计划提交、步骤完成标记、计划修订的状态机。
 * <p>
 * 从 Tools.java 中抽出，提供内存中的计划生命周期管理。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class PlanService {

    private Map<String, PlanStep> currentPlan = null;
    private int currentPlanStepIndex = 0;

    /** 计划步骤 */
    public static class PlanStep {
        public final String id;
        public final String title;
        public final String action;
        public boolean completed = false;

        PlanStep(String id, String title, String action) {
            this.id = id; this.title = title; this.action = action;
        }
    }

    /**
     * 提交一份新计划，清空当前进行中的计划。
     *
     * @param summary   计划标题摘要
     * @param planBody  计划的 Markdown 内容
     * @param stepsRaw  步骤列表（[{id, title, action}, ...]）
     * @return 提交确认消息
     */
    @SuppressWarnings("unchecked")
    public String submitPlan(String summary, String planBody, List<Map<String, Object>> stepsRaw) {
        if (stepsRaw == null || stepsRaw.isEmpty())
            return "{\"error\":\"submit_plan requires at least one step\"}";
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

    /**
     * 标记计划中的一步为已完成。
     *
     * @param stepId    步骤 ID
     * @param result    执行结果描述
     * @param evidence  验证依据列表
     * @return 完成确认消息
     */
    public String markStepComplete(String stepId, String result,
                                    List<Map<String, Object>> evidence) {
        if (currentPlan == null) return "{\"error\":\"no active plan\"}";
        PlanStep step = currentPlan.get(stepId);
        if (step == null) return "{\"error\":\"step not found: " + stepId + "\"}";
        step.completed = true;
        currentPlanStepIndex++;
        return "step " + stepId + " (" + step.title + ") marked complete: "
                + (result != null ? result : "");
    }

    /**
     * 修订进行中的计划，替换剩余步骤。
     * 已完成的步骤保持不变。
     *
     * @param reason         修订原因
     * @param remainingSteps 新的剩余步骤列表
     * @return 修订确认消息
     */
    public String revisePlan(String reason, List<Map<String, Object>> remainingSteps) {
        if (currentPlan == null) return "{\"error\":\"no active plan\"}";
        currentPlan.values().stream().filter(s -> !s.completed).forEach(s -> s.completed = true);
        for (Map<String, Object> s : remainingSteps) {
            String id = (String) s.getOrDefault("id", "step-" + (currentPlan.size() + 1));
            String title = (String) s.getOrDefault("title", id);
            String action = (String) s.getOrDefault("action", "");
            currentPlan.put(id, new PlanStep(id, title, action));
        }
        return "[Plan revised: " + reason + " (" + remainingSteps.size() + " remaining steps)]";
    }
}
