package site.sorghum.agent4j.bin.agent.listener;

/**
 * Agent 循环事件监听器 —— 用于观察推理、工具调用、工具结果和 token 用量。
 * <p>
 * 从 AgentLoop 内接口提取为独立文件，便于复用和测试 mock。
 * </p>
 *
 * @author Sorghum
 */
public interface AgentLoopListener {

    /**
     * 模型在思考（reasoning_content）
     */
    default void onReasoning(String reasoning) {
    }

    /**
     * 模型调用了工具
     */
    default void onToolCall(String name, String args) {
    }

    /**
     * 工具返回结果
     */
    default void onToolResult(String name, String result) {
    }

    /**
     * token 用量回调。
     *
     */
    default void onUsage() {
    }

    /**
     * Token 用量回调（含模型名称）
     */
    default void onUsage(String model, int promptTokens, int completionTokens, int totalTokens,
                         int cacheHit, int cacheMiss) {
        onUsage();
    }
}
