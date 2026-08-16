package site.sorghum.loopra.bin.agent.context;

import site.sorghum.loopra.bin.agent.spi.AgentConfig;

/**
  * 基于 token 的压缩策略，自动与手动上下文折叠共用。
 */
public record ContextCompactionPolicy(
        double thresholdRatio,
        double retainRatio,
        int compactionRetries
) {

    public static final double DEFAULT_THRESHOLD_RATIO = 0.8;
    public static final double DEFAULT_RETAIN_RATIO = 0.16;
    public static final int DEFAULT_COMPACTION_RETRIES = 1;

    public ContextCompactionPolicy {
        if (!Double.isFinite(thresholdRatio) || thresholdRatio <= 0 || thresholdRatio > 1) {
            throw new IllegalArgumentException("thresholdRatio must be in (0, 1]");
        }
        if (!Double.isFinite(retainRatio) || retainRatio <= 0 || retainRatio >= thresholdRatio) {
            throw new IllegalArgumentException("retainRatio must be in (0, thresholdRatio)");
        }
        if (compactionRetries < 0) {
            throw new IllegalArgumentException("compactionRetries must be non-negative");
        }
    }

    public static ContextCompactionPolicy defaults() {
        return new ContextCompactionPolicy(
                DEFAULT_THRESHOLD_RATIO,
                DEFAULT_RETAIN_RATIO,
                DEFAULT_COMPACTION_RETRIES
        );
    }

    public static ContextCompactionPolicy from(AgentConfig config) {
        if (config == null) return defaults();
        return new ContextCompactionPolicy(
                config.compactionThresholdRatio(),
                config.compactionRetainRatio(),
                config.compactionRetries()
        );
    }
}
