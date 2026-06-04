package site.sorghum.agent4j.web.market.impl;

import org.noear.snack4.ONode;
import org.noear.solon.net.http.HttpResponse;
import org.noear.solon.net.http.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import site.sorghum.agent4j.web.market.Market;
import site.sorghum.agent4j.web.market.MarketDetail;
import site.sorghum.agent4j.web.market.MarketItem;

import java.io.BufferedInputStream;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ClawHub 市场适配器 — 对接 clawhub.ai API。
 *
 * @author Sorghum
 */
public class ClawhubMarket implements Market {

    private static final Logger LOG = LoggerFactory.getLogger(ClawhubMarket.class);

    private static final String BASE_URL = "https://clawhub.ai";
    private static final String USER_AGENT = "Agent4j/1.0";

    @Override
    public String name() {
        return "clawhub.ai";
    }

    @Override
    public String description() {
        return "ClawHub 国际技能市场";
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

        MarketDetail detail = new MarketDetail()
                .slug(getStringValue(skillNode, "slug"))
                .displayName(getStringValue(skillNode, "displayName"))
                .summary(getStringValue(skillNode, "summary"))
                .description(getStringValue(skillNode, "description"))
                .ownerHandle(getStringValue(skillNode, "ownerHandle"))
                .installSlug(getStringValue(skillNode, "slug"));

        ONode statsNode = skillNode.get("stats");
        if (statsNode != null && !statsNode.isNull()) {
            detail.installs(getLongValue(statsNode, "installsCurrent"));
            detail.stars(getLongValue(statsNode, "stars"));
        }

        return detail;
    }

    // ==================== 安装 ====================

    @Override
    public String install(String slug, Path skillsDir) throws Exception {
        if (slug == null || slug.isEmpty()) {
            throw new IllegalArgumentException("slug is required");
        }

        slug = slug.replaceAll("[^a-zA-Z0-9._-]", "");
        if (slug.isEmpty()) {
            throw new IllegalArgumentException("Invalid slug");
        }

        MarketDetail detailResult = detail(slug);
        String displayName = detailResult.getDisplayName();
        if (displayName == null || displayName.isEmpty()) {
            displayName = slug;
        }

        String downloadUrl = BASE_URL + "/api/v1/download?slug="
                + URLEncoder.encode(slug, "UTF-8");

        Files.createDirectories(skillsDir);

        Path tempZip = Files.createTempFile("skill-", ".zip");
        try {
            HttpResponse httpResp = HttpUtils.http(downloadUrl)
                    .header("User-Agent", USER_AGENT)
                    .timeout(30000)
                    .exec("GET");

            byte[] zipBytes = httpResp.bodyAsBytes();
            if (zipBytes == null || zipBytes.length == 0) {
                throw new RuntimeException("下载技能包失败: 返回内容为空");
            }
            Files.write(tempZip, zipBytes);

            if (Files.size(tempZip) == 0) {
                throw new RuntimeException("下载技能包失败: 文件为空");
            }

            Path targetDir = skillsDir.resolve(slug);
            if (Files.exists(targetDir)) {
                deleteDirectory(targetDir);
            }

            unzipToDirectory(tempZip, targetDir);

            LOG.info("ClawhubMarket.install: {} -> {}", slug, targetDir);
            return displayName;
        } finally {
            Files.deleteIfExists(tempZip);
        }
    }

    // ==================== 内部工具方法 ====================

    private String httpGet(String url) throws Exception {
        return HttpUtils.http(url)
                .header("User-Agent", USER_AGENT)
                .timeout(15000)
                .get();
    }

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
                    .slug(slug)
                    .name(slug)
                    .displayName(getStringValue(node, "displayName"))
                    .summary(getStringValue(node, "summary"))
                    .description(getStringValue(node, "description"))
                    .ownerHandle(getStringValue(node, "ownerHandle"))
                    .url(detailUrl);

            ONode statsNode = node.get("stats");
            if (statsNode != null && !statsNode.isNull()) {
                item.installs(getLongValue(statsNode, "installsCurrent"));
                item.stars(getLongValue(statsNode, "stars"));
            }

            result.add(item);
        }
        return result;
    }

    private String getStringValue(ONode node, String key) {
        ONode child = node.get(key);
        return (child != null && !child.isNull()) ? child.getString() : null;
    }

    private long getLongValue(ONode node, String key) {
        ONode child = node.get(key);
        return (child != null && !child.isNull()) ? child.getLong() : 0;
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

    private void unzipToDirectory(Path zipFile, Path targetDir) throws Exception {
        ZipInputStream zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipFile)));
        try {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = targetDir.resolve(entry.getName()).normalize();

                if (!entryPath.startsWith(targetDir.normalize())) {
                    continue;
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        } finally {
            zis.close();
        }
    }
}
