package site.sorghum.cutin.core.context;

/**
 * 一次模型调用的用量统计。
 *
 * <p>{@code promptTokens} 表示包含缓存命中与缓存创建在内的全部输入 token，
 * 以便上下文估算、预算与缓存命中率计算使用同一份口径；缓存字段是其子集。</p>
 *
 * @param promptTokens      全部输入 token 数（含缓存命中与缓存创建）
 * @param completionTokens  输出 token 数
 * @param costMicros        费用（微元）
 * @param cacheReadTokens   缓存命中（读取）token 数
 * @param cacheCreationTokens 缓存创建（写入）token 数
 */
public record Usage(
    long promptTokens,
    long completionTokens,
    long costMicros,
    long cacheReadTokens,
    long cacheCreationTokens
) {

    /** 零用量常量，表示未产生任何消耗。 */
    public static final Usage ZERO = new Usage(0, 0, 0, 0, 0);

    /** 兼容旧调用方：不携带缓存明细的用量。 */
    public Usage(long promptTokens, long completionTokens, long costMicros) {
        this(promptTokens, completionTokens, costMicros, 0, 0);
    }

    /** 输入与输出 token 之和。 */
    public long totalTokens() {
        return promptTokens + completionTokens;
    }

    /** 缓存命中 token 数，等价于缓存读取 token 数。 */
    public long cacheHitTokens() {
        return cacheReadTokens;
    }

    /** 缓存未命中 token 数：全部输入减去缓存读取后的剩余部分（含缓存创建）。 */
    public long cacheMissTokens() {
        return Math.max(0, promptTokens - cacheReadTokens);
    }

    /** 与另一份用量逐项相加，得到累计用量。 */
    public Usage add(Usage other) {
        return new Usage(
            promptTokens + other.promptTokens,
            completionTokens + other.completionTokens,
            costMicros + other.costMicros,
            cacheReadTokens + other.cacheReadTokens,
            cacheCreationTokens + other.cacheCreationTokens
        );
    }
}
