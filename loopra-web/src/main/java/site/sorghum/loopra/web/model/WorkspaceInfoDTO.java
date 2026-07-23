package site.sorghum.loopra.web.model;

/**
 * 工作区信息。
 */
public record WorkspaceInfoDTO(
        String hash,
        String name,
        String path,
        long createdAt,
        long lastAccessedAt,
        int sessionCount
) {
}
