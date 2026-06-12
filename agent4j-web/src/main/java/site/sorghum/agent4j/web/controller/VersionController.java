package site.sorghum.agent4j.web.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.noear.snack4.ONode;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Get;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.net.http.HttpUtils;
import site.sorghum.agent4j.web.model.ApiResponse;
import site.sorghum.agent4j.web.model.SystemVersionDTO;
import site.sorghum.agent4j.web.model.VersionCheckDTO;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 版本管理端点 — 获取当前版本信息、检查远程最新版本。
 * <p>
 * 版本号通过 Maven 资源过滤从 {@code pom.xml} 自动注入到 {@code app.yml} 的 {@code solon.app.version} 中，
 * 运行时通过 Solon 配置读取，无需手动维护。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
@Api(tags = "版本")
@Controller
@Mapping("/api/version")
public class VersionController {

    /**
     * 远程版本检查地址（Gitee 最新 Release API），可通过配置覆盖。
     */
    @Inject("${version.check.url}")
    private String versionCheckUrl;

    /**
     * 获取当前系统版本信息。
     *
     * @return 当前版本号、构建时间、应用名称
     */
    @ApiOperation(value = "获取当前版本", notes = "返回当前 Agent4j 的版本号、构建时间和应用名称")
    @Get
    @Mapping("/")
    public ApiResponse<SystemVersionDTO> currentVersion() {
        String version = org.noear.solon.Solon.cfg().get("solon.app.version");
        String name = org.noear.solon.Solon.cfg().get("solon.app.name");
        if (version == null || version.isEmpty()) {
            version = "unknown";
        }
        return ApiResponse.ok(new SystemVersionDTO(version, "", name));
    }

    /**
     * 检查远程最新版本，与当前版本比较后返回结果。
     * <p>
     * 默认查询 Gitee 仓库的最新 Release，可通过配置 {@code version.check.url} 自定义检查地址。
     * </p>
     *
     * @return 版本检查结果（当前版本、最新版本、是否有更新、发布说明等）
     */
    @ApiOperation(value = "检查最新版本", notes = "查询远程仓库的最新 Release，与当前版本比较，返回是否有新版本及发布说明")
    @Get
    @Mapping("/check")
    public ApiResponse<VersionCheckDTO> checkLatestVersion() {
        String currentVersion = org.noear.solon.Solon.cfg().get("solon.app.version");
        if (currentVersion == null || currentVersion.isEmpty()) {
            currentVersion = "unknown";
        }

        String checkTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        try {
            String url = (versionCheckUrl != null && !versionCheckUrl.isEmpty())
                    ? versionCheckUrl
                    : "https://gitee.com/api/v5/repos/ezdemo/agent4j/releases/latest";

            String responseBody = HttpUtils.http(url)
                    .header("User-Agent", "Agent4j/" + currentVersion)
                    .timeout(10000)
                    .get();

            ONode json = ONode.ofJson(responseBody);

            String latestTag = json.get("tag_name").getString();
            if (latestTag == null) {
                latestTag = json.get("name").getString();
            }
            // 去除 tag 中的 "v" 前缀以便比较
            String latestVersion = latestTag != null ? latestTag.replaceFirst("^[vV]", "") : null;

            String releaseUrl = json.get("html_url").getString();
            String releaseNotes = json.get("body").getString();
            if (releaseNotes != null && releaseNotes.length() > 500) {
                releaseNotes = releaseNotes.substring(0, 500) + "...";
            }

            boolean hasNewVersion = false;
            if (latestVersion != null && !currentVersion.equals("unknown")) {
                hasNewVersion = compareVersions(latestVersion, currentVersion) > 0;
            }

            return ApiResponse.ok(new VersionCheckDTO(
                    currentVersion,
                    latestVersion != null ? latestVersion : "unknown",
                    hasNewVersion,
                    releaseUrl,
                    releaseNotes,
                    checkTime
            ));
        } catch (Exception e) {
            log.warn("检查最新版本失败: {}", e.getMessage());
            // 检查失败时返回当前版本信息，标记无新版本
            return ApiResponse.ok(new VersionCheckDTO(
                    currentVersion,
                    currentVersion,
                    false,
                    null,
                    "版本检查失败: " + e.getMessage(),
                    checkTime
            ));
        }
    }

    /**
     * 比较两个语义化版本号（x.y.z 格式）。
     *
     * @param v1 版本 A
     * @param v2 版本 B
     * @return v1 &gt; v2 返回正数，v1 &lt; v2 返回负数，相等返回 0
     */
    private int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        int len = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < len; i++) {
            int num1 = i < parts1.length ? parseIntSafe(parts1[i]) : 0;
            int num2 = i < parts2.length ? parseIntSafe(parts2[i]) : 0;
            if (num1 != num2) {
                return num1 - num2;
            }
        }
        return 0;
    }

    /**
     * 安全地将字符串解析为整数，解析失败时返回 0。
     */
    private int parseIntSafe(String str) {
        // 只取数字部分（忽略 pre-release 标签如 "12-beta" -> 12）
        StringBuilder digits = new StringBuilder();
        for (char c : str.toCharArray()) {
            if (Character.isDigit(c)) {
                digits.append(c);
            } else {
                break;
            }
        }
        if (digits.isEmpty()) return 0;
        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
