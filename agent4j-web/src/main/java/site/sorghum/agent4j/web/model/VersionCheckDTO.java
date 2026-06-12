package site.sorghum.agent4j.web.model;

/**
 * 版本检查结果 — 用于前端检测是否有新版本可用。
 *
 * @param currentVersion  当前运行版本
 * @param latestVersion   远程最新版本
 * @param hasNewVersion   是否有新版本
 * @param releaseUrl      最新版本的发布页 URL
 * @param releaseNotes    最新版本的发布说明（摘要）
 * @param checkTime       检查时间
 * @author Sorghum
 */
public record VersionCheckDTO(
        String currentVersion,
        String latestVersion,
        boolean hasNewVersion,
        String releaseUrl,
        String releaseNotes,
        String checkTime
) {
}
