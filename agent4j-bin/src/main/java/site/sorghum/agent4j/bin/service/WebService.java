package site.sorghum.agent4j.bin.service;

import org.noear.solon.annotation.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Web 服务 —— 网络搜索和网页抓取。
 * <p>
 * 从 Tools.java 中抽出。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class WebService {

    /**
     * 通过 DuckDuckGo Lite 搜索互联网。
     * 解析 HTML 响应提取搜索结果标题和链接。
     *
     * @param query 搜索关键词
     * @param topK  返回结果数量（默认 5，上限 10）
     * @return 格式化搜索结果列表
     */
    public String webSearch(String query, Integer topK) throws IOException {
        try {
            String encoded = URLEncoder.encode(query, "UTF-8");
            URL url = new URL("https://lite.duckduckgo.com/lite/?q=" + encoded);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            String resp = FileSystemService.readFully(conn.getInputStream());
            StringBuilder sb = new StringBuilder();
            Pattern p = Pattern.compile("<a[^>]+href=\"([^\"]+)\"[^>]*>([^<]+)</a>",
                    Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(resp);
            int count = 0;
            int max = Math.min(topK != null ? topK : 5, 10);
            while (m.find() && count < max) {
                String href = m.group(1);
                String text = m.group(2).replaceAll("<[^>]+>", "").trim();
                if (!text.isEmpty() && !href.startsWith("#")) {
                    sb.append(count + 1).append(". ").append(text).append("\n   ")
                            .append(href).append("\n");
                    count++;
                }
            }
            return sb.length() > 0 ? sb.toString().trim() : "(no results)";
        } catch (Exception e) {
            return "[ERROR] web_search failed: " + e.getMessage();
        }
    }

    /**
     * 抓取 URL 内容并提取纯文本。
     * 自动去除 HTML 标签、Script 和 Style 内容。
     *
     * @param urlStr 完整 URL（http:// 或 https://）
     * @return 纯文本内容（最多 100K 字符）
     */
    public String webFetch(String urlStr) throws IOException {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            String html = FileSystemService.readFully(conn.getInputStream());
            String text = html.replaceAll("<script[^>]*>[\\s\\S]*?</script>", " ")
                    .replaceAll("<style[^>]*>[\\s\\S]*?</style>", " ")
                    .replaceAll("<[^>]+>", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
            int max = Math.min(text.length(), 100000);
            return text.substring(0, max);
        } catch (Exception e) {
            return "[ERROR] web_fetch failed: " + e.getMessage();
        }
    }
}
