package site.sorghum.loopra.web.model;

/**
 * 系统版本信息。
 */
public record SystemVersionDTO(
        String version,
        String buildTime,
        String name
) {
}
