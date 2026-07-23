package site.sorghum.loopra.web.model;

/**
 * 当前会话信息。
 */
public record SessionCurrentDTO(
        String workspaceHash,
        String sessionName
) {
}
