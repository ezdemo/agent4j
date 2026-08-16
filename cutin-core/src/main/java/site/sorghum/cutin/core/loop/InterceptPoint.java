package site.sorghum.cutin.core.loop;

/**
 * 生命周期拦截点枚举。
 *
 * <p>每个拦截点拥有独立的、按 order 排序的拦截器链。模型、工具、
 * 重试、重入、输出与错误处理等阶段都有对应的切入位置。</p>
 */
public enum InterceptPoint {
    /** 循环开始前：初始化、会话策略、路由。 */
    PRE_LOOP,
    /** 循环结束后：审计、持久化、清理。 */
    POST_LOOP,
    /** 每个节点执行前：节点守卫、条件路由。 */
    BEFORE_STEP,
    /** 每个节点执行后：状态校验、追踪。 */
    AFTER_STEP,
    /** 模型调用前：上下文压缩、提示词守卫、模型路由。 */
    BEFORE_MODEL,
    /** 模型流式增量到达时：流式 UI、取消、成本检查。 */
    ON_MODEL_STREAM,
    /** 模型调用完成后：用量采集、响应校验。 */
    AFTER_MODEL,
    /** 工具执行前：权限、人工审批、限流。 */
    BEFORE_TOOL,
    /** 工具执行后：结果归一化、产物捕获。 */
    AFTER_TOOL,
    /** 工具执行出错时：重试、降级、权限升级。 */
    ON_TOOL_ERROR,
    /** 任意阶段发生未处理错误时。 */
    ON_ERROR,
    /** 重试执行前：退避、模型/工具替换。 */
    BEFORE_RETRY,
    /** 重入时：快照恢复、审计。 */
    ON_REENTER,
    /** 输出节点执行前：输出渲染、摘要。 */
    BEFORE_OUTPUT,
    /** 循环退出前：最终结果替换或继续跳转。 */
    BEFORE_EXIT,
    /** 模型调用失败时：错误策略、故障切换。 */
    ON_MODEL_ERROR,
    /** 一批工具执行前。 */
    BEFORE_TOOL_BATCH,
    /** 一批工具执行后。 */
    AFTER_TOOL_BATCH,
    /** 工具超时时。 */
    ON_TOOL_TIMEOUT,
    /** 工具被取消时。 */
    ON_TOOL_CANCEL,
    /** 整轮执行结束后。 */
    AFTER_TURN
}
