package site.sorghum.agent4j.bin.agent.core;

import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;
import org.noear.solon.ai.chat.tool.FunctionToolDesc;
import site.sorghum.agent4j.bin.agent.model.HitlState;
import site.sorghum.agent4j.bin.agent.model.ToolExecutionResult;
import site.sorghum.agent4j.bin.agent.hitl.HitlManager;
import site.sorghum.agent4j.bin.tool.ToolRegistry;
import site.sorghum.agent4j.tool.HitlRequiredException;

import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLoopToolSignalTest {

    @Test
    void terminateOnNoToolCallCanBeUpdatedAtRuntime() {
        AgentLoop loop = new AgentLoop(null, registryWith(tool("status", args -> "ok")), null);

        assertTrue(loop.terminateOnNoToolCall());
        loop.setTerminateOnNoToolCall(false);
        assertFalse(loop.terminateOnNoToolCall());
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
