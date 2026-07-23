package site.sorghum.loopra.web.service;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Init;
import site.sorghum.loopra.bin.config.ConfigService;
import site.sorghum.loopra.bin.config.LoopraConfig;
import site.sorghum.loopra.bin.model.ContextSizeProvider;
import site.sorghum.loopra.bin.model.HttpModelClient;

/**
 * 基于当前渠道模型配置的上下文大小提供者。
 * 配置保存后应立即生效，因此绕过 ContextSizeProvider 的五分钟默认缓存。
 */
@Slf4j
@Component
public class ModelMetaContextSizeProvider implements ContextSizeProvider {

    @Init
    public void init() {
        HttpModelClient.setContextSizeProvider(this);
        log.info("[model-config] 已注册渠道模型配置上下文提供者");
    }

    @Override
    public int getContextSize(String modelName) {
        return _getContextSize(modelName);
    }

    @Override
    public int _getContextSize(String modelName) {
        return _getContextSize(null, modelName);
    }

    @Override
    public int getContextSize(String channelId, String modelName) {
        return _getContextSize(channelId, modelName);
    }

    @Override
    public int _getContextSize(String channelId, String modelName) {
        LoopraConfig config = ConfigService.getConfig();
        LoopraConfig.ModelEntry entry = config == null ? null
                : config.modelEntry(channelId == null || channelId.isBlank() ? config.modelChannelId() : channelId, modelName);
        return entry == null ? -1 : entry.contextTokens();
    }
}
