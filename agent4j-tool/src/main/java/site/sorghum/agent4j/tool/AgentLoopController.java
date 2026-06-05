package site.sorghum.agent4j.tool;

/**
 * AgentLoop 控制接口 —— 工具通过此接口影响推理循环的控制流。
 * <p>
 * 由 {@code AgentLoop} 实现并注入到 {@link ToolContext} 中，
 * 工具在执行过程中可通过 {@link ToolContext#getLoopController()} 获取此引用。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * AgentLoopController ctrl = ctx.getLoopController();
 * if (ctrl != null) {
 *     ctrl.getOutput().onLog(LogLevel.INFO, "进度信息");  // 直接输出
 *     ctrl.requestStop();                                 // 停止推理
 *     ctrl.injectUserMessage("继续...");                  // 注入消息
 * }
 * }</pre>
 *
 * @author Sorghum
 */
public interface AgentLoopController {

    /**
     * 获取 Agent 输出通道。
     * <p>工具可通过此接口推送日志、错误、自定义事件等内容到前端/控制台。
     * 返回 {@code null} 时表示无关联的输出通道（如后台任务、测试环境）。</p>
     */
    AgentOutput getOutput();

    /**
     * 停止推理循环 —— 等效于用户点击"停止"按钮。
     */
    void requestStop();

    /**
     * 在下一轮循环前注入一条用户消息。
     *
     * @param message 要注入的用户消息内容
     */
    void injectUserMessage(String message);

    /**
     * 向下游推送自定义事件。
     * <p>事件通过 {@code AgentOutput.sendEvent(type, data)} 发送。</p>
     *
     * @param type 事件类型标识符，如 {@code "build_progress"}
     * @param data JSON 格式的事件数据
     */
    default void emitEvent(String type, String data) {
        AgentOutput out = getOutput();
        if (out != null) {
            out.sendEvent(type, data);
        }
    }
}
