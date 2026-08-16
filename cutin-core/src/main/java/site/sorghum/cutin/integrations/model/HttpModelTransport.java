package site.sorghum.cutin.integrations.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 模型 HTTP 传输层：统一处理 JSON POST 与 SSE 流式 POST。
 *
 * <p>三个协议 Provider 共用该传输：非 2xx 响应会抛出
 * {@link ModelProviderException}；SSE 流会过滤出 data 行并把
 * {@code [DONE]} 视为流结束。</p>
 */
public final class HttpModelTransport {

    /** 请求 endpoint。 */
    private final URI endpoint;
    /** JSON 映射器。 */
    private final ObjectMapper mapper;
    /** HTTP 客户端。 */
    private final HttpClient httpClient;
    /** 固定请求头（例如 Authorization、x-api-key）。 */
    private final Map<String, String> headers;

    /** 使用默认 15 秒连接超时的 HTTP 客户端创建传输层。 */
    public HttpModelTransport(
        String endpoint,
        ObjectMapper mapper,
        Map<String, String> headers
    ) {
        this(endpoint, mapper, headers, HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build());
    }

    /** 使用自定义 HTTP 客户端创建传输层。 */
    public HttpModelTransport(
        String endpoint,
        ObjectMapper mapper,
        Map<String, String> headers,
        HttpClient httpClient
    ) {
        this.endpoint = URI.create(endpoint);
        this.mapper = mapper;
        this.headers = Map.copyOf(headers);
        this.httpClient = httpClient;
    }

    /** 发送 JSON POST 并解析响应体为 JsonNode。 */
    public JsonNode post(JsonNode body) {
        try {
            HttpRequest request = buildRequest(body.toString());
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ModelProviderException("provider returned HTTP " + response.statusCode()
                    + ": " + response.body());
            }
            return mapper.readTree(response.body());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ModelProviderException("provider request interrupted");
        }
    }

    /** 发送 SSE 流式 POST，返回解析后的 JsonNode 行流。 */
    public Stream<JsonNode> postSse(JsonNode body) {
        try {
            HttpRequest request = buildRequest(body.toString());
            HttpResponse<Stream<String>> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofLines()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String error = response.body().limit(500).collect(Collectors.joining("\n"));
                response.body().close();
                throw new ModelProviderException("provider stream returned HTTP "
                    + response.statusCode() + ": " + error);
            }
            return response.body()
                .map(String::trim)
                .filter(line -> line.startsWith("data:"))
                .map(line -> line.substring(5).trim())
                .filter(line -> !line.isEmpty() && !line.equals("[DONE]"))
                .map(json -> {
                    try {
                        return mapper.readTree(json);
                    } catch (IOException exception) {
                        throw new UncheckedIOException(exception);
                    }
                })
                .onClose(response.body()::close);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ModelProviderException("provider stream interrupted");
        }
    }

    /** 构造 POST 请求：设置 JSON 头、Accept 头、固定请求头与 120 秒超时。 */
    private HttpRequest buildRequest(String json) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(120))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(json));
        headers.forEach(builder::header);
        return builder.build();
    }
}
