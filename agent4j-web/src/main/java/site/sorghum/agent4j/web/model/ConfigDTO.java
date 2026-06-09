package site.sorghum.agent4j.web.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 配置信息（apiKey 脱敏）。
 */
public record ConfigDTO(
        String baseUrl,
        String model,
        List<String> availableModels,
        String workspace,
        String editMode,
        String reasoningEffort,
        String lang,
        boolean hitl,
        Set<String> disabledTools,
        List<String> blockedPaths,
        String apiKey,
        Map<String, Map<String, Double>> price
) {
}
