package site.sorghum.loopra.web.model.meta;

/**
 * 分层定价信息。
 * <p>
 * JSON 结构示例：
 * <pre>
 * {
 *   "input": 2.5,
 *   "output": 15,
 *   "cache_read": 0.25,
 *   "cache_write": 2.375,
 *   "tier": {
 *     "type": "context",
 *     "size": 200000
 *   }
 * }
 * </pre>
 * </p>
 *
 * @param input        输入价格（每百万 token）
 * @param output       输出价格（每百万 token）
 * @param cache_read   缓存读取价格（每百万 token）
 * @param cache_write  缓存写入价格（每百万 token）
 * @param tier         分层条件
 */
public record CostTier(
        double input,
        double output,
        double cache_read,
        double cache_write,
        TierCondition tier
) {
    /**
     * 分层条件。
     *
     * @param type 条件类型（如 "context"）
     * @param size 条件阈值（如 200000 token）
     */
    public record TierCondition(
            String type,
            long size
    ) {
    }
}