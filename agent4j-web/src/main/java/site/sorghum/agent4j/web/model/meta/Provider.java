package site.sorghum.agent4j.web.model.meta;

import java.util.List;
import java.util.Map;

/**
 * 模型提供商信息。
 * <p>
 * JSON 结构示例：
 * <pre>
 * {
 *   "id": "requesty",
 *   "env": ["REQUESTY_API_KEY"],
 *   "npm": "@ai-sdk/openai-compatible",
 *   "api": "https://router.requesty.ai/v1",
 *   "name": "Requesty",
 *   "doc": "https://requesty.ai/solution/llm-routing/models",
 *   "models": { ... }
 * }
 * </pre>
 * </p>
 *
 * @param id      提供商唯一标识符
 * @param env     需要的环境变量列表
 * @param npm     相关的 npm 包名
 * @param api     提供商的 API 基础端点
 * @param name    提供商的显示名称
 * @param doc     相关文档链接
 * @param models  该提供商下的模型映射，键为模型 ID（如 "xai/grok-4"）
 */
public record Provider(
        String id,
        List<String> env,
        String npm,
        String api,
        String name,
        String doc,
        Map<String, Model> models
) {
    /**
     * 获取该提供商下的所有模型列表。
     *
     * @return 模型列表
     */
    public List<Model> getModelList() {
        return models.values().stream().toList();
    }

    /**
     * 获取该提供商下的模型数量。
     *
     * @return 模型数量
     */
    public int getModelCount() {
        return models.size();
    }

    /**
     * 根据模型 ID 获取模型（仅在当前提供商范围内）。
     *
     * @param modelId 模型 ID（如 "xai/grok-4"）
     * @return 模型对象，如果不存在则返回 null
     */
    public Model getModel(String modelId) {
        return models.get(modelId);
    }
}