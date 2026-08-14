package site.sorghum.loopra.web.model;

/**
 * Project registry entry.
 */
public record ProjectInfoDTO(
        String hash,
        String name,
        String path,
        long createdAt,
        long lastAccessedAt,
        int sessionCount
) {
}
