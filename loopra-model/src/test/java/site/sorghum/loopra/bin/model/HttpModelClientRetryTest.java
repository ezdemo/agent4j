package site.sorghum.loopra.bin.model;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;
import site.sorghum.loopra.bin.agent.model.LoopraChatMessage;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpModelClientRetryTest {

    @Test
    void streamRetryIsReportedBeforeNextAttempt() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat", exchange -> respond(exchange, requests.incrementAndGet()));
        server.start();

        try {
            HttpModelClient client = new HttpModelClient(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/chat",
                    "test-key", "test-model", "high", new int[]{0});
            AtomicInteger retryCount = new AtomicInteger();
            AtomicReference<String> retryReason = new AtomicReference<>();
            AtomicReference<String> content = new AtomicReference<>("");

            client.chatStream(List.of(LoopraChatMessage.ofUser("hello")), new ONode().asArray(),
                    new ModelClient.StreamCallback() {
                        @Override
                        public void onRetry(String reason, int retryAttempt, int maxAttempts, int delaySeconds) {
                            retryCount.incrementAndGet();
                            retryReason.set(reason);
                            assertEquals(1, retryAttempt);
                            assertEquals(1, maxAttempts);
                            assertEquals(0, delaySeconds);
                        }

                        @Override
                        public void onContentDelta(String token) {
                            content.set(content.get() + token);
                        }
                    });

            assertEquals(2, requests.get());
            assertEquals(1, retryCount.get());
            assertEquals("HTTP 503", retryReason.get());
            assertEquals("ok", content.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void nonStreamingRetryExhaustionRemainsIOException() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();

        try {
            HttpModelClient client = new HttpModelClient(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/chat",
                    "test-key", "test-model", "high", new int[0]);

            assertThrows(IOException.class,
                    () -> client.chat(List.of(LoopraChatMessage.ofUser("hello")), new ONode().asArray()));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void streamDoesNotRetryAfterContentWasEmitted() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat", exchange -> respondWithPartialOutput(exchange, requests));
        server.start();

        try {
            HttpModelClient client = new HttpModelClient(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/chat",
                    "test-key", "test-model", "high", new int[]{0});
            AtomicReference<String> content = new AtomicReference<>("");
            AtomicReference<String> error = new AtomicReference<>();

            client.chatStream(List.of(LoopraChatMessage.ofUser("hello")), new ONode().asArray(),
                    new ModelClient.StreamCallback() {
                        @Override
                        public void onContentDelta(String token) {
                            content.updateAndGet(value -> value + token);
                        }

                        @Override
                        public void onError(String value) {
                            error.set(value);
                        }
                    });

            assertEquals(1, requests.get());
            assertEquals("partial", content.get());
            assertEquals("{\"error\":{\"message\":\"stream failed\"}}", error.get());
        } finally {
            server.stop(0);
        }
    }

    private static void respond(HttpExchange exchange, int requestNumber) throws IOException {
        if (requestNumber == 1) {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
            return;
        }

        byte[] body = ("data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}\n\n"
                + "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static void respondWithPartialOutput(HttpExchange exchange, AtomicInteger requests) throws IOException {
        requests.incrementAndGet();
        byte[] body = ("data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n"
                + "data: {\"error\":{\"message\":\"stream failed\"}}\n\n")
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
