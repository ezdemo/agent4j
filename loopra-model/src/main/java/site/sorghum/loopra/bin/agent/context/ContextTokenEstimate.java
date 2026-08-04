package site.sorghum.loopra.bin.agent.context;

/**
 * 当前请求的离线上下文 token 构成。
 * <p>仅用于容量预估和界面展示；服务端 usage 仍是计费和实际用量的权威来源。</p>
 */
public record ContextTokenEstimate(
        int systemTokens,
        int toolDefinitionTokens,
        int userTokens,
        int assistantTokens,
        int toolResultTokens,
        int totalTokens,
        boolean exactTokenizer,
        String estimator
) {
}
