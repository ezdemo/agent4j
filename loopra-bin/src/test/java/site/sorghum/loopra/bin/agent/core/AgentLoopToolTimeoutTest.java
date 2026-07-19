package site.sorghum.loopra.bin.agent.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.noear.snack4.ONode;
import org.noear.solon.ai.chat.tool.FunctionToolDesc;
import site.sorghum.loopra.bin.agent.model.ChatMessage;
import site.sorghum.loopra.bin.agent.model.ToolExecutionResult;
import site.sorghum.loopra.bin.config.LoopraConfig;
import site.sorghum.loopra.bin.model.HttpModelClient;
import site.sorghum.loopra.bin.model.ModelClient;
import site.sorghum.loopra.bin.tool.ToolRegistry;
import site.sorghum.loopra.tool.ToolContext;
import site.sorghum.loopra.tool.solon.common.SessionFileChangeTracker;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class AgentLoopToolTimeoutTest {
    @TempDir
    Path workspace;

    @Test
    void subAgentUsesDedicatedTimeoutInsteadOfRegularToolTimeout() throws Exception {
        ToolRegistry registry = registryWith(tool("sub_agent", args -> {
            Thread.sleep(1_200);
            return "completed";
        }));
        AgentLoop loop = loop(registry, 1, 3);

        ToolExecutionResult result = loop.executeToolCalls(toolCalls("sub_agent"));

        assertEquals("completed", result.toolResults().get(0).getContent());
    }

    @Test
    void subAgentTimeoutRunsExplicitCancellation() throws Exception {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        CountDownLatch cancellationObserved = new CountDownLatch(1);
        ToolRegistry registry = registryWith(tool("sub_agent", args -> {
            ToolContext context = (ToolContext) args.get("ctx");
            context.getLoopController().registerToolCancellation(() -> {
                cancelled.set(true);
                cancellationObserved.countDown();
            });
            while (!cancelled.get()) {
                Thread.sleep(20);
            }
            return "cancelled";
        }));
        AgentLoop loop = loop(registry, 5, 1);

        ToolExecutionResult result = loop.executeToolCalls(toolCalls("sub_agent"));

        assertTrue(result.toolResults().get(0).getContent().contains("子代理执行超时（1s）"));
        assertTrue(cancellationObserved.await(1, TimeUnit.SECONDS));
    }

    @Test
    void regularToolKeepsRegularTimeoutAndRunsCancellation() throws Exception {
        CountDownLatch cancellationObserved = new CountDownLatch(1);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        ToolRegistry registry = registryWith(tool("bash", args -> {
            ToolContext context = (ToolContext) args.get("ctx");
            context.getLoopController().registerToolCancellation(() -> {
                cancelled.set(true);
                cancellationObserved.countDown();
            });
            while (!cancelled.get()) {
                Thread.sleep(20);
            }
            return "cancelled";
        }));
        AgentLoop loop = loop(registry, 1, 5);

        ToolExecutionResult result = loop.executeToolCalls(toolCalls("bash"));

        assertTrue(result.toolResults().get(0).getContent().contains("工具执行超时（1s）"));
        assertTrue(cancellationObserved.await(1, TimeUnit.SECONDS));
    }

    @Test
    void httpModelClientForkHasIndependentCallState() {
        HttpModelClient client = new HttpModelClient(
                "http://localhost/v1/chat/completions", "test-key", "test-model", "high");

        ModelClient fork = client.fork();

        assertNotSame(client, fork);
        assertEquals(client.getModel(), fork.getModel());
    }

    @Test
    void subAgentAbortBeforeRunPreventsModelCall() throws Exception {
        BlockingModelClient client = new BlockingModelClient();
        ToolRegistry registry = new ToolRegistry().setDisabledTools(Set.of());
        registry.setRefreshContext(Paths.get(".").toAbsolutePath(), null, null, List.of());
        SubAgent subAgent = new SubAgent(client, registry, "test sub agent");
        subAgent.setConfig(config(5, 5));

        subAgent.abort();
        String result = subAgent.run("wait", null);

        assertEquals("⏹️ 子代理已取消", result);
        assertFalse(client.started.await(100, TimeUnit.MILLISECONDS));
    }

    @Test
    void parentLoopDrainsFileChangesRecordedBySubAgentLoop() throws Exception {
        ToolRegistry registry = new ToolRegistry().setDisabledTools(Set.of());
        registry.setRefreshContext(workspace, null, null, List.of());
        registry.register(tool("write", args -> {
            SessionFileChangeTracker.record("src/Child.java", "before\n", "after\n", false);
            return "written";
        }));
        registry.register(tool("read", args -> "read"));
        AgentLoop subLoop = loop(registry, 5, 5);
        AgentLoop parentLoop = loop(registry, 5, 5);
        subLoop.setSessionId("session-a");
        subLoop.setDrainFileChanges(false);
        parentLoop.setSessionId("session-a");
        SessionFileChangeTracker.beginTurn(workspace, "session-a");

        assertTrue(subLoop.executeToolCalls(toolCalls("write")).fileChanges().isEmpty());
        List<site.sorghum.loopra.bin.agent.model.FileChange> changes =
                parentLoop.executeToolCalls(toolCalls("read")).fileChanges();

        assertEquals(1, changes.size());
        assertEquals("src/Child.java", changes.get(0).path());
    }

    private static AgentLoop loop(ToolRegistry registry, int toolTimeoutSec,
                                  int subAgentTimeoutSec) throws Exception {
        return new AgentLoop(null, registry, null, "free",
                config(toolTimeoutSec, subAgentTimeoutSec));
    }

    private static LoopraConfig config(int toolTimeoutSec,
                                        int subAgentTimeoutSec) throws Exception {
        String json = "{\"toolTimeoutSec\":" + toolTimeoutSec
                + ",\"subAgentTimeoutSec\":" + subAgentTimeoutSec + "}";
        Constructor<LoopraConfig> constructor =
                LoopraConfig.class.getDeclaredConstructor(ONode.class);
        constructor.setAccessible(true);
        return constructor.newInstance(ONode.ofJson(json));
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

    private static ONode toolCalls(String name) {
        ONode calls = new ONode().asArray();
        calls.addNew().then(call -> {
            call.set("id", "call-1");
            call.set("index", "0");
            call.getOrNew("function").then(function -> {
                function.set("name", name);
                function.set("arguments", "{}");
            });
        });
        return calls;
    }

    private static final class BlockingModelClient implements ModelClient {
        private final CountDownLatch started = new CountDownLatch(1);
        private final AtomicBoolean aborted = new AtomicBoolean(false);

        @Override
        public ONode chat(List<ChatMessage> messages, ONode tools) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void chatStream(List<ChatMessage> messages, ONode tools, StreamCallback callback) {
            started.countDown();
            try {
                while (!aborted.get()) {
                    Thread.sleep(20);
                }
                callback.onError("aborted");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                callback.onError("interrupted");
            }
        }

        @Override
        public String getModel() {
            return "blocking-test-model";
        }

        @Override
        public void setModel(String model) {
        }

        @Override
        public void abortStream() {
            aborted.set(true);
        }
    }
}
