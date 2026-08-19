package site.sorghum.cutin.integrations.model;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;
import site.sorghum.cutin.core.context.Message;
import site.sorghum.cutin.core.context.Usage;
import site.sorghum.cutin.core.json.JsonSupport;
import site.sorghum.cutin.core.model.ModelCallRequest;
import site.sorghum.cutin.core.model.ModelResponse;
import site.sorghum.cutin.core.model.StreamChunk;
import site.sorghum.cutin.core.tool.ToolDefinition;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实协议 Provider 测试：用本地 HTTP 服务器验证三个 Provider 的
 * 请求映射、响应解析、流式增量、工具调用与用量统计。
 */
class RealModelProvidersTest {

    /** Chat Completions Provider 应正确映射请求、工具调用与用量。 */
    @Test
    void chatCompletionsProviderMapsRequestAndToolCalls() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = server("/chat/completions", requestBody, """
            {
              "choices": [{"message": {
                "role": "assistant",
                "content": "hello",
              "tool_calls": [{"id": "call_1", "type": "function",
                  "function": {"name": "read", "arguments": "{\\"path\\":\\"a.txt\\"}"}}]
              }}],
              "usage": {"prompt_tokens": 10, "completion_tokens": 5,
                "prompt_tokens_details": {"cached_tokens": 6}}
            }
            """);
        server.start();
        try {
            ModelProviderConfig config = new ModelProviderConfig(
                "chat",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "key",
                "gpt-5",
                Map.of()
            );
            OpenAiChatCompletionsProvider provider = new OpenAiChatCompletionsProvider(config);
            ModelCallRequest request = new ModelCallRequest(
                "gpt-5",
                List.of(new Message("user", "hi").withMetadata("images", List.of("https://example.com/cat.png"))),
                List.of(new ToolDefinition("read", "Read a file", Map.of("type", "object"))),
                Map.of()
            );

            ModelResponse response = provider.call(request);

            ONode body = JsonSupport.read(requestBody.get());
            assertEquals("gpt-5", JsonSupport.text(body, "", "model"));
            assertFalse(JsonSupport.boolValue(body, false, "stream"));
            assertEquals("text", JsonSupport.text(body, "", "messages", 0, "content", 0, "type"));
            assertEquals("hi", JsonSupport.text(body, "", "messages", 0, "content", 0, "text"));
            assertEquals("image_url", JsonSupport.text(body, "", "messages", 0, "content", 1, "type"));
            assertEquals("https://example.com/cat.png", JsonSupport.text(body, "", "messages", 0, "content", 1, "image_url", "url"));
            assertEquals("read", JsonSupport.text(body, "", "tools", 0, "function", "name"));
            assertEquals("hello", response.message().content());
            assertEquals(1, response.message().toolCalls().size());
            assertEquals("a.txt", response.message().toolCalls().get(0).arguments().get("path"));
            assertEquals(15, response.usage().totalTokens());
            assertEquals(6, response.usage().cacheReadTokens());
            assertEquals(4, response.usage().cacheMissTokens());
        } finally {
            server.stop(0);
        }
    }

    /** Responses Provider 应正确提取 instructions 并映射函数调用。 */
    @Test
    void responsesProviderMapsInstructionsAndFunctionCalls() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = server("/responses", requestBody, """
            {
              "output": [
                {"type": "message", "content": [{"type": "output_text", "text": "hello"}]},
                {"type": "function_call", "id": "call_1", "call_id": "call_1",
                 "name": "read", "arguments": "{\\"path\\":\\"b.txt\\"}"}
              ],
              "usage": {"input_tokens": 12, "output_tokens": 4,
                "input_tokens_details": {"cached_tokens": 7}}
            }
            """);
        server.start();
        try {
            ModelProviderConfig config = new ModelProviderConfig(
                "responses",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "key",
                "gpt-5",
                Map.of()
            );
            OpenAiResponsesProvider provider = new OpenAiResponsesProvider(config);
            ModelCallRequest request = new ModelCallRequest(
                "gpt-5",
                List.of(
                    new Message("system", "be concise"),
                    new Message("user", "hi").withMetadata("images", List.of("https://example.com/cat.png"))
                ),
                List.of(),
                Map.of()
            );

            ModelResponse response = provider.call(request);

            ONode body = JsonSupport.read(requestBody.get());
            assertEquals("be concise", JsonSupport.text(body, "", "instructions"));
            assertEquals("input_text", JsonSupport.text(body, "", "input", 0, "content", 0, "type"));
            assertEquals("hi", JsonSupport.text(body, "", "input", 0, "content", 0, "text"));
            assertEquals("input_image", JsonSupport.text(body, "", "input", 0, "content", 1, "type"));
            assertEquals("https://example.com/cat.png", JsonSupport.text(body, "", "input", 0, "content", 1, "image_url"));
            assertEquals("hello", response.message().content());
            assertEquals(1, response.message().toolCalls().size());
            assertEquals("b.txt", response.message().toolCalls().get(0).arguments().get("path"));
            assertEquals(16, response.usage().totalTokens());
            assertEquals(7, response.usage().cacheReadTokens());
            assertEquals(5, response.usage().cacheMissTokens());
        } finally {
            server.stop(0);
        }
    }

    /** Anthropic Provider 应正确映射 system、工具声明与 tool_use。 */
    @Test
    void anthropicProviderMapsSystemToolsAndToolUse() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = server("/v1/messages", requestBody, """
            {
              "content": [
                {"type": "text", "text": "hello"},
                {"type": "tool_use", "id": "call_1", "name": "read",
                 "input": {"path": "c.txt"}}
              ],
              "usage": {"input_tokens": 8, "output_tokens": 7,
                "cache_read_input_tokens": 3, "cache_creation_input_tokens": 2}
            }
            """);
        server.start();
        try {
            ModelProviderConfig config = new ModelProviderConfig(
                "anthropic",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "key",
                "claude-4",
                Map.of()
            );
            AnthropicMessagesProvider provider = new AnthropicMessagesProvider(config);
            ModelCallRequest request = new ModelCallRequest(
                "claude-4",
                List.of(
                    new Message("system", "be concise"),
                    new Message("user", "hi").withMetadata("images", List.of("data:image/png;base64,aGVsbG8="))
                ),
                List.of(new ToolDefinition("read", "Read a file", Map.of("type", "object"))),
                Map.of()
            );

            ModelResponse response = provider.call(request);

            ONode body = JsonSupport.read(requestBody.get());
            assertEquals("claude-4", JsonSupport.text(body, "", "model"));
            assertEquals("be concise", JsonSupport.text(body, "", "system"));
            assertEquals("text", JsonSupport.text(body, "", "messages", 0, "content", 0, "type"));
            assertEquals("hi", JsonSupport.text(body, "", "messages", 0, "content", 0, "text"));
            assertEquals("image", JsonSupport.text(body, "", "messages", 0, "content", 1, "type"));
            assertEquals("base64", JsonSupport.text(body, "", "messages", 0, "content", 1, "source", "type"));
            assertEquals("image/png", JsonSupport.text(body, "", "messages", 0, "content", 1, "source", "media_type"));
            assertEquals("aGVsbG8=", JsonSupport.text(body, "", "messages", 0, "content", 1, "source", "data"));
            assertEquals("read", JsonSupport.text(body, "", "tools", 0, "name"));
            assertFalse(JsonSupport.boolValue(body, false, "stream"));
            assertEquals("hello", response.message().content());
            assertEquals(1, response.message().toolCalls().size());
            assertEquals("c.txt", response.message().toolCalls().get(0).arguments().get("path"));
            assertEquals(20, response.usage().totalTokens());
            assertEquals(13, response.usage().promptTokens());
            assertEquals(3, response.usage().cacheReadTokens());
            assertEquals(2, response.usage().cacheCreationTokens());
            assertEquals(10, response.usage().cacheMissTokens());
        } finally {
            server.stop(0);
        }
    }

    /** Chat Completions 流式调用应拼接正文增量。 */
    @Test
    void chatCompletionsProviderStreamsContent() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = newStreamingServer("/chat/completions", requestBody);
        server.start();
        try {
            ModelProviderConfig config = new ModelProviderConfig(
                "chat",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "key",
                "gpt-5",
                Map.of()
            );
            OpenAiChatCompletionsProvider provider = new OpenAiChatCompletionsProvider(config);
            ModelCallRequest request = new ModelCallRequest(
                "gpt-5",
                List.of(new Message("user", "hi")),
                List.of(),
                Map.of()
            );

            String content;
            try (Stream<StreamChunk> chunks = provider.stream(request)) {
                content = chunks.map(StreamChunk::content).reduce("", String::concat);
            }

            assertEquals("hello", content);
            assertTrue(JsonSupport.boolValue(JsonSupport.read(requestBody.get()), false, "stream"));
        } finally {
            server.stop(0);
        }
    }

    /** Chat Completions 流式调用应只交付一次用量并聚合完整工具调用。 */
    @Test
    void chatCompletionsProviderStreamsToolCallsAndUsageOnce() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = streamingServer("/chat/completions", requestBody, """
            data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"read","arguments":"{\\"path\\":\\"a.txt\\"}"}}]}}]}

            data: {"choices":[{"delta":{"content":"hello"}}]}

            data: {"usage":{"prompt_tokens":10,"completion_tokens":5,"prompt_tokens_details":{"cached_tokens":4}}}

            data: [DONE]

            """);
        server.start();
        try {
            ModelProviderConfig config = new ModelProviderConfig(
                "chat",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "key",
                "gpt-5",
                Map.of()
            );
            OpenAiChatCompletionsProvider provider = new OpenAiChatCompletionsProvider(config);
            ModelCallRequest request = new ModelCallRequest(
                "gpt-5",
                List.of(new Message("user", "hi")),
                List.of(),
                Map.of()
            );

            StringBuilder content = new StringBuilder();
            AtomicInteger usageChunks = new AtomicInteger();
            Usage[] totalUsage = {Usage.ZERO};
            AtomicReference<StreamChunk> last = new AtomicReference<>();
            try (Stream<StreamChunk> chunks = provider.stream(request)) {
                chunks.forEach(chunk -> {
                    if (chunk.content() != null) {
                        content.append(chunk.content());
                    }
                    if (chunk.usage().totalTokens() > 0) {
                        usageChunks.incrementAndGet();
                    }
                    totalUsage[0] = totalUsage[0].add(chunk.usage());
                    last.set(chunk);
                });
            }

            assertEquals("hello", content.toString());
            assertEquals(1, usageChunks.get());
            assertEquals(15, totalUsage[0].totalTokens());
            assertEquals(4, totalUsage[0].cacheReadTokens());
            assertEquals(6, totalUsage[0].cacheMissTokens());
            assertEquals(1, last.get().toolCalls().size(), () -> "last=" + last.get());
            assertEquals("read", last.get().toolCalls().get(0).toolId());
            assertEquals("a.txt", last.get().toolCalls().get(0).arguments().get("path"));
        } finally {
            server.stop(0);
        }
    }

    /** Chat Completions 流若逐块携带累计 usage（OpenRouter 等网关行为），应取末值而非反复累加。 */
    @Test
    void chatCompletionsProviderTakesLastUsageWhenRepeated() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = streamingServer("/chat/completions", requestBody, """
            data: {"choices":[{"delta":{"content":"hel"}}],"usage":{"prompt_tokens":10,"completion_tokens":1}}

            data: {"choices":[{"delta":{"content":"lo"}}],"usage":{"prompt_tokens":11,"completion_tokens":2}}

            data: {"choices":[{"delta":{"content":" world"}}],"usage":{"prompt_tokens":12,"completion_tokens":3}}

            data: [DONE]

            """);
        server.start();
        try {
            ModelProviderConfig config = new ModelProviderConfig(
                "chat",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "key",
                "gpt-5",
                Map.of()
            );
            OpenAiChatCompletionsProvider provider = new OpenAiChatCompletionsProvider(config);
            ModelCallRequest request = new ModelCallRequest(
                "gpt-5",
                List.of(new Message("user", "hi")),
                List.of(),
                Map.of()
            );

            StringBuilder content = new StringBuilder();
            AtomicInteger usageChunks = new AtomicInteger();
            Usage[] totalUsage = {Usage.ZERO};
            try (Stream<StreamChunk> chunks = provider.stream(request)) {
                chunks.forEach(chunk -> {
                    if (chunk.content() != null) {
                        content.append(chunk.content());
                    }
                    if (chunk.usage().totalTokens() > 0) {
                        usageChunks.incrementAndGet();
                    }
                    totalUsage[0] = totalUsage[0].add(chunk.usage());
                });
            }

            assertEquals("hello world", content.toString());
            assertEquals(1, usageChunks.get());
            assertEquals(12, totalUsage[0].promptTokens());
            assertEquals(3, totalUsage[0].completionTokens());
            assertEquals(15, totalUsage[0].totalTokens());
        } finally {
            server.stop(0);
        }
    }

    /** llama.cpp 风格的 timings 应转换为统一 Usage，并保留缓存命中明细。 */
    @Test
    void chatCompletionsProviderMapsLlamaCppTimingsUsage() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = streamingServer("/chat/completions", requestBody, """
            data: {"choices":[{"finish_reason":"tool_calls","index":0,"delta":{}}],"created":1787109007,"id":"chatcmpl-1","model":"Qwen3.8","object":"chat.completion.chunk","timings":{"cache_n":23058,"prompt_n":4,"prompt_ms":110.645,"predicted_n":482,"predicted_ms":30127.483}}

            data: [DONE]

            """);
        server.start();
        try {
            ModelProviderConfig config = new ModelProviderConfig(
                "chat",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "key",
                "Qwen3.8",
                Map.of()
            );
            OpenAiChatCompletionsProvider provider = new OpenAiChatCompletionsProvider(config);
            ModelCallRequest request = new ModelCallRequest(
                "Qwen3.8",
                List.of(new Message("user", "hi")),
                List.of(),
                Map.of()
            );

            Usage total = Usage.ZERO;
            try (Stream<StreamChunk> chunks = provider.stream(request)) {
                total = chunks.map(StreamChunk::usage).reduce(Usage.ZERO, Usage::add);
            }

            assertEquals(23062, total.promptTokens());
            assertEquals(482, total.completionTokens());
            assertEquals(23058, total.cacheReadTokens());
            assertEquals(4, total.cacheMissTokens());
            assertEquals(23544, total.totalTokens());
        } finally {
            server.stop(0);
        }
    }

    /** Ollama 原生最终响应的计数字段应转换为统一 Usage。 */
    @Test
    void chatCompletionsProviderMapsOllamaUsage() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = streamingServer("/chat/completions", requestBody, """
            data: {"model":"qwen3","message":{"role":"assistant","content":""},"done":true,"prompt_eval_count":23062,"eval_count":482,"prompt_eval_duration":110645000,"eval_duration":30127483000}

            data: [DONE]

            """);
        server.start();
        try {
            ModelProviderConfig config = new ModelProviderConfig(
                "chat",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "key",
                "qwen3",
                Map.of()
            );
            OpenAiChatCompletionsProvider provider = new OpenAiChatCompletionsProvider(config);
            ModelCallRequest request = new ModelCallRequest(
                "qwen3",
                List.of(new Message("user", "hi")),
                List.of(),
                Map.of()
            );

            Usage total;
            try (Stream<StreamChunk> chunks = provider.stream(request)) {
                total = chunks.map(StreamChunk::usage).reduce(Usage.ZERO, Usage::add);
            }

            assertEquals(23062, total.promptTokens());
            assertEquals(482, total.completionTokens());
            assertEquals(0, total.cacheReadTokens());
            assertEquals(23062, total.cacheMissTokens());
            assertEquals(23544, total.totalTokens());
        } finally {
            server.stop(0);
        }
    }

    /** Responses 流式调用应只交付一次用量并聚合完整函数调用。 */
    @Test
    void responsesProviderStreamsFunctionCallAndUsageOnce() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = streamingServer("/responses", requestBody, """
            data: {"type":"response.output_item.added","output_index":0,"item":{"type":"function_call","id":"call_1","call_id":"call_1","name":"read","arguments":""}}

            data: {"type":"response.function_call_arguments.delta","output_index":0,"delta":"{\\"path\\":\\"b.txt\\"}"}

            data: {"type":"response.function_call_arguments.done","output_index":0,"arguments":"{\\"path\\":\\"b.txt\\"}"}

            data: {"type":"response.output_text.delta","delta":"hello"}

            data: {"type":"response.completed","response":{"usage":{"input_tokens":12,"output_tokens":4,"input_tokens_details":{"cached_tokens":7}}}}

            data: [DONE]

            """);
        server.start();
        try {
            ModelProviderConfig config = new ModelProviderConfig(
                "responses",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "key",
                "gpt-5",
                Map.of()
            );
            OpenAiResponsesProvider provider = new OpenAiResponsesProvider(config);
            ModelCallRequest request = new ModelCallRequest(
                "gpt-5",
                List.of(new Message("user", "hi")),
                List.of(),
                Map.of()
            );

            StringBuilder content = new StringBuilder();
            AtomicInteger usageChunks = new AtomicInteger();
            Usage[] totalUsage = {Usage.ZERO};
            AtomicReference<StreamChunk> last = new AtomicReference<>();
            try (Stream<StreamChunk> chunks = provider.stream(request)) {
                chunks.forEach(chunk -> {
                    if (chunk.content() != null) {
                        content.append(chunk.content());
                    }
                    if (chunk.usage().totalTokens() > 0) {
                        usageChunks.incrementAndGet();
                    }
                    totalUsage[0] = totalUsage[0].add(chunk.usage());
                    last.set(chunk);
                });
            }

            assertEquals("hello", content.toString());
            assertEquals(1, usageChunks.get());
            assertEquals(16, totalUsage[0].totalTokens());
            assertEquals(7, totalUsage[0].cacheReadTokens());
            assertEquals(5, totalUsage[0].cacheMissTokens());
            assertEquals(1, last.get().toolCalls().size(), () -> "last=" + last.get());
            assertEquals("read", last.get().toolCalls().get(0).toolId());
            assertEquals("b.txt", last.get().toolCalls().get(0).arguments().get("path"));
        } finally {
            server.stop(0);
        }
    }

    /** Anthropic 流式调用应聚合 thinking、工具调用并只交付一次用量。 */
    @Test
    void anthropicProviderStreamsThinkingToolUseAndUsageOnce() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = streamingServer("/v1/messages", requestBody, """
            data: {"type":"message_start","message":{"usage":{"input_tokens":8,"output_tokens":0,"cache_read_input_tokens":3,"cache_creation_input_tokens":2}}}

            data: {"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":"let me","signature":""}}

            data: {"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":" think"}}

            data: {"type":"content_block_delta","index":0,"delta":{"type":"signature_delta","signature":"sig123"}}

            data: {"type":"content_block_stop","index":0}

            data: {"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"call_1","name":"read","input":{}}}

            data: {"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\\"path\\":\\"c.txt\\"}"}}

            data: {"type":"content_block_stop","index":1}

            data: {"type":"content_block_delta","index":2,"delta":{"type":"text_delta","text":"hello"}}

            data: {"type":"message_delta","usage":{"output_tokens":7}}

            data: {"type":"message_stop"}

            data: [DONE]

            """);
        server.start();
        try {
            ModelProviderConfig config = new ModelProviderConfig(
                "anthropic",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "key",
                "claude-4",
                Map.of()
            );
            AnthropicMessagesProvider provider = new AnthropicMessagesProvider(config);
            ModelCallRequest request = new ModelCallRequest(
                "claude-4",
                List.of(new Message("user", "hi")),
                List.of(),
                Map.of()
            );

            StringBuilder content = new StringBuilder();
            AtomicInteger usageChunks = new AtomicInteger();
            Usage[] totalUsage = {Usage.ZERO};
            AtomicReference<StreamChunk> last = new AtomicReference<>();
            try (Stream<StreamChunk> chunks = provider.stream(request)) {
                chunks.forEach(chunk -> {
                    if (chunk.content() != null) {
                        content.append(chunk.content());
                    }
                    if (chunk.usage().totalTokens() > 0) {
                        usageChunks.incrementAndGet();
                    }
                    totalUsage[0] = totalUsage[0].add(chunk.usage());
                    last.set(chunk);
                });
            }

            assertEquals("hello", content.toString());
            assertEquals(1, usageChunks.get());
            assertEquals(20, totalUsage[0].totalTokens());
            assertEquals(3, totalUsage[0].cacheReadTokens());
            assertEquals(2, totalUsage[0].cacheCreationTokens());
            assertEquals(10, totalUsage[0].cacheMissTokens());
            assertEquals(1, last.get().thinkingBlocks().size());
            assertTrue(last.get().thinkingBlocks().get(0).contains("let me think"));
            assertTrue(last.get().thinkingBlocks().get(0).contains("sig123"));
            assertEquals(1, last.get().toolCalls().size(), () -> "last=" + last.get());
            assertEquals("read", last.get().toolCalls().get(0).toolId());
            assertEquals("c.txt", last.get().toolCalls().get(0).arguments().get("path"));
        } finally {
            server.stop(0);
        }
    }

    /** 创建返回固定 JSON 的普通 HTTP 服务器。 */
    private HttpServer server(String path, AtomicReference<String> requestBody, String response) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(path, exchange -> {
            try {
                respondJson(exchange, requestBody, response);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return server;
    }

    /** 创建返回 SSE 流的 HTTP 服务器。 */
    private HttpServer streamingServer(String path, AtomicReference<String> requestBody, String body) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(path, exchange -> {
            try {
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return server;
    }

    /** 创建返回固定 SSE 流的 HTTP 服务器。 */
    private HttpServer newStreamingServer(String path, AtomicReference<String> requestBody) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(path, exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = ("data: {\"choices\":[{\"delta\":{\"content\":\"he\"}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":\"llo\"}}]}\n\n"
                + "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        return server;
    }

    /** 统一响应 JSON：记录请求体、设置 Content-Type 并返回响应内容。 */
    private static void respondJson(HttpExchange exchange, AtomicReference<String> requestBody, String response)
        throws Exception {
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] body = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
