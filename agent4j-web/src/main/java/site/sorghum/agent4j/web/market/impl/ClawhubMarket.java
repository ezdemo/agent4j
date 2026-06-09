package site.sorghum.agent4j.web.market.impl;

import org.noear.snack4.ONode;
import site.sorghum.agent4j.web.market.AbstractZipMarket;
import site.sorghum.agent4j.web.market.MarketDetail;
import site.sorghum.agent4j.web.market.MarketItem;

import java.net.URLEncoder;
import java.util.*;

/**
 * ClawHub 市场适配器 — 对接 clawhub.ai API。
 *
 * @author Sorghum
 */
public class ClawhubMarket extends AbstractZipMarket {

    private static final String BASE_URL = "https://clawhub.ai";

    @Override
    public String name() {
        return "clawhub.ai";
    }

    @Override
    public String description() {
        return "ClawHub 国际技能市场";
    }

    @Override
    protected String buildDownloadUrl(String slug) throws Exception {
        return BASE_URL + "/api/v1/download?slug=" + URLEncoder.encode(slug, "UTF-8");
    }

    // ==================== 列表与搜索 ====================

    @Override
    public List<MarketItem> trending(int limit) throws Exception {
        String url = BASE_URL + "/api/v1/skills?limit=" + limit + "&sort=trending";
        String body = httpGet(url);
        ONode root = ONode.ofJson(body);

        if (root.hasKey("error")) {
            throw new RuntimeException(root.get("message").getString());
        }

        return parseItems(root);
    }

    @Override
    public List<MarketItem> search(String query, int limit) throws Exception {
        if (query == null || query.isEmpty()) {
            return trending(limit);
        }

        String url = BASE_URL + "/api/v1/search?q=" + URLEncoder.encode(query, "UTF-8");
        String body = httpGet(url);
        ONode root = ONode.ofJson(body);

        if (root.hasKey("error")) {
            throw new RuntimeException(root.get("message").getString());
        }

        ONode resultsNode = root.get("results");
        if (resultsNode != null && resultsNode.isArray()) {
            return parseNodeArray(resultsNode);
        } else {
            return parseItems(root);
        }
    }

    // ==================== 详情 ====================

    @Override
    public MarketDetail detail(String slug) throws Exception {
        if (slug == null || slug.isEmpty()) {
            throw new IllegalArgumentException("slug is required");
        }

        String url = BASE_URL + "/api/v1/skills/" + URLEncoder.encode(slug, "UTF-8");
        String body = httpGet(url);
        ONode root = ONode.ofJson(body);

        if (root.hasKey("error")) {
            throw new RuntimeException(root.get("message").getString());
        }

        ONode skillNode = root.get("skill");
        if (skillNode == null || skillNode.isNull()) {
            throw new RuntimeException("技能不存在: " + slug);
        }

        MarketDetail detail = new MarketDetail();
        detail.setSlug(getStringValue(skillNode, "slug"))
                .setDisplayName(getStringValue(skillNode, "displayName"))
                .setSummary(getStringValue(skillNode, "summary"))
                .setDescription(getStringValue(skillNode, "description"))
                .setOwnerHandle(getStringValue(skillNode, "ownerHandle"))
                .setInstallSlug(getStringValue(skillNode, "slug"));

        ONode statsNode = skillNode.get("stats");
        if (statsNode != null && !statsNode.isNull()) {
            detail.setInstalls(getLongValue(statsNode, "installsCurrent"));
            detail.setStars(getLongValue(statsNode, "stars"));
        }

        return detail;
    }

    // ==================== 内部工具方法 ====================

    private List<MarketItem> parseItems(ONode root) {
        ONode itemsNode = root.get("items");
        if (itemsNode != null && itemsNode.isArray()) {
            return parseNodeArray(itemsNode);
        }
        return Collections.emptyList();
    }

    private List<MarketItem> parseNodeArray(ONode arrayNode) {
        List<MarketItem> result = new ArrayList<>();
        for (ONode node : arrayNode.getArray()) {
            String slug = getStringValue(node, "slug");
            String apiUrl = getStringValue(node, "url");
            String detailUrl = (apiUrl != null) ? apiUrl : BASE_URL + "/skills/" + slug;

            MarketItem item = new MarketItem()
                    .setSlug(slug)
                    .setName(slug)
                    .setDisplayName(getStringValue(node, "displayName"))
                    .setSummary(getStringValue(node, "summary"))
                    .setDescription(getStringValue(node, "description"))
                    .setOwnerHandle(getStringValue(node, "ownerHandle"))
                    .setUrl(detailUrl);

            ONode statsNode = node.get("stats");
            if (statsNode != null && !statsNode.isNull()) {
                item.setInstalls(getLongValue(statsNode, "installsCurrent"));
                item.setStars(getLongValue(statsNode, "stars"));
            }

            result.add(item);
        }
        return result;
    }
}
