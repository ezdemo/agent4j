package site.sorghum.loopra.bin.agent.spi;

/**
 * 会话用量上报 SPI —— AgentLoop / SubAgent 借此把 token 用量回写到会话持久层。
 * <p>
 * 内核仅依赖本接口，不感知会话持久化实现；由上层模块（loopra-harness 的 SessionService）
 * 实现后注入。未设置（{@code null}）时内核跳过用量上报。
 * </p>
 *
 * @author Sorghum
 */
public interface SessionUsageSink {

    /**
     * 记录最近一次 API 返回的 prompt_tokens（用于上下文占用展示）。
     *
     * @param lastPromptTokens 最近一次请求的 prompt token 数
     */
    void updateLastPromptTokens(int lastPromptTokens);

    /**
     * 按模型累计 token 用量。
     *
     * @param model            模型名
     * @param promptTokens     prompt token 数
     * @param completionTokens completion token 数
     * @param cacheHit         缓存命中 token 数
     * @param cacheMiss        缓存未命中 token 数
     */
    default void addUsage(String model, int promptTokens, int completionTokens, int cacheHit, int cacheMiss) {
    }
}
