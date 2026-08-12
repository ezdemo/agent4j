package site.sorghum.loopra.bin.model;

import okhttp3.Request;
import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;
import site.sorghum.loopra.bin.agent.model.ChatMessage;
import site.sorghum.loopra.bin.agent.model.ToolCallEntry;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AnthropicApiProtocolTest {

    private static final AnthropicApiProtocol PROTOCOL = new AnthropicApiProtocol();

    @Test
    void buildsRequestWithSystemToolsAndMergedToolResults() {
        ONode tools = ONode.ofJson("""
                [{"type":"function","function":{"name":"read","description":"Read a file",\
                "parameters":{"type":"object","properties":{"path":{"type":"string"}}}}}]
                """);
        ModelApiProtocol.RequestContext context = new ModelApiProtocol.RequestContext(
                "test-model", "none", List.of(
                        ChatMessage.ofSystem("system prompt"),
                        ChatMessage.ofUser("hello"),
                        ChatMessage.assistant("", List.of(new ToolCallEntry(
                                "call_1", "read", "{\"path\":\"a.txt\"}")), null),
                        ChatMessage.tool("call_1", "file content"),
                        ChatMessage.ofUser("follow up")
                ), tools, null, null);

        ONode request = PROTOCOL.buildRequest(context);

        assertEquals("test-model", request.get("model").getString());
        assertEquals(8192, request.get("max_tokens").getInt());
        assertEquals("system prompt", request.get("system").getString());
        assertTrue(request.get("thinking").isNull());
        // user(hello) → assistant(tool_use) → user(工具结果 + follow up 合并)
        assertEquals(3, request.get("messages").size());
        assertEquals("hello", request.select("$.messages[0].content").getString());
        assertEquals("tool_use", request.select("$.messages[1].content[0].type").getString());
        assertEquals("call_1", request.select("$.messages[1].content[0].id").getString());
        assertEquals("read", request.select("$.messages[1].content[0].name").getString());
        assertEquals("a.txt", request.select("$.messages[1].content[0].input.path").getString());
        assertEquals("tool_result", request.select("$.messages[2].content[0].type").getString());
        assertEquals("call_1", request.select("$.messages[2].content[0].tool_use_id").getString());
        assertEquals("file content", request.select("$.messages[2].content[0].content").getString());
        assertEquals("follow up", request.select("$.messages[2].content[1].text").getString());
        assertEquals("read", request.select("$.tools[0].name").getString());
        assertEquals("object", request.select("$.tools[0].input_schema.type").getString());
        assertTrue(request.select("$.tools[0].function").isNull());
    }

    @Test
    void omitsThinkingRegardlessOfReasoningEffortAndAppliesSessionCaching() {
        ONode tools = ONode.ofJson("""
                [{"type":"function","function":{"name":"read","parameters":{"type":"object"}}}]
                """);
        ModelApiProtocol.RequestContext context = new ModelApiProtocol.RequestContext(
                "test-model", "high", List.of(ChatMessage.ofSystem("sys"), ChatMessage.ofUser("hello")),
                tools, "user-1", "session-1");

        ONode request = PROTOCOL.buildRequest(context);

        // Claude 4.7+ 拒绝 thinking.type=enabled（400），因此不发送 thinking，max_tokens 恒定。
        assertTrue(request.get("thinking").isNull());
        assertEquals(8192, request.get("max_tokens").getInt());
        assertEquals("ephemeral", request.select("$.system[0].cache_control.type").getString());
        assertEquals("ephemeral", request.select("$.tools[0].cache_control.type").getString());
        assertEquals("ephemeral", request.select("$.messages[0].content[0].cache_control.type").getString());
        // DeepSeek 等网关用 metadata.user_id 做限流隔离
        assertEquals("user-1", request.select("$.metadata.user_id").getString());
    }

    @Test
    void convertsImagePartsToBase64AndUrlSources() {
        ModelApiProtocol.RequestContext base64Context = new ModelApiProtocol.RequestContext(
                "test-model", "none",
                List.of(ChatMessage.ofUser("look", List.of("data:image/png;base64,AAA="))),
                new ONode().asArray(), null, null);

        ONode request = PROTOCOL.buildRequest(base64Context);

        assertEquals("image", request.select("$.messages[0].content[0].type").getString());
        assertEquals("base64", request.select("$.messages[0].content[0].source.type").getString());
        assertEquals("image/png", request.select("$.messages[0].content[0].source.media_type").getString());
        assertEquals("AAA=", request.select("$.messages[0].content[0].source.data").getString());
        assertEquals("look", request.select("$.messages[0].content[1].text").getString());

        ModelApiProtocol.RequestContext urlContext = new ModelApiProtocol.RequestContext(
                "test-model", "none",
                List.of(ChatMessage.ofUser("", List.of("https://example.com/img.png"))),
                new ONode().asArray(), null, null);
        ONode urlRequest = PROTOCOL.buildRequest(urlContext);
        assertEquals("url", urlRequest.select("$.messages[0].content[0].source.type").getString());
        assertEquals("https://example.com/img.png",
                urlRequest.select("$.messages[0].content[0].source.url").getString());
    }

    @Test
    void mapsToolImageResultIntoVisualContentBlock() {
        ChatMessage imageToolResult = ChatMessage.toolWithImage(
                "call-img", "图片已读取", "data:image/jpeg;base64,BBBB", "high");
        ModelApiProtocol.RequestContext context = new ModelApiProtocol.RequestContext(
                "test-model", "none", List.of(imageToolResult), new ONode().asArray(), null, null);

        ONode request = PROTOCOL.buildRequest(context);

        assertEquals("tool_result", request.select("$.messages[0].content[0].type").getString());
        assertEquals("text", request.select("$.messages[0].content[0].content[0].type").getString());
        assertEquals("image", request.select("$.messages[0].content[0].content[1].type").getString());
        assertEquals("image/jpeg",
                request.select("$.messages[0].content[0].content[1].source.media_type").getString());
    }

    @Test
    void parsesNonStreamingResponseIntoChatMessage() throws Exception {
        ONode response = ONode.ofJson("""
                {"id":"msg_1","type":"message","role":"assistant","content":[
                  {"type":"thinking","thinking":"think step"},
                  {"type":"text","text":"done"},
                  {"type":"tool_use","id":"toolu_1","name":"write","input":{"path":"a.txt","content":"data"}}
                ],"usage":{"input_tokens":10,"output_tokens":5}}
                """);

        ONode message = PROTOCOL.parseResponse(response, "response");

        assertEquals("assistant", message.get("role").getString());
        assertEquals("done", message.get("content").getString());
        assertEquals("think step", message.get("reasoning_content").getString());
        assertEquals("toolu_1", message.select("$.tool_calls[0].id").getString());
        assertEquals("write", message.select("$.tool_calls[0].function.name").getString());
        assertEquals("{\"path\":\"a.txt\",\"content\":\"data\"}",
                message.select("$.tool_calls[0].function.arguments").getString());
    }

    @Test
    void streamsTextReasoningToolCallsAndUsage() {
        ModelApiStreamState state = new ModelApiStreamState();
        AtomicReference<String> content = new AtomicReference<>("");
        AtomicReference<String> reasoning = new AtomicReference<>("");
        AtomicReference<ONode> toolCalls = new AtomicReference<>();
        AtomicReference<int[]> usage = new AtomicReference<>();
        ModelClient.StreamCallback callback = new ModelClient.StreamCallback() {
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
        };

        PROTOCOL.processStreamChunk(ONode.ofJson("{\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\"," +
                "\"usage\":{\"input_tokens\":12,\"cache_read_input_tokens\":5,\"cache_creation_input_tokens\":7}}}"),
                callback, state);
        PROTOCOL.processStreamChunk(ONode.ofJson(
                "{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"thinking\",\"thinking\":\"\"}}"),
                callback, state);
        PROTOCOL.processStreamChunk(ONode.ofJson(
                "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"think\"}}"),
                callback, state);
        PROTOCOL.processStreamChunk(ONode.ofJson("{\"type\":\"content_block_stop\",\"index\":0}"), callback, state);
        PROTOCOL.processStreamChunk(ONode.ofJson(
                "{\"type\":\"content_block_start\",\"index\":1,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}"),
                callback, state);
        PROTOCOL.processStreamChunk(ONode.ofJson(
                "{\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello\"}}"),
                callback, state);
        PROTOCOL.processStreamChunk(ONode.ofJson(
                "{\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"text_delta\",\"text\":\" world\"}}"),
                callback, state);
        PROTOCOL.processStreamChunk(ONode.ofJson("{\"type\":\"content_block_stop\",\"index\":1}"), callback, state);
        PROTOCOL.processStreamChunk(ONode.ofJson(
                "{\"type\":\"content_block_start\",\"index\":2,\"content_block\":{\"type\":\"tool_use\"," +
                        "\"id\":\"toolu_2\",\"name\":\"read\",\"input\":{}}}"), callback, state);
        PROTOCOL.processStreamChunk(ONode.ofJson(
                "{\"type\":\"content_block_delta\",\"index\":2,\"delta\":{\"type\":\"input_json_delta\"," +
                        "\"partial_json\":\"{\\\"path\\\":\\\"b.txt\\\"}\"}}"), callback, state);
        PROTOCOL.processStreamChunk(ONode.ofJson("{\"type\":\"content_block_stop\",\"index\":2}"), callback, state);
        PROTOCOL.processStreamChunk(ONode.ofJson(
                "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"tool_use\"},\"usage\":{\"output_tokens\":4}}"),
                callback, state);
        PROTOCOL.processStreamChunk(ONode.ofJson("{\"type\":\"message_stop\"}"), callback, state);
        // HttpModelClient 在流结束后调用 completeStream 触发 tool_calls 回调
        PROTOCOL.completeStream(state, callback);

        assertEquals("Hello world", content.get());
        assertEquals("think", reasoning.get());
        assertEquals(1, toolCalls.get().size());
        assertEquals("toolu_2", toolCalls.get().select("$[0].id").getString());
        assertEquals("read", toolCalls.get().select("$[0].function.name").getString());
        assertEquals("{\"path\":\"b.txt\"}", toolCalls.get().select("$[0].function.arguments").getString());
        // 单次 message_delta 上报完整值；多次 message_delta 的增量场景见 reportsSubsequentMessageDeltaAsIncrement
        assertEquals(List.of(12, 4, 16, 5, 7), toList(usage.get()));
        assertTrue(state.completed);
        assertNull(PROTOCOL.streamCompletionError(state));
    }

    @Test
    void reportsSubsequentMessageDeltaAsIncrement() {
        ModelApiStreamState state = new ModelApiStreamState();
        AtomicReference<int[]> firstUsage = new AtomicReference<>();
        AtomicReference<int[]> secondUsage = new AtomicReference<>();
        AtomicInteger callCount = new AtomicInteger();
        ModelClient.StreamCallback callback = new ModelClient.StreamCallback() {
            @Override
            public void onUsage(int promptTokens, int completionTokens, int totalTokens,
                                int cacheHitTokens, int cacheMissTokens) {
                int[] values = new int[]{promptTokens, completionTokens, totalTokens, cacheHitTokens, cacheMissTokens};
                if (callCount.incrementAndGet() == 1) {
                    firstUsage.set(values);
                } else {
                    secondUsage.set(values);
                }
            }
        };

        PROTOCOL.processStreamChunk(ONode.ofJson("{\"type\":\"message_start\",\"message\":{\"id\":\"msg_x\"," +
                "\"usage\":{\"input_tokens\":100,\"cache_read_input_tokens\":20,\"cache_creation_input_tokens\":30}}}"),
                callback, state);
        PROTOCOL.processStreamChunk(ONode.ofJson(
                "{\"type\":\"message_delta\",\"delta\":{},\"usage\":{\"output_tokens\":10}}"), callback, state);
        PROTOCOL.processStreamChunk(ONode.ofJson(
                "{\"type\":\"message_delta\",\"delta\":{},\"usage\":{\"output_tokens\":25}}"), callback, state);

        assertEquals(List.of(100, 10, 110, 20, 30), toList(firstUsage.get()));
        assertEquals(List.of(0, 15, 15, 0, 0), toList(secondUsage.get()));
    }

    @Test
    void preservesThinkingBlocksWithSignatureForRoundTrip() throws Exception {
        // 非流式：服务端返回 thinking 块（含 signature）与 redacted_thinking 块
        ONode response = ONode.ofJson("""
                {"id":"msg_t1","type":"message","role":"assistant","content":[
                  {"type":"redacted_thinking","data":"REDACTED_1"},
                  {"type":"thinking","thinking":"think step","signature":"SIG_1"},
                  {"type":"text","text":"done"}
                ],"usage":{"input_tokens":10,"output_tokens":5}}
                """);

        ONode message = PROTOCOL.parseResponse(response, "response");

        assertEquals("done", message.get("content").getString());
        assertEquals("think step", message.get("reasoning_content").getString());
        assertEquals("redacted_thinking", message.select("$.thinking_blocks[0].type").getString());
        assertEquals("REDACTED_1", message.select("$.thinking_blocks[0].data").getString());
        assertEquals("thinking", message.select("$.thinking_blocks[1].type").getString());
        assertEquals("SIG_1", message.select("$.thinking_blocks[1].signature").getString());

        // 回传：assistant 消息带 thinkingBlocks → 块原样置于 text 之前
        ChatMessage assistant = ChatMessage.fromMap(message.toBean());
        assertTrue(assistant.getThinkingBlocks().size() == 2);
        ModelApiProtocol.RequestContext context = new ModelApiProtocol.RequestContext(
                "test-model", "none", List.of(assistant), new ONode().asArray(), null, null);

        ONode request = PROTOCOL.buildRequest(context);

        assertEquals("redacted_thinking", request.select("$.messages[0].content[0].type").getString());
        assertEquals("REDACTED_1", request.select("$.messages[0].content[0].data").getString());
        assertEquals("thinking", request.select("$.messages[0].content[1].type").getString());
        assertEquals("SIG_1", request.select("$.messages[0].content[1].signature").getString());
        assertEquals("text", request.select("$.messages[0].content[2].type").getString());
        assertEquals("done", request.select("$.messages[0].content[2].text").getString());
    }

    @Test
    void streamsThinkingBlocksWithSignatureAndRedactedData() {
        ModelApiStreamState state = new ModelApiStreamState();
        AtomicReference<List<String>> blocks = new AtomicReference<>();
        ModelClient.StreamCallback callback = new ModelClient.StreamCallback() {
            @Override
            public void onThinkingBlocks(List<String> value) {
                blocks.set(value);
            }
        };

        // thinking 块：start + thinking_delta + signature_delta + stop
        PROTOCOL.processStreamChunk(ONode.ofJson(
                "{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"thinking\",\"thinking\":\"\"}}"),
                callback, state);
        PROTOCOL.processStreamChunk(ONode.ofJson(
                "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"think\"}}"),
                callback, state);
        PROTOCOL.processStreamChunk(ONode.ofJson(
                "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"signature_delta\",\"signature\":\"SIG_2\"}}"),
                callback, state);
        PROTOCOL.processStreamChunk(ONode.ofJson("{\"type\":\"content_block_stop\",\"index\":0}"), callback, state);
        // redacted_thinking 块：start 带 data + stop
        PROTOCOL.processStreamChunk(ONode.ofJson(
                "{\"type\":\"content_block_start\",\"index\":1,\"content_block\":{\"type\":\"redacted_thinking\",\"data\":\"REDACTED_2\"}}"),
                callback, state);
        PROTOCOL.processStreamChunk(ONode.ofJson("{\"type\":\"content_block_stop\",\"index\":1}"), callback, state);
        PROTOCOL.completeStream(state, callback);

        List<String> captured = blocks.get();
        assertEquals(2, captured.size());
        assertEquals("thinking", ONode.ofJson(captured.get(0)).get("type").getString());
        assertEquals("think", ONode.ofJson(captured.get(0)).get("thinking").getString());
        assertEquals("SIG_2", ONode.ofJson(captured.get(0)).get("signature").getString());
        assertEquals("redacted_thinking", ONode.ofJson(captured.get(1)).get("type").getString());
        assertEquals("REDACTED_2", ONode.ofJson(captured.get(1)).get("data").getString());
    }

    @Test
    void marksStreamErrorEvents() {
        ModelApiStreamState state = new ModelApiStreamState();
        PROTOCOL.processStreamChunk(ONode.ofJson(
                "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"overloaded\"}}"),
                new ModelClient.StreamCallback() {
                }, state);
        assertNotNull(state.errorData);
        assertTrue(state.retryableError);
        assertFalse(state.contextLengthExceeded);
        assertNotNull(PROTOCOL.streamCompletionError(state));

        ModelApiStreamState contextState = new ModelApiStreamState();
        PROTOCOL.processStreamChunk(ONode.ofJson(
                "{\"type\":\"error\",\"error\":{\"type\":\"context_length_exceeded\",\"message\":\"too long\"}}"),
                new ModelClient.StreamCallback() {
                }, contextState);
        assertTrue(contextState.contextLengthExceeded);
        assertFalse(contextState.retryableError);
    }

    @Test
    void appliesAnthropicAuthHeadersWithoutBearer() {
        Request.Builder builder = new Request.Builder().url("https://example.test/v1/messages");
        PROTOCOL.applyAuthHeaders(builder, "secret-key");
        Request request = builder.build();

        // 官方 Anthropic / DeepSeek 认 x-api-key；小米 MiMo 认 api-key，两者同时发送。
        assertEquals("secret-key", request.header("x-api-key"));
        assertEquals("secret-key", request.header("api-key"));
        assertEquals("2023-06-01", request.header("anthropic-version"));
        assertNull(request.header("Authorization"));
    }

    private static List<Integer> toList(int[] values) {
        return List.of(values[0], values[1], values[2], values[3], values[4]);
    }
}
