package site.sorghum.loopra.bin.mcp;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 只把指定 MCP 路径转发到 Loopra Web 服务的本地代理。
 *
 * <p>Quick Tunnel 的 {@code --url} 只接受一个 origin，直接指向 Loopra Web 端口会把
 * 其它 Web/API 路由一并暴露。因此这里使用 JDK 自带的 HttpServer 在回环地址开启一个
 * 临时端口，仅转发 MCP endpoint。</p>
 */
final class CloudflareTunnelProxy implements AutoCloseable {

    private static final int MAX_REQUEST_BODY_BYTES = 16 * 1024 * 1024;
    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade", "host", "content-length",
            "expect");

    private final HttpServer server;
    private final ExecutorService executor;
    private final HttpClient httpClient;
    private final String targetBaseUrl;
    private final String endpoint;
    private final String originUrl;

    private CloudflareTunnelProxy(HttpServer server, ExecutorService executor,
                                  String targetBaseUrl, String endpoint, String originUrl) {
        this.server = server;
        this.executor = executor;
        this.targetBaseUrl = targetBaseUrl;
        this.endpoint = endpoint;
        this.originUrl = originUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    static CloudflareTunnelProxy start(int targetPort, String endpoint) throws IOException {
        if (targetPort <= 0 || targetPort > 65535) {
            throw new IllegalArgumentException("Loopra Web 服务端口无效: " + targetPort);
        }
        if (endpoint == null || endpoint.isBlank() || "/".equals(endpoint)
                || !endpoint.startsWith("/")) {
            throw new IllegalArgumentException("Cloudflare 隧道要求有效的 MCP endpoint 路径");
        }

        String targetBaseUrl = "http://127.0.0.1:" + targetPort;
        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        ExecutorService executor = Executors.newCachedThreadPool(new DaemonThreadFactory());
        String originUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        CloudflareTunnelProxy proxy = new CloudflareTunnelProxy(
                server, executor, targetBaseUrl, endpoint, originUrl);
        server.createContext("/", proxy.new ProxyHandler());
        server.setExecutor(executor);
        try {
            server.start();
            return proxy;
        } catch (RuntimeException e) {
            executor.shutdownNow();
            server.stop(0);
            throw e;
        }
    }

    String originUrl() {
        return originUrl;
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private final class ProxyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try {
                URI requestUri = exchange.getRequestURI();
                String requestPath = requestUri.getPath();
                if (!isAllowedPath(requestPath)) {
                    sendText(exchange, 404, "Not Found");
                    return;
                }

                byte[] requestBody = readRequestBody(exchange.getRequestBody());
                URI targetUri = buildTargetUri(requestUri);
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(targetUri)
                        .timeout(Duration.ofMinutes(30));
                copyRequestHeaders(exchange.getRequestHeaders(), requestBuilder);

                HttpRequest.BodyPublisher body = requestBody.length == 0
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofByteArray(requestBody);
                requestBuilder.method(exchange.getRequestMethod(), body);

                HttpResponse<InputStream> response = httpClient.send(
                        requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
                copyResponseHeaders(response.headers(), exchange.getResponseHeaders());

                int status = response.statusCode();
                boolean noBody = exchange.getRequestMethod().equalsIgnoreCase("HEAD")
                        || status == 204 || status == 304 || (status >= 100 && status < 200);
                exchange.sendResponseHeaders(status, noBody ? -1 : 0);
                if (!noBody) {
                    try (InputStream input = response.body();
                         OutputStream output = exchange.getResponseBody()) {
                        input.transferTo(output);
                    }
                } else {
                    response.body().close();
                }
            } catch (PayloadTooLargeException e) {
                sendText(exchange, 413, e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                sendText(exchange, 504, "MCP 请求被中断");
            } catch (Exception e) {
                sendText(exchange, 502, "Loopra MCP 服务暂时不可用");
            } finally {
                exchange.close();
            }
        }

        private boolean isAllowedPath(String requestPath) {
            return requestPath != null
                    && (requestPath.equals(endpoint) || requestPath.startsWith(endpoint + "/"));
        }

        private URI buildTargetUri(URI requestUri) {
            String rawPath = requestUri.getRawPath();
            String rawQuery = requestUri.getRawQuery();
            String target = targetBaseUrl + rawPath
                    + (rawQuery == null || rawQuery.isEmpty() ? "" : "?" + rawQuery);
            return URI.create(target);
        }

        private void copyRequestHeaders(Headers source, HttpRequest.Builder target) {
            for (Map.Entry<String, List<String>> entry : source.entrySet()) {
                if (isHopByHop(entry.getKey())) continue;
                for (String value : entry.getValue()) {
                    target.header(entry.getKey(), value);
                }
            }
        }

        private void copyResponseHeaders(HttpHeaders source, Headers target) {
            for (Map.Entry<String, List<String>> entry : source.map().entrySet()) {
                if (isHopByHop(entry.getKey())) continue;
                for (String value : entry.getValue()) {
                    target.add(entry.getKey(), value);
                }
            }
        }

        private boolean isHopByHop(String name) {
            return name != null && HOP_BY_HOP_HEADERS.contains(name.toLowerCase(Locale.ROOT));
        }

        private byte[] readRequestBody(InputStream input) throws IOException {
            try (InputStream body = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int total = 0;
                int read;
                while ((read = body.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_REQUEST_BODY_BYTES) {
                        throw new PayloadTooLargeException("MCP 请求体过大");
                    }
                    output.write(buffer, 0, read);
                }
                return output.toByteArray();
            }
        }

        private void sendText(HttpExchange exchange, int status, String message) {
            try {
                byte[] body = message.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                exchange.sendResponseHeaders(status, body.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(body);
                }
            } catch (IOException ignored) {
                // 请求已经断开或响应头已发送，不能再写错误响应。
            }
        }
    }

    private static final class PayloadTooLargeException extends IOException {
        private PayloadTooLargeException(String message) {
            super(message);
        }
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "loopra-mcp-tunnel-proxy-"
                    + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
