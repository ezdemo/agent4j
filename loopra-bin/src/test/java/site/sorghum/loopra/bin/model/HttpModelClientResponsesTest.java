package site.sorghum.loopra.bin.model;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;
import site.sorghum.loopra.bin.agent.model.ChatMessage;
import site.sorghum.loopra.bin.agent.model.ToolCallEntry;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpModelClientResponsesTest {

    @Test
    void streamsResponsesTextToolCallsAndUsage() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/responses", exchange -> respondStream(exchange, requestBody));
        server.start();

        try {
            HttpModelClient client = responsesClient(server);
            ONode tools = ONode.ofJson("""
                    [{"type":"function","function":{"name":"read","description":"Read a file",\
                    "parameters":{"type":"object","properties":{"path":{"type":"string"}}}}}]
                    """);
            String previousReasoning = "{\"type\":\"reasoning\",\"id\":\"rs_old\",\"encrypted_content\":\"encrypted-old\",\"summary\":[]}";
            List<ChatMessage> messages = List.of(
                    ChatMessage.ofSystem("system prompt"),
                    ChatMessage.ofUser("hello"),
                    ChatMessage.assistant("", List.of(new ToolCallEntry(
                            "call_1", "read", "{\"path\":\"old.txt\"}", previousReasoning)), null),
                    ChatMessage.tool("call_1", "old content")
            );
            AtomicReference<String> content = new AtomicReference<>("");
            AtomicReference<String> reasoning = new AtomicReference<>("");
            AtomicReference<ONode> toolCalls = new AtomicReference<>();
            AtomicReference<int[]> usage = new AtomicReference<>();
            AtomicInteger done = new AtomicInteger();

            client.chatStream(messages, tools, new ModelClient.StreamCallback() {
                @Override
                public void onContentDelta(String token) {
                    content.updateAndGet(value -> value + token);
                }

                @Override
                public void onReasoningDelta(String token) {
                    reasoning.updateAndGet(value -> value + token);
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
            assertTrue(request.get("stream").getBoolean());
            assertTrue(request.get("messages").isNull());
            assertEquals("system prompt", request.select("$.input[0].content").getString());
            assertEquals("reasoning", request.select("$.input[2].type").getString());
            assertEquals("encrypted-old", request.select("$.input[2].encrypted_content").getString());
            assertEquals("function_call", request.select("$.input[3].type").getString());
            assertEquals("call_1", request.select("$.input[4].call_id").getString());
            assertEquals("function_call_output", request.select("$.input[4].type").getString());
            assertEquals("read", request.select("$.tools[0].name").getString());
            assertTrue(request.select("$.tools[0].function").isNull());
            assertEquals("high", request.select("$.reasoning.effort").getString());
            assertEquals("reasoning.encrypted_content", request.select("$.include[0]").getString());

            assertEquals("ok", content.get());
            assertEquals("thinking", reasoning.get());
            assertEquals("call_2", toolCalls.get().select("$[0].id").getString());
            assertEquals("read", toolCalls.get().select("$[0].function.name").getString());
            assertEquals("{\"path\":\"new.txt\"}", toolCalls.get().select("$[0].function.arguments").getString());
            assertEquals(1, toolCalls.get().size());
            assertEquals("encrypted-new", ONode.ofJson(toolCalls.get()
                    .select("$[0].response_reasoning").getString()).get("encrypted_content").getString());
            assertEquals(List.of(12, 4, 16, 5, 7), toList(usage.get()));
            assertEquals(1, done.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void explicitSessionAffinityOverridesThreadLocalForBodyAndHeaders() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<Headers> requestHeaders = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            requestHeaders.set(exchange.getRequestHeaders());
            byte[] response = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        String affinity = "parent-session:sub-agent:unique";
        HttpModelClient.CURRENT_LOG_SESSION.set("parent-session");
        try {
            HttpModelClient client = new HttpModelClient(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/chat",
                    "test-key", "gpt-test", "high");
            client.setSessionAffinity(affinity);
            client.chat(List.of(ChatMessage.ofUser("hello")), new ONode().asArray());

            assertEquals(affinity, ONode.ofJson(requestBody.get()).get("prompt_cache_key").getString());
            assertEquals(affinity, requestHeaders.get().getFirst("x-session-affinity"));
            assertEquals(affinity, requestHeaders.get().getFirst("X-Session-ID"));
            assertEquals(affinity, requestHeaders.get().getFirst("X-Claude-Code-Session-Id"));
            assertEquals(affinity, requestHeaders.get().getFirst("specific_channel_id"));
            assertEquals(affinity, requestHeaders.get().getFirst("Session_id"));
            assertEquals(affinity, requestHeaders.get().getFirst("channel_affinity"));
        } finally {
            HttpModelClient.CURRENT_LOG_SESSION.remove();
            server.stop(0);
        }
    }

    @Test
    void convertsNonStreamingResponseToChatMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/responses", HttpModelClientResponsesTest::respondNonStreaming);
        server.start();

        try {
            ONode message = responsesClient(server).chat(List.of(ChatMessage.ofUser("hello")), new ONode().asArray());

            assertEquals("assistant", message.get("role").getString());
            assertEquals("doneblocked", message.get("content").getString());
            assertEquals("summary", message.get("reasoning_content").getString());
            assertEquals("call_3", message.select("$.tool_calls[0].id").getString());
            assertEquals("write", message.select("$.tool_calls[0].function.name").getString());
            assertEquals("encrypted-nonstream", ONode.ofJson(message
                    .select("$.tool_calls[0].response_reasoning").getString()).get("encrypted_content").getString());
            assertFalse(message.get("tool_calls").getArray().isEmpty());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reportsTerminalStreamFailureAsError() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/responses", exchange -> {
            byte[] body = "data: {\"type\":\"response.failed\",\"response\":{\"error\":{\"message\":\"bad request\"}}}\n\n"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            AtomicReference<String> error = new AtomicReference<>();
            AtomicInteger done = new AtomicInteger();
            responsesClient(server).chatStream(List.of(ChatMessage.ofUser("hello")), new ONode().asArray(),
                    new ModelClient.StreamCallback() {
                        @Override
                        public void onError(String value) {
                            error.set(value);
                        }

                        @Override
                        public void onDone() {
                            done.incrementAndGet();
                        }
                    });

            assertTrue(error.get().contains("response.failed"));
            assertEquals(0, done.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsToolCallsWhenStreamEndsBeforeCompleted() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/responses", exchange -> {
            byte[] body = ("data: {\"type\":\"response.output_item.done\",\"output_index\":0,"
                    + "\"item\":{\"type\":\"function_call\",\"call_id\":\"call_4\","
                    + "\"name\":\"write\",\"arguments\":\"{\\\"path\\\":\\\"unsafe.txt\\\"}\"}}\n\n")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            AtomicReference<String> error = new AtomicReference<>();
            AtomicReference<ONode> toolCalls = new AtomicReference<>();
            AtomicInteger done = new AtomicInteger();
            responsesClient(server).chatStream(List.of(ChatMessage.ofUser("hello")), new ONode().asArray(),
                    new ModelClient.StreamCallback() {
                        @Override
                        public void onToolCalls(ONode calls) {
                            toolCalls.set(calls);
                        }

                        @Override
                        public void onError(String value) {
                            error.set(value);
                        }

                        @Override
                        public void onDone() {
                            done.incrementAndGet();
                        }
                    });

            assertEquals("Responses API stream ended before response.completed", error.get());
            assertNull(toolCalls.get());
            assertEquals(0, done.get());
        } finally {
            server.stop(0);
        }
    }

    private static HttpModelClient responsesClient(HttpServer server) {
        return new HttpModelClient(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/responses",
                "test-key", "test-model", "high", "test-channel", "responses");
    }

    private static void respondStream(HttpExchange exchange, AtomicReference<String> requestBody) throws IOException {
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        String body = """
                data: {"type":"response.reasoning_summary_text.delta","delta":"thinking"}

                data: {"type":"response.output_text.delta","delta":"ok"}

                data: {"type":"response.output_item.done","output_index":0,"item":{"type":"reasoning","id":"rs_new","encrypted_content":"encrypted-new","summary":[]}}

                data: {"type":"response.output_item.added","output_index":1,"item":{"type":"function_call","call_id":"call_2","name":"read","arguments":""}}

                data: {"type":"response.function_call_arguments.delta","output_index":1,"delta":"{\\\"path\\\":"}

                data: {"type":"response.function_call_arguments.done","output_index":1,"arguments":"{\\\"path\\\":\\\"new.txt\\\"}"}

                data: {"type":"response.output_item.done","output_index":1,"item":{"type":"function_call","call_id":"call_2","name":"read","arguments":"{\\\"path\\\":\\\"new.txt\\\"}"}}

                data: {"type":"response.completed","response":{"id":"resp_1","usage":{"input_tokens":12,"input_tokens_details":{"cached_tokens":5},"output_tokens":4,"output_tokens_details":{"reasoning_tokens":2},"total_tokens":16}}}

                data: {"type":"response.output_item.done","output_index":2,"item":{"type":"function_call","call_id":"call_after_done","name":"write","arguments":"{\\\"path\\\":\\\"unsafe.txt\\\"}"}}

                """;
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void respondNonStreaming(HttpExchange exchange) throws IOException {
        String body = """
                {"id":"resp_2","output":[
                  {"type":"reasoning","encrypted_content":"encrypted-nonstream","summary":[{"type":"summary_text","text":"summary"}]},
                  {"type":"message","role":"assistant","content":[{"type":"output_text","text":"done"},{"type":"refusal","refusal":"blocked"}]},
                  {"type":"function_call","call_id":"call_3","name":"write","arguments":"{\\\"path\\\":\\\"a.txt\\\"}"}
                ]}
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
