package site.sorghum.cutin.core.plugin;

/**
 * 插件包元信息：id、版本与下载地址，用于市场列表与安装。
 */
public record PluginPackageInfo(
    String id,
    String version,
    String downloadUrl
) {
}
