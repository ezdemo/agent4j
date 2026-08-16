package site.sorghum.loopra.integration.cutin;

import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;
import org.noear.solon.ai.chat.tool.FunctionTool;
import site.sorghum.cutin.core.context.Budget;
import site.sorghum.cutin.core.context.DefaultLoopContext;
import site.sorghum.cutin.core.context.Message;
import site.sorghum.cutin.core.loop.DefaultLoopEngine;
import site.sorghum.cutin.core.loop.LoopHandle;
import site.sorghum.cutin.core.loop.LoopProgram;
import site.sorghum.cutin.core.loop.LoopResult;
import site.sorghum.loopra.bin.model.TestLoopraProvider;
import site.sorghum.loopra.bin.tool.ToolRegistry;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CutinLoopraBridgeTest {

    @Test
    void loopraToolsAndModelProviderRunThroughCutinEngine() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(echoTool());

        AtomicInteger calls = new AtomicInteger();
        TestLoopraProvider provider = TestLoopraProvider.builder()
                .model("stub")
                .call(request -> {
                    if (calls.getAndIncrement() == 0) {
                        return TestLoopraProvider.toolCallsResponse(toolCalls());
                    }
                    return TestLoopraProvider.response("echo: hello");
                })
                .build();
        DefaultLoopEngine engine = CutinLoopraBridge.newEngine(provider, registry);

        LoopProgram program = CutinSteps.codingProgram("stub");
        DefaultLoopContext context = engine.newContext(
            "loopra-cutin-test",
            List.of(new Message("user", "say hello")),
            Map.of(),
            Budget.unlimited()
        );
        LoopHandle handle = engine.run(program, context);
        LoopResult result = handle.result().join();

        assertEquals(LoopResult.Status.COMPLETED, result.status());
        assertTrue(calls.get() >= 2, "model must be called twice");

        List<Message> messages = result.finalSnapshot().messages();
        assertTrue(
            messages.stream().anyMatch(message ->
                "tool".equals(message.role()) && message.content().contains("echo: hello")
            ),
            "tool result should be visible in the cutin context"
        );
        assertTrue(
            messages.stream().anyMatch(message ->
                "assistant".equals(message.role()) && "echo: hello".equals(message.content())
            ),
            "final assistant answer should be visible in the cutin context"
        );
    }

    private static FunctionTool echoTool() {
        return new FunctionTool() {
            @Override
            public String name() {
                return "echo";
            }

            @Override
            public String title() {
                return "echo";
            }

            @Override
            public String description() {
                return "Echo the given text back.";
            }

            @Override
            public boolean returnDirect() {
                return false;
            }

            @Override
            public String inputSchema() {
                return "{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}},\"required\":[\"text\"]}";
            }

            @Override
            public Type returnType() {
                return String.class;
            }

            @Override
            public Object handle(Map<String, Object> args) {
                return "echo: " + args.get("text");
            }
        };
    }

    private static ONode toolCalls() {
        ONode message = ONode.ofJson("{}").asObject();
        ONode call = message.getOrNew("tool_calls").asArray().addNew();
        call.set("id", "call_1");
        call.set("type", "function");
        ONode function = call.getOrNew("function");
        function.set("name", "echo");
        function.set("arguments", "{\"text\":\"hello\"}");
        return message.get("tool_calls");
    }
}
