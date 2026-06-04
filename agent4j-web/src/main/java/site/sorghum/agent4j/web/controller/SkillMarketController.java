package site.sorghum.agent4j.web.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.noear.solon.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import site.sorghum.agent4j.web.common.ServiceException;
import site.sorghum.agent4j.web.market.Market;
import site.sorghum.agent4j.web.market.MarketDetail;
import site.sorghum.agent4j.web.market.MarketItem;
import site.sorghum.agent4j.web.market.MarketManager;
import site.sorghum.agent4j.web.model.ApiResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;

/**
 * 技能市场 API 控制器 — 提供技能市场的浏览、搜索和安装能力。
 *
 * <p>所有外部 API 调用均由后端 Market 适配器完成，前端不直接访问外部服务。</p>
 *
 * @author Sorghum
 */
@Api(tags = "技能市场")
@Controller
@Mapping("/api/skill-market")
public class SkillMarketController {

    private static final Logger LOG = LoggerFactory.getLogger(SkillMarketController.class);

    private final MarketManager marketManager;

    public SkillMarketController() {
        this.marketManager = new MarketManager();
    }

    @ApiOperation(value = "获取可用市场列表", notes = "返回所有已注册的技能市场名称和描述")
    @Get
    @Mapping("/markets")
    public ApiResponse<List<MarketManager.MarketInfo>> markets() {
        return ApiResponse.ok(marketManager.getMarketInfos());
    }

    @ApiOperation(value = "浏览热门技能或搜索技能", notes = "通过 action 参数区分 trending（热门）和 search（搜索）模式")
    @Get
    @Mapping("/proxy")
    public ApiResponse<List<MarketItem>> proxy(
            @ApiParam(value = "操作类型：trending 获取热门 | search 搜索") @Param(value = "action", defaultValue = "trending") String action,
            @ApiParam(value = "搜索关键词（action=search 时使用）") @Param(value = "q", defaultValue = "") String query,
            @ApiParam(value = "返回数量限制") @Param(value = "limit", defaultValue = "50") int limit,
            @ApiParam(value = "市场名称（可选，默认使用 skillhub.cn）") @Param(value = "marketName", defaultValue = "") String marketName) {
        try {
            Market market = marketManager.getMarketByName(marketName);
            List<MarketItem> items;
            if ("search".equals(action) && query != null && !query.isEmpty()) {
                items = market.search(query, limit);
            } else {
                items = market.trending(limit);
            }
            return ApiResponse.ok(items);
        } catch (Exception e) {
            LOG.warn("SkillMarketProxy error: {}", e.getMessage());
            return ApiResponse.fail("操作失败: " + e.getMessage());
        }
    }

    @ApiOperation(value = "获取技能详情", notes = "根据 slug 获取技能的详细信息")
    @Get
    @Mapping("/detail")
    public ApiResponse<MarketDetail> detail(
            @ApiParam(value = "技能 slug", required = true) @Param(value = "slug", required = true) String slug,
            @ApiParam(value = "市场名称（可选）") @Param(value = "marketName", defaultValue = "") String marketName) {
        if (slug == null || slug.isEmpty()) {
            return ApiResponse.fail("slug is required");
        }
        try {
            Market market = marketManager.getMarketByName(marketName);
            MarketDetail detail = market.detail(slug);
            return ApiResponse.ok(detail);
        } catch (Exception e) {
            LOG.warn("SkillMarketDetail error: {}", e.getMessage());
            return ApiResponse.fail("获取详情失败: " + e.getMessage());
        }
    }

    @ApiOperation(value = "安装技能", notes = "从市场下载并安装技能到本地技能池")
    @Post
    @Mapping("/install")
    public ApiResponse<String> install(
            @ApiParam(value = "技能 slug", required = true) @Param(value = "slug", required = true) String slug,
            @ApiParam(value = "市场名称（可选）") @Param(value = "marketName", defaultValue = "") String marketName) {
        if (slug == null || slug.isEmpty()) {
            return ApiResponse.fail("slug is required");
        }

        try {
            Market market = marketManager.getMarketByName(marketName);

            // 确定安装目标目录：使用 ~/.claude/skills（与 PoolManager 注册的 @skill 路径一致）
            Path skillsDir = getSkillsInstallDir();

            String displayName = market.install(slug, skillsDir);

            LOG.info("技能安装成功: {} ({})", displayName, slug);
            return ApiResponse.ok(displayName);
        } catch (Exception e) {
            LOG.warn("SkillMarketInstall error: {}", e.getMessage(), e);
            return ApiResponse.fail("安装失败: " + e.getMessage());
        }
    }

    @ApiOperation(value = "卸载技能", notes = "从本地技能池删除已安装的技能")
    @Post
    @Mapping("/uninstall")
    public ApiResponse<Void> uninstall(
            @ApiParam(value = "技能 slug", required = true) @Param(value = "slug", required = true) String slug) {
        if (slug == null || slug.isEmpty()) {
            return ApiResponse.fail("slug is required");
        }

        slug = slug.replaceAll("[^a-zA-Z0-9._-]", "");
        if (slug.isEmpty()) {
            return ApiResponse.fail("Invalid slug");
        }

        try {
            Path skillsDir = getSkillsInstallDir();
            Path targetDir = skillsDir.resolve(slug);

            if (!Files.exists(targetDir)) {
                // 也尝试其它可能的技能目录
                Path altDir = Paths.get(System.getProperty("user.home"), ".agent4j", "skills", slug);
                if (Files.exists(altDir)) {
                    targetDir = altDir;
                } else {
                    return ApiResponse.fail("技能未找到: " + slug);
                }
            }

            deleteDirectory(targetDir);
            LOG.info("技能卸载成功: {}", slug);
            return ApiResponse.ok(null);
        } catch (Exception e) {
            LOG.warn("SkillMarketUninstall error: {}", e.getMessage(), e);
            return ApiResponse.fail("卸载失败: " + e.getMessage());
        }
    }

    private void deleteDirectory(Path dir) throws Exception {
        if (!Files.exists(dir)) return;
        Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (Exception ignored) {
                    }
                });
    }

    /**
     * 获取技能安装目录。
     * 优先使用 ~/.agent4j/skills，否则使用 ~/.claude/skills。
     */
    private Path getSkillsInstallDir() {
        // 尝试 ~/.agent4j/skills
        Path agent4jSkills = Paths.get(System.getProperty("user.home"), ".agent4j", "skills");
        if (Files.exists(agent4jSkills)) {
            return agent4jSkills;
        }

        // 尝试 ~/.claude/skills（与 PoolManager 默认注册的 @skill 路径一致）
        Path claudeSkills = Paths.get(System.getProperty("user.home"), ".claude", "skills");
        if (Files.exists(claudeSkills)) {
            return claudeSkills;
        }

        // 默认使用 ~/.claude/skills
        return claudeSkills;
    }
}
