package site.sorghum.loopra.bin.agent.context;

/**
  * 下一次请求压力快照，以及由路由模型上下文窗口推导出的压缩预算。
 */
public record ContextPressure(
        ContextTokenEstimate estimate,
        int lastPromptTokens,
        int effectivePromptTokens,
        int contextWindow,
        int thresholdTokens,
        int retainTokens,
        boolean shouldCompact
) {
}
