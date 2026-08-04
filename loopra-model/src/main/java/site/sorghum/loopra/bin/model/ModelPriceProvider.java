package site.sorghum.loopra.bin.model;

import org.noear.solon.data.cache.CacheService;
import org.noear.solon.data.cache.LocalCacheService;

import java.util.Map;

/**
 * 模型价格提供者接口 —— 用于从外部源（如 ModelMetaService）获取模型的价格信息。
 * <p>
 * 实现此接口可以为不同的模型提供价格信息，
 * 用于覆盖默认的价格配置。
 * </p>
 *
 * @author Sorghum
 */
public interface ModelPriceProvider {
    CacheService cacheService = new LocalCacheService();

    /**
     * 获取指定模型的价格信息（带缓存）。
     *
     * @param modelName 模型名称（如 "openai/gpt-5"、"google/gemini-2.5-pro"）
     * @return 价格信息 Map，包含 "input"、"cache"、"output" 等键，单位：元/百万 token
     *         如果无法确定则返回 null 或空 Map
     */
    default Map<String, Double> getModelPrice(String modelName) {
        return cacheService.getOrStore(
                "ModelPriceProvider:getModelPrice:" + modelName,
                Map.class,
                5 * 60,
                () -> _getModelPrice(modelName)
        );
    }

    /**
     * 获取指定模型的价格信息（内部实现，不带缓存）。
     *
     * @param modelName 模型名称
     * @return 价格信息 Map
     */
    Map<String, Double> _getModelPrice(String modelName);
}