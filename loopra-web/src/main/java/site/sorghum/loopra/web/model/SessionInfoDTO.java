package site.sorghum.loopra.web.model;

/**
 * 会话信息。
 */
public record SessionInfoDTO(
        String name,
        String title,
        long messageCount,
        boolean current,
        long mtime
) {
}
