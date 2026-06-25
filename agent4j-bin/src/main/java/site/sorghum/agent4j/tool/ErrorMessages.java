package site.sorghum.agent4j.tool;

/**
 * 错误消息常量。
 *
 * @author Sorghum
 */
public final class ErrorMessages {

    private ErrorMessages() {
        // 工具类，禁止实例化
    }

    /**
     * ask_choice 工具需要至少一个选项
     */
    public static final String ASK_CHOICE_REQUIRES_OPTIONS = "{\"error\":\"ask_choice requires at least one option\"}";

    /**
     * submit_plan 工具需要至少一个步骤
     */
    public static final String SUBMIT_PLAN_REQUIRES_STEPS = "{\"error\":\"submit_plan requires at least one step\"}";

    /**
     * 没有活跃的计划
     */
    public static final String NO_ACTIVE_PLAN = "{\"error\":\"no active plan\"}";

    /**
     * 生成"步骤未找到"的错误消息。
     *
     * @param stepId 未找到的步骤标识
     * @return JSON 格式的错误消息
     */
    public static String stepNotFound(String stepId) {
        return String.format("{\"error\":\"step not found: %s\"}", stepId);
    }
}
