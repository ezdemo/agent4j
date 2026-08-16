package site.sorghum.loopra.web.model;

/**
  * 项目注册表条目。
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
