package site.sorghum.loopra.bin.model;

import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;
import site.sorghum.loopra.bin.agent.model.ChatMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
