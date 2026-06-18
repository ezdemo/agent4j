package site.sorghum.agent4j.web.model.meta;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 模型元数据顶层容器，对应 models.dev/api.json 的完整结构。
 * <p>
 * JSON 结构示例：
 * <pre>
 * {
 *   "requesty": { ... },
 *   "google": { ... },
 *   "openai": { ... }
 * }
 * </pre>
 * </p>
 *
 * @param providers 提供商映射，键为提供商 ID（如 "requesty"、"google"、"openai"）
 */
public record ModelMeta(
        Map<String, Provider> providers
) {
    /**
     * 获取所有提供商的列表。
     *
     * @return 提供商列表
     */
    public List<Provider> getAllProviders() {
        return providers.values().stream().toList();
    }

    /**
     * 获取所有模型的扁平列表（跨所有提供商）。
     *
     * @return 所有模型的列表
     */
    public List<Model> getAllModels() {
        return providers.values().stream()
                .flatMap(provider -> provider.models().values().stream())
                .toList();
    }

    /**
     * 根据模型 ID 查找模型（跨所有提供商）。
     *
     * @param modelId 模型 ID（如 "openai/gpt-5"、"google/gemini-2.5-pro"）
     * @return 找到的模型，如果不存在则返回 null
     */
    public Model findModelById(String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            return null;
        }
        // 模型 ID 通常格式为 "provider/modelName"
        String[] parts = modelId.split("/", 2);
        if (parts.length != 2) {
            // 遍历所有的
            for (Provider provider : providers.values()) {
                Model model = provider.models().get(modelId);
                if (model != null) {
                    return model;
                }
            }
            return null;
        }
        String providerId = parts[0];
        Provider provider = providers.get(providerId);
        if (provider == null) {
            return null;
        }
        return provider.models().get(modelId);
    }

    /**
     * 获取指定提供商下的所有模型。
     *
     * @param providerId 提供商 ID
     * @return 模型列表，如果提供商不存在则返回空列表
     */
    public List<Model> getModelsByProvider(String providerId) {
        Provider provider = providers.get(providerId);
        if (provider == null) {
            return List.of();
        }
        return provider.models().values().stream().toList();
    }

    /**
     * 搜索模型名称或 ID 中包含指定关键词的模型（不区分大小写）。
     *
     * @param keyword 关键词
     * @return 匹配的模型列表
     */
    public List<Model> searchModels(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return List.of();
        }
        String lowerKeyword = keyword.toLowerCase();
        return getAllModels().stream()
                .filter(model -> 
                        model.id().toLowerCase().contains(lowerKeyword) ||
                        model.name().toLowerCase().contains(lowerKeyword))
                .toList();
    }
}