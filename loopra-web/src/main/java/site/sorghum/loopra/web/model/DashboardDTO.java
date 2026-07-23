package site.sorghum.loopra.web.model;

import java.util.List;
import java.util.Map;

/**
 * 数据面板返回值。
 *
 * @param totalPromptTokens  总输入 token
 * @param totalCompletionTokens 总输出 token
 * @param totalCacheHit      总缓存命中
 * @param totalCacheMiss     总缓存未命中
 * @param totalCost          总费用（元）
 * @param activeDays         活跃天数
 * @param totalRequests      总请求数（LLM 调用次数）
 * @param dailyStats         按天统计列表（最近 N 天）
 * @param modelStats         按模型汇总统计
 * @param modelPrices        模型价格（元/百万 token）model → {input, cache, output}
 */
public record DashboardDTO(
        long totalPromptTokens,
        long totalCompletionTokens,
        long totalCacheHit,
        long totalCacheMiss,
        double totalCost,
        int activeDays,
        long totalRequests,
        List<DailyStat> dailyStats,
        List<ModelStat> modelStats,
        Map<String, Map<String, Double>> modelPrices
) {
    /**
     * 单日统计。
     *
     * @param date          日期字符串（yyyy-MM-dd）
     * @param promptTokens  当日输入 token
     * @param completionTokens 当日输出 token
     * @param cacheHit      当日缓存命中
     * @param cacheMiss     当日缓存未命中
     * @param totalTokens   当日总 token
     * @param cost          当日费用
     * @param requests      当日请求数
     * @param modelBreakdown 按模型的细分
     */
    public record DailyStat(
            String date,
            long promptTokens,
            long completionTokens,
            long cacheHit,
            long cacheMiss,
            long totalTokens,
            double cost,
            long requests,
            Map<String, ModelUsage> modelBreakdown
    ) {}

    /**
     * 按模型汇总统计。
     *
     * @param model         模型名称
     * @param promptTokens  总输入 token
     * @param completionTokens 总输出 token
     * @param cacheHit      总缓存命中
     * @param totalTokens   总 token
     * @param cost          总费用
     * @param requests      调用次数
     */
    public record ModelStat(
            String model,
            long promptTokens,
            long completionTokens,
            long cacheHit,
            long totalTokens,
            double cost,
            long requests
    ) {}

    /**
     * 单次用量记录（模型级别）。
     */
    public record ModelUsage(
            long promptTokens,
            long completionTokens,
            long cacheHit,
            long cacheMiss,
            long totalTokens
    ) {}
}
