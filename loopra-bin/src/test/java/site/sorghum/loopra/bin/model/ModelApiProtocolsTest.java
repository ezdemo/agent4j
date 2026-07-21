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
