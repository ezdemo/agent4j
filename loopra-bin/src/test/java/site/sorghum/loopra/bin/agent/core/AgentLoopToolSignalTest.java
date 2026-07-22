package site.sorghum.loopra.bin.agent.core;

import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;
import org.noear.solon.ai.chat.tool.FunctionToolDesc;
import site.sorghum.loopra.bin.agent.hitl.HitlManager;
import site.sorghum.loopra.bin.agent.model.ChatMessage;
import site.sorghum.loopra.bin.agent.model.HitlState;
import site.sorghum.loopra.bin.agent.model.ToolExecutionResult;
import site.sorghum.loopra.bin.model.ModelClient;
import site.sorghum.loopra.bin.tool.ToolRegistry;
import site.sorghum.loopra.tool.HitlRequiredException;
import site.sorghum.loopra.tool.ToolContext;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AgentLoopToolSignalTest {

    @Test
    void terminateOnNoToolCallCanBeUpdatedAtRuntime() {
        AgentLoop loop = new AgentLoop(null, registryWith(tool("status", args -> "ok")), null);

        assertTrue(loop.terminateOnNoToolCall());
        loop.setTerminateOnNoToolCall(false);
        assertFalse(loop.terminateOnNoToolCall());
    }

    @Test
    void resetUserAbortAlsoClearsModelStreamAbort() {
        AtomicInteger resets = new AtomicInteger();
        ModelClient client = new ModelClient() {
            @Override
            public ONode chat(List<ChatMessage> messages, ONode tools) {
                return null;
            }

            @Override
            public void chatStream(List<ChatMessage> messages, ONode tools, StreamCallback callback) {
            }

            @Override
            public String getModel() {
                return "test";
            }

            @Override
            public void setModel(String model) {
            }

            @Override
            public void resetStreamAbort() {
                resets.incrementAndGet();
            }
        };
        AgentLoop loop = new AgentLoop(client, registryWith(tool("status", args -> "ok")), null);

        loop.resetUserAbort();

        assertEquals(1, resets.get());
    }

    @Test
    void copiedRegistryPreservesWorkspaceForToolCwd() {
        Path workspace = Paths.get(".").toAbsolutePath().normalize();
        AtomicReference<Map<String, Object>> capturedArgs = new AtomicReference<>();
        ToolRegistry parentRegistry = registryWith(tool("workspace", args -> {
            capturedArgs.set(args);
            return "ok";
        }));
        AgentLoop childLoop = new AgentLoop(null, parentRegistry.copy(), null);

        childLoop.executeToolCalls(toolCalls("workspace", "{}"));

        assertEquals(workspace.toString(), capturedArgs.get().get("__cwd"));
        ToolContext context = (ToolContext) capturedArgs.get().get("ctx");
        assertEquals(workspace, context.getRootDir().toAbsolutePath().normalize());
    }

    @Test
    void repeatedToolCallSetsSuppressionSignalAndSkipsExecution() {
        AtomicInteger executions = new AtomicInteger();
        ToolRegistry registry = registryWith(tool("edit", args -> {
            executions.incrementAndGet();
            return "ok";
        }));
        AgentLoop loop = new AgentLoop(null, registry, null);
        ONode calls = toolCalls("edit", "{\"path\":\"a.java\"}");

        assertFalse(loop.executeToolCalls(calls).anySuppressed());
        assertFalse(loop.executeToolCalls(calls).anySuppressed());
        ToolExecutionResult third = loop.executeToolCalls(calls);

        assertTrue(third.anySuppressed());
        assertEquals(2, executions.get());
        assertTrue(third.toolResults().get(0).getContent().contains("\"rejectedReason\":\"storm\""));
    }

    @Test
    void hitlExceptionSetsSandboxPendingSignalAndPreservesDetails() {
        ToolRegistry registry = registryWith(tool("write", args -> {
            throw new HitlRequiredException(
                    "write", "SANDBOX_ESCAPE", "outside workspace", Map.of("path", "../secret"));
        }));
        AgentLoop loop = new AgentLoop(null, registry, null);

        ToolExecutionResult result = loop.executeToolCalls(
                toolCalls("write", "{\"path\":\"../secret\"}"));

        assertFalse(result.anySuppressed());
        assertEquals(HitlState.PENDING, loop.getHitlManager().getState());
        assertTrue(loop.getHitlManager().hasSandboxPending());
        assertTrue(result.toolResults().get(0).getContent().contains("HITL_PENDING:SANDBOX_ESCAPE"));

        HitlManager.PendingSandboxState pending = loop.getHitlManager().drainSandboxHITL();
        assertEquals("outside workspace", pending.details());
    }

    @Test
    void bashWaitIsNeverSuppressed() {
        AtomicInteger executions = new AtomicInteger();
        AgentLoop loop = new AgentLoop(null, registryWith(tool("bash_wait", args -> {
            executions.incrementAndGet();
            return "waiting";
        })), null);
        ONode calls = toolCalls("bash_wait", "{\"session_id\":\"session-1\",\"yield_time_ms\":1000}");

        assertFalse(loop.executeToolCalls(calls).anySuppressed());
        assertFalse(loop.executeToolCalls(calls).anySuppressed());
        assertFalse(loop.executeToolCalls(calls).anySuppressed());
        assertEquals(3, executions.get());
    }

    @Test
    void stormExemptToolIsNeverSuppressed() {
        AtomicInteger executions = new AtomicInteger();
        FunctionToolDesc tool = tool("status", args -> {
            executions.incrementAndGet();
            return "ok";
        });
        tool.metaPut("stormExempt", true);
        AgentLoop loop = new AgentLoop(null, registryWith(tool), null);
        ONode calls = toolCalls("status", "{}");

        assertFalse(loop.executeToolCalls(calls).anySuppressed());
        assertFalse(loop.executeToolCalls(calls).anySuppressed());
        assertFalse(loop.executeToolCalls(calls).anySuppressed());
        assertEquals(3, executions.get());
    }

    private static ToolRegistry registryWith(FunctionToolDesc tool) {
        ToolRegistry registry = new ToolRegistry().setDisabledTools(Set.of());
        registry.setRefreshContext(Paths.get(".").toAbsolutePath(), null, null, java.util.List.of());
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
