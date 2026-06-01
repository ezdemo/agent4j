package site.sorghum.agent4j.web.model;

/**
 * 会话缓存统计。
 */
public record SessionStatsDTO(
        int cacheSize,
        int maxCacheSize
) {
}
