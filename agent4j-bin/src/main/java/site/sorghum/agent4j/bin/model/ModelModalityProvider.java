package site.sorghum.agent4j.bin.model;

import org.noear.solon.data.cache.CacheService;
import org.noear.solon.data.cache.LocalCacheService;

/**
 * 模型多模态支持提供者接口 —— 用于从外部源（如 ModelMetaService）获取模型的多模态支持信息。
 * <p>
 * 实现此接口可以为不同的模型提供多模态支持信息，
 * 用于判断模型是否支持图像、音频、视频等输入输出。
 * </p>
 *
 * @author Sorghum
 */
public interface ModelModalityProvider {
    CacheService cacheService = new LocalCacheService();

    /**
     * 获取指定模型的多模态支持信息（带缓存）。
     *
     * @param modelName 模型名称（如 "openai/gpt-5"、"google/gemini-2.5-pro"）
     * @return 多模态支持信息，如果无法确定则返回 {@link ModalitySupport#TEXT_ONLY}
     */
    default ModalitySupport getModalitySupport(String modelName) {
        return cacheService.getOrStore(
                "ModelModalityProvider:getModalitySupport:" + modelName,
                ModalitySupport.class,
                5 * 60,
                () -> {
                    ModalitySupport result = _getModalitySupport(modelName);
                    return result != null ? result : ModalitySupport.TEXT_ONLY;
                }
        );
    }

    /**
     * 获取指定模型的多模态支持信息（内部实现，不带缓存）。
     *
     * @param modelName 模型名称
     * @return 多模态支持信息，如果无法确定则返回 null
     */
    ModalitySupport _getModalitySupport(String modelName);
}