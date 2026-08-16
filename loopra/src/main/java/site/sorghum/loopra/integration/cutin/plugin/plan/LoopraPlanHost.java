package site.sorghum.loopra.integration.cutin.plugin.plan;

/**
  * 持有待定计划持久化边界的宿主切片。
 */
public interface LoopraPlanHost {

    String PLAN_SUBMITTED = "ON_PLAN_SUBMITTED";
    String PLAN_CLEARED = "ON_PLAN_CLEARED";

    void persistPendingPlan(String planMarkdown);
}
