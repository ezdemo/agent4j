package site.sorghum.agent4j.bin.agent;

/**
 * Agent 输出抽象接口 —— 所有 Agent 向外输出的内容都通过此接口发送。
 * <p>
 * 将"输出"与"业务逻辑"解耦，当前实现 {@link ConsoleAgentOutput} 打印到控制台。
 * 可替换为其他实现（如 WebSocket SSE、日志文件、测试 Mock 等），
 * 实现不同场景下的输出处理。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 控制台输出（默认行为）
 * agent.setOutput(new ConsoleAgentOutput());
 *
 * // 自定义输出（如收集到 StringBuilder）
 * agent.setOutput(new AgentOutput() {
 *     public void onContentDelta(String token) { buffer.append(token); }
 *     // ... 其他方法
 * });
 * }</pre>
 *
 * @author Sorghum
 */
public interface AgentOutput {

    // ==================== 流式输出 ====================

    /** 流式内容增量（模型回复的文本片段） */
    void onContentDelta(String token);

    /** 流式内容结束（模型回复完成） */
    void onContentComplete();

    /** 流式思考增量（reasoning_content 的文本片段） */
    void onReasoningDelta(String token);

    /** 流式思考结束 */
    void onReasoningComplete();

    // ==================== 事件 ====================

    /** 完整思考内容（非流式场景，如无 tool_calls 时模型返回纯思考） */
    void onReasoning(String reasoning);

    /** 工具调用事件 */
    void onToolCall(String name, String args);

    /** 工具结果事件 */
    void onToolResult(String name, String result);

    /** Token 用量回调 */
    void onUsage(int promptTokens, int completionTokens, int totalTokens,
                 int cacheHit, int cacheMiss);

    /** Token 用量回调（含模型名称，用于按模型分别计费） */
    default void onUsage(String model, int promptTokens, int completionTokens, int totalTokens,
                         int cacheHit, int cacheMiss) {
        onUsage(promptTokens, completionTokens, totalTokens, cacheHit, cacheMiss);
    }

    /** 错误信息 */
    void onError(String error);

    // ==================== 日志 & 消息 ====================

    /** 日志消息（调试/信息/警告/错误日志） */
    void onLog(LogLevel level, String message);

    /** 普通文本消息（如 Agent4jApp 中的提示信息） */
    void onMessage(String message);

    /** 选项列表（如 HITL 审批：同意/拒绝） */
    void onChoice(java.util.List<ChoiceOption> options);

    // ==================== 数据类型 ====================

    /** 日志级别 */
    enum LogLevel {
        DEBUG, INFO, WARN, ERROR
    }

    /** 选项（value = 发送的消息，title = 展示文本） */
    record ChoiceOption(String value, String title) {}

    // ==================== NOOP 空实现 ====================

    /** 无操作的空实现 —— 关闭所有输出 */
    AgentOutput NOOP = new AgentOutput() {
        @Override public void onContentDelta(String token) {}
        @Override public void onContentComplete() {}
        @Override public void onReasoningDelta(String token) {}
        @Override public void onReasoningComplete() {}
        @Override public void onReasoning(String reasoning) {}
        @Override public void onToolCall(String name, String args) {}
        @Override public void onToolResult(String name, String result) {}
        @Override public void onUsage(int promptTokens, int completionTokens, int totalTokens, int cacheHit, int cacheMiss) {}
        @Override public void onUsage(String model, int promptTokens, int completionTokens, int totalTokens, int cacheHit, int cacheMiss) {}
        @Override public void onError(String error) {}
        @Override public void onLog(LogLevel level, String message) {}
        @Override public void onMessage(String message) {}
        @Override public void onChoice(java.util.List<ChoiceOption> options) {}
    };
}
