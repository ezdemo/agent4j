package site.sorghum.loopra.web.model;

/**
 * 会话固定的模型、模型渠道和思考强度。
 */
public record SessionSettingsDTO(
        String model,
        String modelChannelId,
        String reasoningEffort) {
}
