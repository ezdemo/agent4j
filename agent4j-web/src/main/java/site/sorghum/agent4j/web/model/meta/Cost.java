package site.sorghum.agent4j.web.model.meta;

import java.util.List;

/**
 * 模型定价信息。
 * <p>
 * JSON 结构示例：
 * <pre>
 * {
 *   "input": 3,
 *   "output": 15,
 *   "cache_read": 0.75,
 *   "cache_write": 3,
 *   "tiers": [ ... ],
 *   "context_over_200k": { ... }
 * }
 * </pre>
 * </p>
 *
 * @param input              输入价格（每百万 token）
 * @param output             输出价格（每百万 token）
 * @param cache_read         缓存读取价格（每百万 token）
 * @param cache_write        缓存写入价格（每百万 token）
 * @param tiers              分层定价列表（可选）
 * @param context_over_200k  上下文超过 200k token 时的定价（可选）
 */
public record Cost(
        double input,
        double output,
        double cache_read,
        double cache_write,
        List<CostTier> tiers,
        ContextOver200k context_over_200k
) {
    /**
     * 上下文超过 200k token 时的定价信息。
     *
     * @param input      输入价格（每百万 token）
     * @param output     输出价格（每百万 token）
     * @param cache_read 缓存读取价格（每百万 token）
     */
    public record ContextOver200k(
            double input,
            double output,
            double cache_read
    ) {
    }

    /**
     * 计算指定 token 数量的输入成本（美元）。
     *
     * @param tokens token 数量
     * @return 成本（美元）
     */
    public double calculateInputCost(long tokens) {
        return (tokens / 1_000_000.0) * input;
    }

    /**
     * 计算指定 token 数量的输出成本（美元）。
     *
     * @param tokens token 数量
     * @return 成本（美元）
     */
    public double calculateOutputCost(long tokens) {
        return (tokens / 1_000_000.0) * output;
    }

    /**
     * 检查是否有分层定价。
     *
     * @return true 表示有分层定价
     */
    public boolean hasTiers() {
        return tiers != null && !tiers.isEmpty();
    }

    /**
     * 检查是否有上下文超过 200k 的特殊定价。
     *
     * @return true 表示有特殊定价
     */
    public boolean hasContextOver200k() {
        return context_over_200k != null;
    }
}