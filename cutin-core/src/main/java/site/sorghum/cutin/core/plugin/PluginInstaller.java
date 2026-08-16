package site.sorghum.cutin.core.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * 插件安装器：下载插件 JAR 到本地目录。
 *
 * <p>文件名使用安全的插件 id 与版本拼接，避免路径穿越与特殊字符。</p>
 */
public final class PluginInstaller {

    /** 插件安装目录。 */
    private final Path pluginDirectory;
    /** HTTP 客户端。 */
    private final HttpClient httpClient;

    /** 使用默认 10 秒连接超时的 HTTP 客户端创建安装器。 */
    public PluginInstaller(Path pluginDirectory) {
        this(pluginDirectory, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    /** 使用自定义 HTTP 客户端创建安装器。 */
    public PluginInstaller(Path pluginDirectory, HttpClient httpClient) {
        this.pluginDirectory = pluginDirectory;
        this.httpClient = httpClient;
    }

    /** 下载并保存插件 JAR，返回本地文件路径。 */
    public Path install(PluginPackageInfo info) throws IOException {
        Files.createDirectories(pluginDirectory);
        HttpRequest request = HttpRequest.newBuilder(URI.create(info.downloadUrl()))
            .timeout(Duration.ofSeconds(60))
            .GET()
            .build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            String safeId = info.id().replaceAll("[^a-zA-Z0-9._-]", "_");
            Path target = pluginDirectory.resolve(safeId + "-" + info.version() + ".jar");
            try (InputStream input = response.body(); OutputStream output = Files.newOutputStream(target)) {
                input.transferTo(output);
            }
            return target;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("plugin download interrupted", exception);
        }
    }
}
