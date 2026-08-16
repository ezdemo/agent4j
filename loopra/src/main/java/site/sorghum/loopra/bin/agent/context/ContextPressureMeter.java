package site.sorghum.loopra.bin.agent.context;

import org.noear.snack4.ONode;
import site.sorghum.loopra.bin.agent.model.ChatMessage;

import java.util.List;

/**
  * 用于压缩决策的统一 token 压力测量。
 */
public final class ContextPressureMeter {

    private ContextPressureMeter() {
    }

    public static ContextPressure measure(
            List<ChatMessage> messages,
            ONode tools,
            String additionalSystemText,
            int lastPromptTokens,
            int contextWindow,
            ContextCompactionPolicy policy
    ) {
        ContextTokenEstimate estimate = ContextTokenEstimator.estimate(messages, tools, additionalSystemText);
        int effectivePromptTokens = Math.max(estimate.totalTokens(), lastPromptTokens);
        int thresholdTokens = (int) Math.floor(contextWindow * policy.thresholdRatio());
        int retainTokens = (int) Math.floor(contextWindow * policy.retainRatio());
        boolean shouldCompact = contextWindow > 0 && effectivePromptTokens > thresholdTokens;
        return new ContextPressure(
                estimate,
                lastPromptTokens,
                effectivePromptTokens,
                contextWindow,
                thresholdTokens,
                retainTokens,
                shouldCompact
        );
    }
}
