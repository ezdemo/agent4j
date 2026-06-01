package site.sorghum.agent4j.bin.agent;

import java.util.List;

/**
 * AgentOutput 的无操作空实现 —— 关闭所有输出。
 * <p>
 * 替代原来在 AgentOutput 接口中的匿名类实例 {@code AgentOutput.NOOP}。
 * </p>
 *
 * @author Sorghum
 */
public final class NoOpAgentOutput implements AgentOutput {

    public static final NoOpAgentOutput INSTANCE = new NoOpAgentOutput();

    private NoOpAgentOutput() {
    }

    @Override
    public void onContentDelta(String token) {
    }

    @Override
    public void onContentComplete() {
    }

    @Override
    public void onReasoningDelta(String token) {
    }

    @Override
    public void onReasoningComplete() {
    }

    @Override
    public void onReasoning(String reasoning) {
    }

    @Override
    public void onToolCall(String name, String args) {
    }

    @Override
    public void onToolResult(String name, String result) {
    }

    @Override
    public void onUsage(int promptTokens, int completionTokens, int totalTokens, int cacheHit, int cacheMiss) {
    }

    @Override
    public void onUsage(String model, int promptTokens, int completionTokens, int totalTokens, int cacheHit, int cacheMiss) {
    }

    @Override
    public void onError(String error) {
    }

    @Override
    public void onLog(LogLevel level, String message) {
    }

    @Override
    public void onMessage(String message) {
    }

    @Override
    public void onChoice(List<ChoiceOption> options) {
    }
}
