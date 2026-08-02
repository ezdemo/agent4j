package site.sorghum.loopra.bin.model;

import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;
import site.sorghum.loopra.bin.agent.model.ChatMessage;
import site.sorghum.loopra.bin.agent.model.ToolCallEntry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModelApiProtocolsTest {

    @Test
    void resolvesConfiguredProtocolAndFallsBackToChatCompletions() {
        assertInstanceOf(ResponsesApiProtocol.class, ModelApiProtocols.resolve("responses"));
        assertInstanceOf(ResponsesApiProtocol.class, ModelApiProtocols.resolve("RESPONSES"));
        assertInstanceOf(ChatCompletionsApiProtocol.class, ModelApiProtocols.resolve(null));
        assertInstanceOf(ChatCompletionsApiProtocol.class, ModelApiProtocols.resolve("future_protocol"));
    }

    @Test
    void chatCompletionsRequestMappingDoesNotMutateMessages() {
        ChatMessage toolMessage = ChatMessage.tool("call-1", "");
        ModelApiProtocol.RequestContext context = new ModelApiProtocol.RequestContext(
                "test-model", "none", List.of(toolMessage), new ONode().asArray(), null, null);

        ONode request = ModelApiProtocols.resolve("chat_completions").buildRequest(context);

        assertEquals("ERROR 工具执行失败或者工具执行结果为空",
                request.select("$.messages[0].content").getString());
        assertTrue(toolMessage.getContent().isEmpty());
    }

    @Test
    void responsesFunctionCallInputIncludesItemIdAlongsideCallId() {
        ChatMessage assistant = ChatMessage.assistant("", List.of(
                new ToolCallEntry("call-1", "read", "{}")), null);
        ModelApiProtocol.RequestContext context = new ModelApiProtocol.RequestContext(
                "test-model", "none", List.of(assistant), new ONode().asArray(), null, null);

        ONode request = ModelApiProtocols.resolve("responses").buildRequest(context);

        assertEquals("function_call", request.select("$.input[0].type").getString());
        assertEquals("call-1", request.select("$.input[0].id").getString());
        assertEquals("call-1", request.select("$.input[0].call_id").getString());
    }

    @Test
    void deepSeekResponsesRequestReplaysPlaintextReasoningBeforeToolCall() {
        ChatMessage assistant = ChatMessage.assistant("", List.of(
                new ToolCallEntry("call-1", "read", "{}")), "thinking");
        ModelApiProtocol.RequestContext context = new ModelApiProtocol.RequestContext(
                "deepseek-v4-flash", "high",
                List.of(assistant, ChatMessage.tool("call-1", "content")),
                new ONode().asArray(), null, null, "https://api.deepseek.com/responses");

        ONode request = ModelApiProtocols.resolve("responses").buildRequest(context);

        assertEquals("reasoning", request.select("$.input[0].type").getString());
        assertEquals("reasoning_text", request.select("$.input[0].content[0].type").getString());
        assertEquals("thinking", request.select("$.input[0].content[0].text").getString());
        assertTrue(request.select("$.input[0].id").isNull());
        assertTrue(request.select("$.input[0].summary").isNull());
        assertTrue(request.select("$.input[0].encrypted_content").isNull());
        assertEquals("function_call", request.select("$.input[1].type").getString());
        assertEquals("function_call_output", request.select("$.input[2].type").getString());
        assertEquals("high", request.select("$.reasoning.effort").getString());
        assertTrue(request.select("$.reasoning.summary").isNull());
        assertTrue(request.get("include").isNull());
    }

    @Test
    void plaintextReasoningHistorySelectsDeepSeekCompatibilityForProviderAliases() {
        ChatMessage assistant = ChatMessage.assistant("", List.of(
                new ToolCallEntry("call-1", "read", "{}",
                        "{\"type\":\"reasoning\",\"id\":\"rs_1\",\"summary\":[]}")), "thinking");
        ModelApiProtocol.RequestContext context = new ModelApiProtocol.RequestContext(
                "provider-model-alias", "high", List.of(assistant), new ONode().asArray(),
                null, null, "https://console-go.example/responses");

        ONode request = ModelApiProtocols.resolve("responses").buildRequest(context);

        assertEquals("thinking", request.select("$.input[0].content[0].text").getString());
        assertTrue(request.select("$.input[0].id").isNull());
        assertTrue(request.select("$.input[0].summary").isNull());
        assertTrue(request.select("$.reasoning.summary").isNull());
        assertTrue(request.get("include").isNull());
    }

    @Test
    void responsesStreamUsesCompleteReasoningItemWhenDeltasAreAbsent() {
        ModelApiProtocol protocol = ModelApiProtocols.resolve("responses");
        ModelApiStreamState state = new ModelApiStreamState();
        StringBuilder reasoning = new StringBuilder();
        ModelClient.StreamCallback callback = new ModelClient.StreamCallback() {
            @Override
            public void onReasoningDelta(String token) {
                reasoning.append(token);
            }
        };

        protocol.processStreamChunk(ONode.ofJson("""
                {"type":"response.output_item.done","output_index":0,"item":{"type":"reasoning",
                 "content":[{"type":"reasoning_text","text":"complete thinking"}]}}
                """), callback, state);

        assertEquals("complete thinking", reasoning.toString());
        assertTrue(state.emittedReasoning);
        assertEquals("complete thinking",
                ONode.ofJson(state.responseReasoning).select("$.content[0].text").getString());
    }

    @Test
    void responsesRequestOmitsOutputOnlyReasoningStatus() {
        ChatMessage assistant = ChatMessage.assistant(null, null, null);
        assistant.setResponseReasoning("""
                {"type":"reasoning","id":"rs_1","status":"completed", "summary":[],
                 "encrypted_content":"encrypted"}
                """);
        ModelApiProtocol.RequestContext context = new ModelApiProtocol.RequestContext(
                "test-model", "none", List.of(assistant), new ONode().asArray(), null, null);

        ONode request = ModelApiProtocols.resolve("responses").buildRequest(context);

        assertEquals("reasoning", request.select("$.input[0].type").getString());
        assertEquals("rs_1", request.select("$.input[0].id").getString());
        assertEquals("encrypted", request.select("$.input[0].encrypted_content").getString());
        assertTrue(request.select("$.input[0].status").isNull());
        assertEquals("auto", request.select("$.reasoning.summary").getString());
        assertEquals("reasoning.encrypted_content", request.select("$.include[0]").getString());
        assertFalse(request.get("include").getArray().isEmpty());
    }

    @Test
    void imageToolResultUsesVisualOutputForBothProtocols() {
        ChatMessage imageToolResult = ChatMessage.toolWithImage("call-image", "图片已读取", "data:image/png;base64,AA==", "high");
        ModelApiProtocol.RequestContext context = new ModelApiProtocol.RequestContext(
                "test-model", "none", List.of(imageToolResult), new ONode().asArray(), null, null);

        ONode chatRequest = ModelApiProtocols.resolve("chat_completions").buildRequest(context);
        assertEquals("tool", chatRequest.select("$.messages[0].role").getString());
        assertEquals("图片已读取", chatRequest.select("$.messages[0].content").getString());
        assertEquals("user", chatRequest.select("$.messages[1].role").getString());
        assertEquals("data:image/png;base64,AA==",
                chatRequest.select("$.messages[1].content[1].image_url.url").getString());

        ONode responsesRequest = ModelApiProtocols.resolve("responses").buildRequest(context);
        assertEquals("function_call_output", responsesRequest.select("$.input[0].type").getString());
        assertEquals("input_text", responsesRequest.select("$.input[0].output[0].type").getString());
        assertEquals("input_image", responsesRequest.select("$.input[0].output[1].type").getString());
        assertEquals("data:image/png;base64,AA==",
                responsesRequest.select("$.input[0].output[1].image_url").getString());
    }

    @Test
    void chatCompletionsStrategyMapsRequestAndResponse() throws Exception {
        ModelApiProtocol protocol = ModelApiProtocols.resolve("chat_completions");
        ONode tools = ONode.ofJson("""
                [{"type":"function","function":{"name":"read","parameters":{"type":"object"}}}]
                """);
        ModelApiProtocol.RequestContext context = new ModelApiProtocol.RequestContext(
                "test-model[128k]", "high", List.of(ChatMessage.ofUser("hello")), tools,
                "user-1", "session-1");

        ONode request = protocol.buildRequest(context);

        assertEquals("test-model", request.get("model").getString());
        assertEquals("hello", request.select("$.messages[0].content").getString());
        assertEquals("read", request.select("$.tools[0].function.name").getString());
        assertEquals("high", request.get("reasoning_effort").getString());
        assertEquals("user-1", request.get("user").getString());
        assertEquals("session-1", request.get("prompt_cache_key").getString());

        ONode message = protocol.parseResponse(ONode.ofJson("""
                {"choices":[{"message":{"role":"assistant","content":"done"}}]}
                """), "response");
        assertEquals("assistant", message.get("role").getString());
        assertEquals("done", message.get("content").getString());
    }
}
