package site.sorghum.cutin.integrations.model;

import site.sorghum.cutin.core.model.ModelProvider;
import site.sorghum.cutin.core.plugin.AgentPlugin;
import site.sorghum.cutin.core.plugin.LoopPlugin;
import site.sorghum.cutin.core.plugin.LoopRegistrar;

import java.util.List;

/**
 * 模型 Provider 插件：把一组外部模型 Provider 注册进循环引擎。
 *
 * <p>这是 cutin 预置的三个协议 Provider（OpenAI Chat Completions、
 * OpenAI Responses、Anthropic Messages）的统一接入插件。</p>
 */
@AgentPlugin(id = "model-providers")
public final class ModelProviderPlugin implements LoopPlugin {

    /** 要注册的 Provider 列表。 */
    private final List<ModelProvider> providers;

    /** 创建插件并固定 Provider 列表。 */
    public ModelProviderPlugin(List<ModelProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    /** {@inheritDoc} */
    @Override
    public String id() {
        return "model-providers";
    }

    /** 把全部 Provider 注册进模型注册表。 */
    @Override
    public void register(LoopRegistrar registrar) {
        providers.forEach(registrar::addModelProvider);
    }
}
