package site.sorghum.agent4j.web.model;

/**
 * 系统健康检查。
 */
public record SystemHealthDTO(
        String status,
        String version,
        String buildTime
) {
}
