package site.sorghum.loopra.web.service;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Init;
import org.noear.solon.annotation.Inject;
import site.sorghum.loopra.bin.model.ContextSizeProvider;
import site.sorghum.loopra.bin.model.HttpModelClient;
import site.sorghum.loopra.bin.model.ModelContextUtils;
import site.sorghum.loopra.web.model.meta.Model;

/**
 * 基于 ModelMetaService 的上下文大小提供者。
 * <p>
 * 从模型元数据中获取模型的上下文窗口大小，
 * 并注册到 HttpModelClient 中作为上下文大小提供者。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
@Component
public class ModelMetaContextSizeProvider implements ContextSizeProvider {

    @Inject
    private ModelMetaService modelMetaService;

    /**
     * 初始化方法，在 Solon 容器启动后自动执行。
     * 将此提供者注册到 HttpModelClient 中。
     */
    @Init
    public void init() {
        HttpModelClient.setContextSizeProvider(this);
        log.info("[model-meta] 已注册 ModelMetaContextSizeProvider 到 HttpModelClient");
    }

    @Override
    public int _getContextSize(String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            return -1;
        }

        // 首先尝试直接匹配（如 "openai/gpt-5"）
        Model model = modelMetaService.findModelById(modelName);
        if (model != null && model.limit() != null) {
            long contextSize = model.limit().context();
            if (contextSize > 0) {
                log.debug("[model-meta] 从元数据获取模型 '{}' 的上下文大小: {}", modelName, contextSize);
                return (int) Math.min(contextSize, Integer.MAX_VALUE);
            }
        }

        // 如果直接匹配失败，尝试搜索（可能模型名称格式不完全匹配）
        if (model == null) {
            // 尝试去除可能的后缀（如 [512k]）再匹配
            String strippedName = stripContextSizeSuffix(modelName);
            if (!strippedName.equals(modelName)) {
                model = modelMetaService.findModelById(strippedName);
                if (model != null && model.limit() != null) {
                    long contextSize = model.limit().context();
                    if (contextSize > 0) {
                        log.debug("[model-meta] 从元数据获取模型 '{}'（原始: '{}'）的上下文大小: {}",
                                strippedName, modelName, contextSize);
                        return (int) Math.min(contextSize, Integer.MAX_VALUE);
                    }
                }
            }
        }

        log.debug("[model-meta] 未在元数据中找到模型 '{}' 的上下文大小信息", modelName);
        return -1;
    }

    /**
     * 剥离模型名称中的上下文大小后缀。
     * 例如："mimo-v2.5[512k]" → "mimo-v2.5"
     *
     * @param modelName 模型名称
     * @return 剥离后缀后的模型名称
     */
    private String stripContextSizeSuffix(String modelName) {
        return ModelContextUtils.stripContextSizeSuffix(modelName);
    }
}