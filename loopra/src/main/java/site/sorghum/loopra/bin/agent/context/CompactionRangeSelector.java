package site.sorghum.loopra.bin.agent.context;

import site.sorghum.loopra.bin.agent.model.ChatMessage;

import java.util.List;

/**
  * 选择最旧的表层区间进行压缩，同时保留近期 token 预算，
  * 且绝不拆散助手工具调用/结果对。
 */
public final class CompactionRangeSelector {

    private CompactionRangeSelector() {
    }

    /** 压缩选区：start/end 为被折叠的表层区间，keepFromIndex 为保留区起点。 */
    public record Selection(int start, int end, int keepFromIndex) {
        public int shadowedCount() {
            return end - start + 1;
        }
    }

    public static Selection select(List<ChatMessage> history, int retainTokens) {
        if (history == null || history.isEmpty()) return null;

        int accumulated = 0;
        int keepFromIndex = history.size();
        for (int i = history.size() - 1; i >= 0; i--) {
            accumulated += ContextTokenEstimator.estimateMessage(history.get(i));
            keepFromIndex = i;
            if (accumulated >= retainTokens) break;
        }
        if (keepFromIndex == 0) return null;

        while (keepFromIndex > 0 && !balancedBefore(history, keepFromIndex)) {
            keepFromIndex--;
        }
        if (keepFromIndex == 0) return null;
        return new Selection(0, keepFromIndex - 1, keepFromIndex);
    }

    static boolean balancedBefore(List<ChatMessage> messages, int index) {
        int pendingToolCalls = 0;
        for (int i = 0; i < index; i++) {
            ChatMessage message = messages.get(i);
            if (message.isAssistant() && message.hasToolCalls()) {
                pendingToolCalls += message.getToolCalls().size();
            } else if (message.isTool()) {
                pendingToolCalls--;
            }
            if (pendingToolCalls < 0) return false;
        }
        return pendingToolCalls == 0;
    }
}
