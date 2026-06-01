package site.sorghum.agent4j.web.model;

/**
 * 工具参数信息。
 */
public record ToolParamInfoDTO(
        String name,
        String type,
        String description,
        boolean required
) {
}
