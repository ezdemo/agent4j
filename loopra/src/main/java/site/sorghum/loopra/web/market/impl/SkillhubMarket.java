package site.sorghum.loopra.web.market.impl;

import lombok.SneakyThrows;
import org.noear.snack4.ONode;
import site.sorghum.loopra.web.market.AbstractZipMarket;
import site.sorghum.loopra.web.market.MarketDetail;
import site.sorghum.loopra.web.market.MarketItem;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SkillHub 市场适配器 — 对接 skillhub.cn（技能市场）。
 *
 * <p>搜索、详情、下载全部使用 skillhub.cn 自有 API（api.skillhub.cn）。</p>
 *
 * @author Sorghum
 */
public class SkillhubMarket extends AbstractZipMarket {

    private static final String BASE_URL = "https://api.skillhub.cn";

    @Override
    public String name() {
        return "skillhub.cn";
    }

    @Override
    public String description() {
        return "专为中国用户优化的技能社区";
    }

    @SneakyThrows
    @Override
    protected String buildDownloadUrl(String slug){
        return BASE_URL + "/api/v1/download?slug=" + URLEncoder.encode(slug, "UTF-8");
    }

    // ==================== 列表与搜索 ====================

    @Override
    public List<MarketItem> trending(int limit){
        String url = BASE_URL + "/api/v1/search?q=&limit=" + limit;
        String body = httpGet(url);
        ONode root = ONode.ofJson(body);

        if (root.hasKey("error")) {
            throw new RuntimeException(root.get("message").getString());
        }
        return parseResults(root);
    }

    @SneakyThrows
    @Override
    public List<MarketItem> search(String query, int limit){
        if (query == null || query.isEmpty()) {
            return trending(limit);
        }
        String url = BASE_URL + "/api/v1/search?q=" + URLEncoder.encode(query, "UTF-8")
                + "&limit=" + limit;
        String body = httpGet(url);
        ONode root = ONode.ofJson(body);

        if (root.hasKey("error")) {
            throw new RuntimeException(root.get("message").getString());
        }
        return parseResults(root);
    }

    // ==================== 详情 ====================

    @SneakyThrows
    @Override
    public MarketDetail detail(String slug){
        if (slug == null || slug.isEmpty()) {
            throw new IllegalArgumentException("slug is required");
        }

        String url = BASE_URL + "/api/v1/skills/" + URLEncoder.encode(slug, "UTF-8");
        String body = httpGet(url);
        ONode root = ONode.ofJson(body);

        if (root.hasKey("error")) {
            ONode msgNode = root.get("message");
            String errorMsg = (msgNode != null && !msgNode.isNull()) ? msgNode.getString() : "技能不存在";
            throw new RuntimeException(errorMsg);
        }

        ONode skillNode = root.get("skill");
        if (skillNode == null) {
            throw new RuntimeException("技能不存在: " + slug);
        }

        String resolvedSlug = getStringValue(skillNode, "slug");
        String displayName = getStringValue(skillNode, "displayName");
        String summary = firstNonEmpty(
                getStringValue(skillNode, "summary_zh"),
                getStringValue(skillNode, "summary"));

        long installs = 0;
        long stars = 0;
        ONode statsNode = skillNode.get("stats");
        if (statsNode != null) {
            installs = getLongValue(statsNode, "installs");
            stars = getLongValue(statsNode, "stars");
        }

        String ownerHandle = null;
        ONode ownerNode = root.get("owner");
        if (ownerNode != null) {
            ownerHandle = getStringValue(ownerNode, "handle");
        }

        MarketDetail detail = new MarketDetail();
        detail.setSlug(resolvedSlug)
                .setDisplayName(displayName)
                .setSummary(summary)
                .setDescription(summary)
                .setOwnerHandle(ownerHandle)
                .setInstalls(installs)
                .setStars(stars)
                .setInstallSlug(resolvedSlug);

        return detail;
    }

    // ==================== 内部工具方法 ====================

    private List<MarketItem> parseResults(ONode root) {
        ONode resultsNode = root.get("results");
        if (resultsNode == null || !resultsNode.isArray()) {
            return Collections.emptyList();
        }

        List<MarketItem> result = new ArrayList<>();
        for (ONode node : resultsNode.getArray()) {
            MarketItem item = new MarketItem()
                    .setSlug(getStringValue(node, "slug"))
                    .setName(getStringValue(node, "slug"))
                    .setDisplayName(getStringValue(node, "displayName"))
                    .setSummary(getStringValue(node, "summary"))
                    .setDescription(firstNonEmpty(
                            getStringValue(node, "description_zh"),
                            getStringValue(node, "description")))
                    .setOwnerHandle(getStringValue(node, "owner_name"))
                    .setUrl(firstNonEmpty(
                            getStringValue(node, "url"),
                            "https://skillhub.cn/skills/" + getStringValue(node, "slug")))
                    .setInstalls(getLongValue(node, "installs"))
                    .setStars(getLongValue(node, "stars"));

            result.add(item);
        }
        return result;
    }
}
