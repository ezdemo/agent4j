package site.sorghum.loopra.bin.agent.core;

import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;
import org.noear.solon.ai.chat.tool.FunctionToolDesc;
import site.sorghum.loopra.bin.agent.context.ConversationContext;
import site.sorghum.loopra.bin.agent.model.ChatMessage;
import site.sorghum.loopra.bin.agent.model.UserMessage;
import site.sorghum.loopra.bin.agent.prompt.PromptPrefix;
import site.sorghum.loopra.bin.model.ModelClient;
import site.sorghum.loopra.bin.tool.ToolRegistry;
import site.sorghum.loopra.tool.AgentOutput;

import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ToolCallValidatorTest {

    @Test
    void aiApprovalExecutesWithoutOpeningManualHitl() throws Exception {
        AtomicInteger validations = new AtomicInteger();
        AtomicInteger executions = new AtomicInteger();
        AgentLoop loop = loop("approval", toolCallingModel(toolCalls("bash", "{\"command\":\"mvn test\"}")),
                registryWith(tool("bash", args -> {
                    executions.incrementAndGet();
                    return "executed";
                })), ToolCallValidator.forClient(reply(validations,
                        "```json\n{\"allow\":true,\"requiresHuman\":false,\"reason\":\"local test\"}\n```"), Paths.get(".")));

        String result = loop.run(UserMessage.of("run tests"));

        assertEquals("done", result);
        assertEquals(1, validations.get());
        assertEquals(1, executions.get());
        assertFalse(loop.getHitlManager().hasPendingHITL());
    }

    @Test
    void aiDenialBehavesLikeHumanDenial() throws Exception {
        AtomicInteger validations = new AtomicInteger();
        AtomicInteger executions = new AtomicInteger();
        AgentLoop loop = loop("approval", toolCallingModel(toolCalls("bash", "{\"command\":\"rm -rf /\"}")),
                registryWith(tool("bash", args -> {
                    executions.incrementAndGet();
                    return "executed";
                })), ToolCallValidator.forClient(reply(validations,
                        "{\"allow\":false,\"requiresHuman\":false,\"reason\":\"destructive command\"}"), Paths.get(".")));

        String result = loop.run(UserMessage.of("delete files"));

        assertEquals(1, validations.get());
        assertEquals(0, executions.get());
        assertTrue(result.contains("未通过 AI 审批"));
        assertTrue(result.contains("destructive command"));
        assertFalse(loop.getHitlManager().hasPendingHITL());
    }

    @Test
    void freeModeDoesNotInvokeAiApproval() throws Exception {
        AtomicInteger validations = new AtomicInteger();
        AtomicInteger executions = new AtomicInteger();
        AgentLoop loop = loop("free", toolCallingModel(toolCalls("bash", "{}")),
                registryWith(tool("bash", args -> {
                    executions.incrementAndGet();
                    return "executed";
                })), ToolCallValidator.forClient(reply(validations,
                        "{\"allow\":false,\"requiresHuman\":false,\"reason\":\"should not be used\"}"), Paths.get(".")));

        String result = loop.run(UserMessage.of("run"));

        assertEquals("done", result);
        assertEquals(0, validations.get());
        assertEquals(1, executions.get());
    }

    @Test
    void sandboxEscapeIsDeferredToMandatoryHumanApproval() throws Exception {
        AtomicInteger validations = new AtomicInteger();
        AtomicInteger executions = new AtomicInteger();
        AgentLoop loop = loop("approval", toolCallingModel(toolCalls("write", "{\"path\":\"../secret\"}")),
                registryWith(tool("write", args -> {
                    executions.incrementAndGet();
                    return "executed";
                })), ToolCallValidator.forClient(reply(validations,
                        "{\"allow\":false,\"requiresHuman\":true,\"reason\":\"outside workspace\"}"), Paths.get(".")));

        String result = loop.run(UserMessage.of("write outside workspace"));

        assertEquals(1, validations.get());
        assertEquals(0, executions.get());
        assertTrue(result.contains("outside workspace"));
        assertTrue(loop.getHitlManager().hasPendingHITL());
        assertTrue(loop.getHitlManager().hasSandboxPending());
    }

    @Test
    void missingValidationModelKeepsManualHitl() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        AgentLoop loop = loop("approval", toolCallingModel(toolCalls("bash", "{}")),
                registryWith(tool("bash", args -> {
                    executions.incrementAndGet();
                    return "executed";
                })), ToolCallValidator.fromConfig(null, Paths.get(".")));

        String result = loop.run(UserMessage.of("run"));

        assertTrue(result.contains("HITL"));
        assertTrue(loop.getHitlManager().hasPendingHITL());
        assertEquals(0, executions.get());
    }

    @Test
    void malformedValidatorResponseFailsClosed() {
        AgentLoop loop = loop("approval", null, registryWith(tool("bash", args -> "executed")),
                ToolCallValidator.forClient(reply("probably safe"), Paths.get(".")));

        ToolCallValidator.Decision decision = loop.validateHITLToolCalls(toolCalls("bash", "{}"));

        assertFalse(decision.allowed());
        assertTrue(decision.reason().contains("无法解析校验模型结果"));
    }

    @Test
    void nonBooleanAllowValueFailsClosed() {
        AgentLoop loop = loop("approval", null, registryWith(tool("bash", args -> "executed")),
                ToolCallValidator.forClient(reply("{\"allow\":\"true\",\"requiresHuman\":false,\"reason\":\"wrong type\"}"), Paths.get(".")));

        ToolCallValidator.Decision decision = loop.validateHITLToolCalls(toolCalls("bash", "{}"));

        assertFalse(decision.allowed());
        assertTrue(decision.reason().contains("allow 字段不是布尔值"));
    }

    private static AgentLoop loop(String hitlMode, ModelClient mainClient, ToolRegistry registry,
                                  ToolCallValidator validator) {
        ConversationContext context = new ConversationContext(
                new PromptPrefix("test", new ONode().asArray()));
        AgentLoop loop = new AgentLoop(mainClient, registry, context, hitlMode, null, validator);
        loop.setOutput(AgentOutput.NOOP);
        return loop;
    }

    private static ModelClient toolCallingModel(ONode toolCalls) {
        AtomicInteger streams = new AtomicInteger();
        return new ModelClient() {
            @Override
            public ONode chat(List<ChatMessage> messages, ONode tools) {
                return null;
            }

            @Override
            public void chatStream(List<ChatMessage> messages, ONode tools, StreamCallback callback) {
                if (streams.getAndIncrement() == 0) {
                    callback.onToolCalls(toolCalls);
                } else {
                    callback.onContentDelta("done");
                }
                callback.onDone();
            }

            @Override
            public String getModel() {
                return "main";
            }

            @Override
            public void setModel(String model) {
            }
        };
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
        ToolRegistry registry = new ToolRegistry() {
            @Override
            public void refresh() {
                // Unit tests register tools directly and do not boot the Solon container.
            }
        };
        registry.setDisabledTools(Set.of());
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
