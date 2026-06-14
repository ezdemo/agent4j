package site.sorghum.agent4j.web.common;

import site.sorghum.agent4j.bin.config.Agent4jConfig;

import java.util.Collections;
import java.util.Map;

/**
 * Token 费用计算工具 —— 纯函数，无状态，供 AgentService / DashboardService 等复用。
 *
 * @author Sorghum
 */
public final class UsageCostCalculator {

    private UsageCostCalculator() {}

    /**
     * 计算单个模型的 Token 费用。
     *
     * @param prices     价格配置 map（model → {input, cache, output}，单位：元/百万 token）
     * @param model      模型名称
     * @param prompt     输入 token 数
     * @param completion 输出 token 数
     * @param cacheHit   缓存命中 token 数
     * @return 费用（元），无价格配置时返回 0
     */
    public static double calc(Map<String, Map<String, Double>> prices,
                              String model, long prompt, long completion, long cacheHit) {
        if (prices == null) return 0;
        Map<String, Double> mp = prices.get(model);
        if (mp == null || mp.isEmpty()) return 0;
        double inputRate = mp.getOrDefault("input", 0.0);
        double cacheRate = mp.getOrDefault("cache", 0.0);
        double outputRate = mp.getOrDefault("output", 0.0);
        long nonCacheInput = Math.max(0, prompt - cacheHit);
        return nonCacheInput / 1_000_000.0 * inputRate
                + cacheHit / 1_000_000.0 * cacheRate
                + completion / 1_000_000.0 * outputRate;
    }

    /**
     * 加载价格配置，失败时返回空 Map。
     */
    public static Map<String, Map<String, Double>> loadPrices() {
        try {
            Agent4jConfig cfg = Agent4jConfig.load();
            Map<String, Map<String, Double>> prices = cfg.price();
            return prices != null ? prices : Collections.emptyMap();
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    /**
     * 四舍五入到万分位。
     */
    public static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
