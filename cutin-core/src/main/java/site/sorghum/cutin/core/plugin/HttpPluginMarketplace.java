package site.sorghum.cutin.core.plugin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * 基于 HTTP JSON 的插件市场实现。
 *
 * <p>GET 请求配置的 endpoint，响应可以是
 * {@code {"packages": [...]}} 或直接是插件数组，返回统一转换为
 * {@link PluginPackageInfo} 列表。</p>
 */
public final class HttpPluginMarketplace implements PluginMarketplace {

    /** 市场 endpoint。 */
    private final URI endpoint;
    /** HTTP 客户端。 */
    private final HttpClient httpClient;
    /** JSON 映射器。 */
    private final ObjectMapper mapper = new ObjectMapper();

    /** 使用默认 10 秒连接超时的 HTTP 客户端创建市场。 */
    public HttpPluginMarketplace(URI endpoint) {
        this(endpoint, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    /** 使用自定义 HTTP 客户端创建市场。 */
    public HttpPluginMarketplace(URI endpoint, HttpClient httpClient) {
        this.endpoint = endpoint;
        this.httpClient = httpClient;
    }

    /** 请求市场并解析插件列表。 */
    @Override
    public List<PluginPackageInfo> list() {
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(response.body());
            JsonNode packages = root.has("packages") ? root.get("packages") : root;
            return mapper.convertValue(packages, new TypeReference<>() {
            });
        } catch (IOException | InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("failed to list plugins from marketplace", exception);
        }
    }
}
