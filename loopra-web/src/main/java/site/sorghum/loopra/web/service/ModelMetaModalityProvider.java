package site.sorghum.loopra.web.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Init;
import site.sorghum.loopra.bin.config.ConfigService;
import site.sorghum.loopra.bin.config.LoopraConfig;
import site.sorghum.loopra.bin.model.ModalitySupport;
import site.sorghum.loopra.bin.model.ModelModalityProvider;

/**
 * 基于当前渠道模型配置的多模态支持提供者。
 * 配置保存后 ConfigService 会替换配置实例；此提供者不使用五分钟缓存，确保能力立即生效。
 */
@Slf4j
@Component
public class ModelMetaModalityProvider implements ModelModalityProvider {

    @Getter
    private static volatile ModelModalityProvider instance;

    @Init
    public void init() {
        instance = this;
        log.info("[model-config] 已注册渠道模型配置多模态提供者");
    }

    @Override
    public ModalitySupport getModalitySupport(String modelName) {
        return _getModalitySupport(modelName);
    }

    @Override
    public ModalitySupport _getModalitySupport(String modelName) {
        LoopraConfig config = ConfigService.getConfig();
        LoopraConfig.ModelEntry entry = config == null ? null
                : config.modelEntry(config.modelChannelId(), modelName);
        if (entry != null && entry.imageInput()) {
            return new ModalitySupport(true, false, false, false, false, false, false, true, true);
        }
        // 未配置能力时明确按纯文本处理，不再回退到模型元数据。
        return ModalitySupport.TEXT_ONLY;
    }
}
