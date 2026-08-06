package site.sorghum.loopra.bin.agent.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.noear.snack4.ONode;
import org.noear.solon.ai.chat.tool.FunctionToolDesc;
import site.sorghum.loopra.bin.agent.model.LoopraChatMessage;
import site.sorghum.loopra.bin.agent.model.ToolExecutionResult;
import site.sorghum.loopra.bin.agent.spi.AgentConfig;
import site.sorghum.loopra.bin.model.HttpModelClient;
import site.sorghum.loopra.bin.model.ModelClient;
import site.sorghum.loopra.bin.tool.ToolRegistry;
import site.sorghum.loopra.tool.ToolContext;
import site.sorghum.loopra.bin.session.SessionFileChangeTracker;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
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
    void userAbortInterruptsRunningTool() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        ToolRegistry registry = registryWith(tool("blocking", args -> {
            started.countDown();
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                interrupted.countDown();
            }
            return "stopped";
        }));
        AgentLoop loop = loop(registry, 60, 60);

        CompletableFuture<ToolExecutionResult> execution = CompletableFuture.supplyAsync(
                () -> loop.executeToolCalls(toolCalls("blocking")));
        assertTrue(started.await(1, TimeUnit.SECONDS));

        loop.requestUserAbort();

        execution.get(1, TimeUnit.SECONDS);
        assertTrue(interrupted.await(1, TimeUnit.SECONDS),
                "取消 Future 时必须中断正在运行的工具线程");
    }

    @Test
    void userAbortCancelsDetachedResourcesAcrossRegistrationRace() throws Exception {
        AgentLoop loop = loop(registryWith(tool("noop", args -> "done")), 5, 5);
        CountDownLatch existingResourceCancelled = new CountDownLatch(1);
        CountDownLatch lateResourceCancelled = new CountDownLatch(1);
        loop.registerAbortResource("existing", existingResourceCancelled::countDown);

        loop.requestUserAbort();
        loop.registerAbortResource("late", lateResourceCancelled::countDown);

        assertTrue(existingResourceCancelled.await(1, TimeUnit.SECONDS));
        assertTrue(lateResourceCancelled.await(1, TimeUnit.SECONDS));
    }

    @Test
    void httpModelClientForkHasIndependentCallState() {
        HttpModelClient client = new HttpModelClient(
                "http://localhost/v1/chat/completions", "test-key", "test-model", "high", "test-channel");

        ModelClient fork = client.fork();

        assertNotSame(client, fork);
        assertEquals(client.getModel(), fork.getModel());
        assertEquals("test-channel", fork.getModelChannelId());
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
        return new AgentLoop(new BlockingModelClient(), registry, null, "free",
                config(toolTimeoutSec, subAgentTimeoutSec));
    }

    private static AgentConfig config(int toolTimeoutSec,
                                        int subAgentTimeoutSec) {
        return new TimeoutConfig(toolTimeoutSec, subAgentTimeoutSec);
    }

    /** AgentConfig 测试替身：仅覆写工具超时，其余返回内核默认值。 */
    private record TimeoutConfig(int toolTimeoutSec, int subAgentTimeoutSec) implements AgentConfig {
        @Override public int maxContextChars() { return 200_000; }
        @Override public int keepTailChars() { return 80_000; }
        @Override public int maxSelfCorrectionAttempts() { return 5; }
        @Override public boolean terminateOnNoToolCall() { return true; }
        @Override public int stormWindowSize() { return 6; }
        @Override public int stormThreshold() { return 3; }
        @Override public String validationModel() { return ""; }
        @Override public Channel validationModelChannel() { return null; }
        @Override public List<String> autoWhitelist() { return List.of(); }
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
        public ONode chat(List<LoopraChatMessage> messages, ONode tools) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void chatStream(List<LoopraChatMessage> messages, ONode tools, StreamCallback callback) {
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
