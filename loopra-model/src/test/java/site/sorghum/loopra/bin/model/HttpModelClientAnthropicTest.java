package site.sorghum.loopra.bin.model;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;
import site.sorghum.loopra.bin.agent.model.ChatMessage;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class HttpModelClientAnthropicTest {

    @Test
    void streamsAnthropicTextToolCallsAndUsage() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<Headers> requestHeaders = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/messages", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            requestHeaders.set(exchange.getRequestHeaders());
            respondSse(exchange, """
                    data: {"type":"message_start","message":{"id":"msg_1","usage":{"input_tokens":12,"cache_read_input_tokens":5,"cache_creation_input_tokens":7}}}

                    data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

                    data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"ok"}}

                    data: {"type":"content_block_stop","index":0}

                    data: {"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"toolu_2","name":"read","input":{}}}

                    data: {"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\\"path\\":\\"new.txt\\"}"}}

                    data: {"type":"content_block_stop","index":1}

                    data: {"type":"message_delta","delta":{"stop_reason":"tool_use"},"usage":{"output_tokens":4}}

                    data: {"type":"message_stop"}

                    """);
        });
        server.start();

        try {
            HttpModelClient client = anthropicClient(server);
            ONode tools = ONode.ofJson("""
                    [{"type":"function","function":{"name":"read","parameters":{"type":"object"}}}]
                    """);
            List<ChatMessage> messages = List.of(
                    ChatMessage.ofSystem("system prompt"),
                    ChatMessage.ofUser("hello"));
            AtomicReference<String> content = new AtomicReference<>("");
            AtomicReference<ONode> toolCalls = new AtomicReference<>();
            AtomicReference<int[]> usage = new AtomicReference<>();
            AtomicInteger done = new AtomicInteger();

            client.chatStream(messages, tools, new ModelClient.StreamCallback() {
                @Override
                public void onContentDelta(String token) {
                    content.updateAndGet(value -> value + token);
                }

                @Override
                public void onToolCalls(ONode calls) {
                    toolCalls.set(calls);
                }

                @Override
                public void onUsage(int promptTokens, int completionTokens, int totalTokens,
                                    int cacheHitTokens, int cacheMissTokens) {
                    usage.set(new int[]{promptTokens, completionTokens, totalTokens, cacheHitTokens, cacheMissTokens});
                }

                @Override
                public void onDone() {
                    done.incrementAndGet();
                }
            });

            ONode request = ONode.ofJson(requestBody.get());
            assertEquals("test-model", request.get("model").getString());
            assertEquals(8192, request.get("max_tokens").getInt());
            assertTrue(request.get("stream").getBoolean());
            // 即使 reasoningEffort=high 也不发送 thinking（Claude 4.7+ 拒绝该字段）
            assertTrue(request.get("thinking").isNull());
            assertEquals("system prompt", request.get("system").getString());
            assertEquals("hello", request.select("$.messages[0].content").getString());
            assertEquals("read", request.select("$.tools[0].name").getString());
            assertEquals("object", request.select("$.tools[0].input_schema.type").getString());
            assertEquals("secret-key", requestHeaders.get().getFirst("x-api-key"));
            assertEquals("secret-key", requestHeaders.get().getFirst("api-key"));
            assertEquals("2023-06-01", requestHeaders.get().getFirst("anthropic-version"));
            assertNull(requestHeaders.get().getFirst("Authorization"));

            assertEquals("ok", content.get());
            assertEquals("toolu_2", toolCalls.get().select("$[0].id").getString());
            assertEquals("read", toolCalls.get().select("$[0].function.name").getString());
            assertEquals("{\"path\":\"new.txt\"}", toolCalls.get().select("$[0].function.arguments").getString());
            assertEquals(List.of(12, 4, 16, 5, 7), toList(usage.get()));
            assertEquals(1, done.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void convertsNonStreamingAnthropicResponseToChatMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/messages", HttpModelClientAnthropicTest::respondNonStreaming);
        server.start();

        try {
            ONode message = anthropicClient(server).chat(List.of(ChatMessage.ofUser("hello")), new ONode().asArray());

            assertEquals("assistant", message.get("role").getString());
            assertEquals("done", message.get("content").getString());
            assertEquals("toolu_3", message.select("$.tool_calls[0].id").getString());
            assertEquals("write", message.select("$.tool_calls[0].function.name").getString());
            assertEquals("{\"path\":\"a.txt\"}", message.select("$.tool_calls[0].function.arguments").getString());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reportsTerminalStreamErrorWithoutRetry() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/messages", exchange -> {
            requests.incrementAndGet();
            respondSse(exchange, "data: {\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"bad request\"}}\n\n");
        });
        server.start();

        try {
            AtomicReference<String> error = new AtomicReference<>();
            anthropicClient(server).chatStream(List.of(ChatMessage.ofUser("hello")), new ONode().asArray(),
                    new ModelClient.StreamCallback() {
                        @Override
                        public void onError(String value) {
                            error.set(value);
                        }
                    });

            assertEquals(1, requests.get());
            assertTrue(error.get().contains("bad request"));
        } finally {
            server.stop(0);
        }
    }

    private static HttpModelClient anthropicClient(HttpServer server) {
        return new HttpModelClient(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/messages",
                "secret-key", "test-model", "high", "test-channel", "anthropic");
    }

    private static void respondSse(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void respondNonStreaming(HttpExchange exchange) throws IOException {
        String body = """
                {"id":"msg_2","type":"message","role":"assistant","content":[
                  {"type":"text","text":"done"},
                  {"type":"tool_use","id":"toolu_3","name":"write","input":{"path":"a.txt"}}
                ],"usage":{"input_tokens":10,"output_tokens":3}}
                """;
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static List<Integer> toList(int[] values) {
        return List.of(values[0], values[1], values[2], values[3], values[4]);
    }
}
