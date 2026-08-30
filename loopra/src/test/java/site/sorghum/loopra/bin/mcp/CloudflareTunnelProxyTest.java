package site.sorghum.loopra.bin.mcp;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CloudflareTunnelProxyTest {

    @Test
    void forwardsOnlyMcpEndpoint() throws Exception {
        AtomicInteger backendHits = new AtomicInteger();
        HttpServer backend = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        backend.createContext("/", exchange -> {
            backendHits.incrementAndGet();
            byte[] body = "ok".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        backend.start();

        try (CloudflareTunnelProxy proxy = CloudflareTunnelProxy.start(
                backend.getAddress().getPort(), "/mcp")) {
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> allowed = client.send(
                    HttpRequest.newBuilder(URI.create(proxy.originUrl() + "/mcp?probe=1"))
                            .POST(HttpRequest.BodyPublishers.ofString("hello"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(200, allowed.statusCode());
            assertEquals("ok", allowed.body());
            assertEquals(1, backendHits.get());

            HttpResponse<String> forbidden = client.send(
                    HttpRequest.newBuilder(URI.create(proxy.originUrl() + "/api/system"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(404, forbidden.statusCode());
            assertEquals(1, backendHits.get());
        } finally {
            backend.stop(0);
        }
    }

    @Test
    void buildsCloudflaredCommandWithoutShellSyntax() {
        assertEquals(
                List.of("cloudflared.exe", "tunnel", "--url", "http://127.0.0.1:4321"),
                CloudflareTunnelService.buildQuickTunnelCommand(
                        "cloudflared.exe", "http://127.0.0.1:4321"));
    }
}
