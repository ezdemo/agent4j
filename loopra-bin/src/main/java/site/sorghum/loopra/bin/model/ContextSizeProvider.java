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
        return getContextSize(null, modelName);
    }

    /**
     * 获取指定渠道中模型的上下文窗口大小。
     */
    default int getContextSize(String channelId, String modelName){
        return cacheService.getOrStore(
                "ContextSizeProvider:getContextSize:" + channelId + ":" + modelName, Integer.class,
                5 * 60,
                () -> _getContextSize(channelId, modelName)
        );
    }

    int _getContextSize(String modelName);

    /**
     * 渠道感知的内部查询。旧实现可仅覆盖单参数版本。
     */
    default int _getContextSize(String channelId, String modelName) {
        return _getContextSize(modelName);
    }
}
