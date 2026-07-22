package site.sorghum.loopra.bin.agent.core;

import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;
import org.noear.solon.ai.chat.tool.FunctionToolDesc;
import site.sorghum.loopra.bin.agent.model.ChatMessage;
import site.sorghum.loopra.bin.agent.model.ToolExecutionResult;
import site.sorghum.loopra.bin.model.ModelClient;
import site.sorghum.loopra.bin.tool.ToolRegistry;

import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ToolCallValidatorTest {

    @Test
    void dangerousDecisionRejectsToolBeforeExecution() {
        AtomicInteger executions = new AtomicInteger();
        ToolRegistry registry = registryWith(tool("bash", args -> {
            executions.incrementAndGet();
            return "executed";
        }));
        ToolCallValidator validator = ToolCallValidator.forClient(
                reply("{\"allow\":false,\"reason\":\"destructive command\"}"), Paths.get("."));
        AgentLoop loop = new AgentLoop(null, registry, null, "free", null, validator);

        ToolExecutionResult result = loop.executeToolCalls(toolCalls("bash", "{\"command\":\"rm -rf /\"}"));

        assertEquals(0, executions.get());
        assertTrue(result.toolResults().get(0).getContent().contains("\"rejectedReason\":\"validation\""));
        assertTrue(result.toolResults().get(0).getContent().contains("destructive command"));
    }

    @Test
    void safeDecisionAllowsToolExecution() {
        AtomicInteger executions = new AtomicInteger();
        ToolRegistry registry = registryWith(tool("bash", args -> {
            executions.incrementAndGet();
            return "executed";
        }));
        ToolCallValidator validator = ToolCallValidator.forClient(
                reply("```json\n{\"allow\":true,\"reason\":\"local test\"}\n```"), Paths.get("."));
        AgentLoop loop = new AgentLoop(null, registry, null, "free", null, validator);

        ToolExecutionResult result = loop.executeToolCalls(toolCalls("bash", "{\"command\":\"mvn test\"}"));

        assertEquals(1, executions.get());
        assertEquals("executed", result.toolResults().get(0).getContent());
    }

    @Test
    void malformedValidatorResponseFailsClosed() {
        AtomicInteger executions = new AtomicInteger();
        ToolRegistry registry = registryWith(tool("bash", args -> {
            executions.incrementAndGet();
            return "executed";
        }));
        AgentLoop loop = new AgentLoop(null, registry, null, "free", null,
                ToolCallValidator.forClient(reply("probably safe"), Paths.get(".")));

        ToolExecutionResult result = loop.executeToolCalls(toolCalls("bash", "{}"));

        assertEquals(0, executions.get());
        assertTrue(result.toolResults().get(0).getContent().contains("无法解析校验模型结果"));
    }

    @Test
    void nonBooleanAllowValueFailsClosed() {
        AtomicInteger executions = new AtomicInteger();
        ToolRegistry registry = registryWith(tool("bash", args -> {
            executions.incrementAndGet();
            return "executed";
        }));
        AgentLoop loop = new AgentLoop(null, registry, null, "free", null,
                ToolCallValidator.forClient(reply("{\"allow\":\"true\",\"reason\":\"wrong type\"}"), Paths.get(".")));

        ToolExecutionResult result = loop.executeToolCalls(toolCalls("bash", "{}"));

        assertEquals(0, executions.get());
        assertTrue(result.toolResults().get(0).getContent().contains("allow 字段不是布尔值"));
    }

    @Test
    void writeToolIsNotExemptFromValidation() {
        AtomicInteger executions = new AtomicInteger();
        ToolRegistry registry = registryWith(tool("write", args -> {
            executions.incrementAndGet();
            return "executed";
        }));
        AgentLoop loop = new AgentLoop(null, registry, null, "free", null,
                ToolCallValidator.forClient(reply("{\"allow\":false,\"reason\":\"unsafe write\"}"), Paths.get(".")));

        ToolExecutionResult result = loop.executeToolCalls(toolCalls("write", "{\"path\":\"outside\"}"));

        assertEquals(0, executions.get());
        assertTrue(result.toolResults().get(0).getContent().contains("unsafe write"));
    }

    @Test
    void readOnlyToolDoesNotCallValidator() {
        AtomicInteger validations = new AtomicInteger();
        AtomicInteger executions = new AtomicInteger();
        FunctionToolDesc tool = tool("read", args -> {
            executions.incrementAndGet();
            return "content";
        });
        tool.metaPut("readOnly", true);
        ToolCallValidator validator = ToolCallValidator.forClient(reply(validations,
                "{\"allow\":false,\"reason\":\"should not be used\"}"), Paths.get("."));
        AgentLoop loop = new AgentLoop(null, registryWith(tool), null, "free", null, validator);

        loop.executeToolCalls(toolCalls("read", "{}"));

        assertEquals(0, validations.get());
        assertEquals(1, executions.get());
    }

    private static ModelClient reply(String content) {
        return reply(new AtomicInteger(), content);
    }

    private static ModelClient reply(AtomicInteger calls, String content) {
        return new ModelClient() {
            @Override
            public ONode chat(List<ChatMessage> messages, ONode tools) {
                calls.incrementAndGet();
                ONode response = new ONode().asObject();
                response.set("content", content);
                return response;
            }

            @Override
            public void chatStream(List<ChatMessage> messages, ONode tools, StreamCallback callback) {
            }

            @Override
            public String getModel() {
                return "validator";
            }

            @Override
            public void setModel(String model) {
            }
        };
    }

    private static ToolRegistry registryWith(FunctionToolDesc tool) {
        ToolRegistry registry = new ToolRegistry().setDisabledTools(Set.of());
        registry.setRefreshContext(Paths.get(".").toAbsolutePath(), null, null, List.of());
        registry.register(tool);
        return registry;
    }

    private static FunctionToolDesc tool(String name, org.noear.solon.ai.chat.tool.ToolHandler handler) {
        return new FunctionToolDesc(name)
                .description(name)
                .inputSchema("{\"type\":\"object\"}")
                .returnType(String.class)
                .doHandle(handler);
    }

    private static ONode toolCalls(String name, String arguments) {
        ONode calls = new ONode().asArray();
        calls.addNew().then(call -> {
            call.set("id", "call-1");
            call.set("index", "0");
            call.getOrNew("function").then(function -> {
                function.set("name", name);
                function.set("arguments", arguments);
            });
        });
        return calls;
    }
}
