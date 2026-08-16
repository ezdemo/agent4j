package site.sorghum.cutin.integrations.model;

import java.util.Map;

/**
 * 模型 Provider 配置：id、baseUrl、apiKey、默认模型与扩展选项。
 *
 * <p>构造时会去掉 baseUrl 尾部的斜杠，并把选项拷贝为不可变 Map；
 * endpoint、maxTokens、reasoningEffort 均可由选项覆盖默认行为。</p>
 */
public record ModelProviderConfig(
    String id,
    String baseUrl,
    String apiKey,
    String model,
    Map<String, Object> options
) {

    /** 记录构造校验：规范化 baseUrl 并不可变拷贝选项。 */
    public ModelProviderConfig {
        baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        options = options == null ? Map.of() : Map.copyOf(options);
    }

    /** 生成接口地址：优先使用 options 中的 endpoint，否则拼接 baseUrl 与路径。 */
    public String endpoint(String path) {
        Object explicit = options.get("endpoint");
        if (explicit != null && !String.valueOf(explicit).isBlank()) {
            return String.valueOf(explicit);
        }
        return baseUrl + path;
    }

    /** 读取最大输出 token：options 未配置时使用 fallback。 */
    public int maxTokens(int fallback) {
        Object value = options.get("maxTokens");
        return value instanceof Number number ? number.intValue() : fallback;
    }

    /** 读取推理力度配置（reasoningEffort），未配置时返回 null。 */
    public String reasoningEffort() {
        Object value = options.get("reasoningEffort");
        return value == null ? null : String.valueOf(value);
    }
}
