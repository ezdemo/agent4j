package site.sorghum.agent4j.web.common;

/**
 * Web 层错误消息常量 —— 避免在 Controller 中重复硬编码相同字符串。
 *
 * @author Sorghum
 */
public final class WebErrorMessages {

    private WebErrorMessages() {
        // 工具类，禁止实例化
    }

    /** Agent 未就绪时的通用错误消息 */
    public static final String AGENT_NOT_READY = "Agent 未初始化";

    /** 工作区 hash 不能为空 */
    public static final String WORKSPACE_HASH_REQUIRED = "工作区 hash 不能为空";

    /** message 不能为空 */
    public static final String MESSAGE_REQUIRED = "message 不能为空";

    /** slug is required */
    public static final String SLUG_REQUIRED = "slug is required";
}
