package site.sorghum.loopra.integration.cutin.plugin.retry;

import site.sorghum.loopra.tool.AgentOutput;

/**
  * 面向 Loopra 可插拔模型重试策略的宿主切片。
 * <p>
  * 默认实现沿用历史 HTTP 客户端的退避行为，使重试移出传输层后 cutin 路径
  * 保持相同表现。宿主可以覆写任一方法以整体替换策略。
 * </p>
 */
public interface LoopraRetryHost {

    int[] DEFAULT_RETRY_DELAYS = {3, 3, 5, 5, 8, 10, 12, 16, 22, 36};
    int DEFAULT_MAX_RETRIES = DEFAULT_RETRY_DELAYS.length;

    AgentOutput getOutput();

    default int maxModelRetries() {
        return DEFAULT_MAX_RETRIES;
    }

    default int modelRetryDelaySeconds(int attempt) {
        int index = Math.max(0, Math.min(attempt, DEFAULT_RETRY_DELAYS.length - 1));
        return DEFAULT_RETRY_DELAYS[index];
    }
}
