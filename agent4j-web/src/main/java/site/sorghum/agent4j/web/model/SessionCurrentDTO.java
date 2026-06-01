package site.sorghum.agent4j.web.model;

/**
 * 当前会话信息。
 */
public record SessionCurrentDTO(
        String workspaceHash,
        String sessionName
) {
}
