package site.sorghum.cutin.core.model;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 模型 Provider 注册表。
 *
 * <p>同一个模型 id 可以注册多个 Provider，用于故障切换与路由。
 * 注册表维护 Provider 候选池（保持注册顺序，先注册者优先），每次查询时
 * 实时读取 {@link ModelProvider#capabilities()} 进行模型 id 匹配，因此
 * Provider 的能力变化（如运行时切换模型）无需重新注册即可生效。</p>
 */
public final class ModelRegistry {

    /** Provider 候选池，保持注册顺序。 */
    private final List<ModelProvider> providers = new CopyOnWriteArrayList<>();

    /** 注册一个 Provider；能力声明在查询时实时读取。 */
    public void register(ModelProvider provider) {
        providers.add(provider);
    }

    /** 注销 Provider（按实例身份移除全部出现）。 */
    public void unregister(ModelProvider provider) {
        providers.removeIf(candidate -> candidate == provider);
    }

    /** 查找某个模型的首个候选 Provider（按注册顺序）。 */
    public Optional<ModelProvider> find(String modelId) {
        return providers(modelId).stream().findFirst();
    }

    /** 解析某个模型的 Provider，找不到时抛出异常。 */
    public ModelProvider resolve(String modelId) {
        return find(modelId)
            .orElseThrow(() -> new IllegalArgumentException("no provider for model: " + modelId));
    }

    /** 返回某个模型的全部候选 Provider，用于按顺序尝试故障切换。 */
    public List<ModelProvider> providers(String modelId) {
        return providers.stream()
            .filter(provider -> provider.capabilities().models().contains(modelId))
            .toList();
    }
}
