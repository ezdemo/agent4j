package site.sorghum.cutin.core.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 模型 Provider 注册表。
 *
 * <p>同一个模型 id 可以注册多个 Provider，用于故障切换与路由。
 * 注册表按模型 id 建立索引，并提供按序解析 Provider 的能力。</p>
 */
public final class ModelRegistry {

    /** 模型 id 到候选 Provider 列表的映射。 */
    private final Map<String, List<ModelProvider>> providers = new ConcurrentHashMap<>();

    /** 注册一个 Provider，将其能力声明中的所有模型 id 都挂到索引下。 */
    public void register(ModelProvider provider) {
        for (String modelId : provider.capabilities().models()) {
            providers.computeIfAbsent(modelId, ignored -> new CopyOnWriteArrayList<>()).add(provider);
        }
    }

    /** 注销 Provider 的全部模型索引。 */
    public void unregister(ModelProvider provider) {
        providers.values().removeIf(candidates -> {
            candidates.removeIf(candidate -> candidate == provider);
            return candidates.isEmpty();
        });
    }

    /** 查找某个模型的首个候选 Provider。 */
    public Optional<ModelProvider> find(String modelId) {
        List<ModelProvider> candidates = providers.get(modelId);
        return candidates == null || candidates.isEmpty()
            ? Optional.empty()
            : Optional.of(candidates.get(0));
    }

    /** 解析某个模型的 Provider，找不到时抛出异常。 */
    public ModelProvider resolve(String modelId) {
        return find(modelId)
            .orElseThrow(() -> new IllegalArgumentException("no provider for model: " + modelId));
    }

    /** 返回某个模型的全部候选 Provider，用于按顺序尝试故障切换。 */
    public List<ModelProvider> providers(String modelId) {
        return List.copyOf(providers.getOrDefault(modelId, List.of()));
    }
}
