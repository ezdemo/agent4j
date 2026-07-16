package site.sorghum.loopra.bin.model;

import org.noear.solon.data.cache.CacheService;
import org.noear.solon.data.cache.LocalCacheService;

/**
 * 上下文大小提供者接口 —— 用于从外部源（如 ModelMetaService）获取模型的上下文大小。
 * <p>
 * 实现此接口可以为不同的模型提供上下文大小信息，
 * 用于覆盖默认的上下文大小推断逻辑。
 * </p>
 *
 * @author Sorghum
 */
public interface ContextSizeProvider {

    CacheService cacheService = new LocalCacheService();

    /**
     * 获取指定模型的上下文大小（token 数量）。
     *
     * @param modelName 模型名称（如 "openai/gpt-5"、"google/gemini-2.5-pro"）
     * @return 上下文大小（token 数量），如果无法确定则返回 -1
     */
    default int getContextSize(String modelName){
        return cacheService.getOrStore(
                "ContextSizeProvider:getContextSize:" + modelName, Integer.class,
                5 * 60,
                () -> _getContextSize(modelName)
        );
    }

    int _getContextSize(String modelName);
}