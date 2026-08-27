package site.sorghum.loopra.bin.agent.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import site.sorghum.loopra.bin.agent.output.SubAgentEventRecorder;
import site.sorghum.loopra.bin.model.TestLoopraProvider;
import site.sorghum.loopra.bin.session.SubAgentSessionStore;
import site.sorghum.loopra.bin.tool.ToolRegistry;
import site.sorghum.loopra.tool.AgentOutput;
import site.sorghum.loopra.tool.ChoiceOption;
import site.sorghum.loopra.tool.LogLevel;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 子代理会话持久化集成测试：SubAgent 挂载记录器后，执行过程落盘为子代理会话。
 */
class SubAgentRecorderIntegrationTest {

    @TempDir
    Path tempDir;

    private static TestLoopraProvider recordingProvider() {
        return TestLoopraProvider.builder()
                .model("test-model")
                .stream(request -> TestLoopraProvider.contentStream("done"))
                .build();
    }

    /** 构造带父 controller 的子代理（parentOutput 非空，记录器才会挂载）。 */
    private static SubAgent subAgentWithParent(SubAgentSessionStore store, String parentSession, String subSessionId) {
        AgentLoop parent = new AgentLoop(recordingProvider(), new ToolRegistry(), null, null);
        SubAgent sub = new SubAgent(recordingProvider(), new ToolRegistry(), "system", parent);
        sub.setSessionId(parentSession);
        sub.setRecorder(new SubAgentEventRecorder(store, parentSession, subSessionId));
        return sub;
    }

    @Test
    void subAgentRunPersistsSubSessionWithEvents() throws Exception {
        SubAgentSessionStore store = new SubAgentSessionStore(tempDir);
        String subSessionId = "sub-test-1";
        SubAgent sub = subAgentWithParent(store, "parent-session", subSessionId);

        String result = sub.run("do it", null);
        assertEquals("done", result);

        // 列表元数据：状态/任务/事件数
        List<SubAgentSessionStore.SubSessionInfo> list = store.list("parent-session");
        assertEquals(1, list.size());
        SubAgentSessionStore.SubSessionInfo info = list.get(0);
        assertEquals(subSessionId, info.subSessionId());
        assertEquals("completed", info.status());
        assertEquals("do it", info.task());
        assertTrue(info.startedAt() > 0);
        assertTrue(info.endedAt() >= info.startedAt());
        assertTrue(info.eventCount() >= 2);

        // 事件序列：sub_start 开头、sub_end 结尾，正文完整落盘
        List<Map<String, Object>> events = store.events("parent-session", subSessionId);
        assertFalse(events.isEmpty());
        assertEquals("sub_start", events.get(0).get("type"));
        assertEquals("sub_end", events.get(events.size() - 1).get("type"));
        assertEquals("completed", events.get(events.size() - 1).get("status"));
        assertTrue(events.stream().anyMatch(e -> "sub_content".equals(e.get("type"))
                && "done".equals(e.get("content"))));
    }

    @Test
    void abortedBeforeRunWritesNothing() throws Exception {
        SubAgentSessionStore store = new SubAgentSessionStore(tempDir);
        SubAgent sub = subAgentWithParent(store, "parent-session", "sub-abort");
        sub.abort();

        String result = sub.run("do it", null);
        assertEquals("⏹️ 子代理已取消", result);
        assertTrue(store.list("parent-session").isEmpty());
    }

    @Test
    void withoutParentOutputNothingIsPersisted() throws Exception {
        SubAgentSessionStore store = new SubAgentSessionStore(tempDir);
        // 无父 controller（独立场景）：parentOutput 为 null，记录器不挂载
        SubAgent sub = new SubAgent(recordingProvider(), new ToolRegistry(), "system");
        sub.setSessionId("parent-session");
        sub.setRecorder(new SubAgentEventRecorder(store, "parent-session", "sub-standalone"));

        sub.run("do it", null);
        assertTrue(store.list("parent-session").isEmpty());
    }

    @Test
    void parallelSubAgentsWriteSeparateSessions() throws Exception {
        SubAgentSessionStore store = new SubAgentSessionStore(tempDir);
        SubAgent a = subAgentWithParent(store, "parent-session", "sub-para-a");
        SubAgent b = subAgentWithParent(store, "parent-session", "sub-para-b");

        Thread ta = new Thread(() -> {
            try {
                a.run("task a", null);
            } catch (Exception ignored) {
            }
        });
        Thread tb = new Thread(() -> {
            try {
                b.run("task b", null);
            } catch (Exception ignored) {
            }
        });
        ta.start();
        tb.start();
        ta.join();
        tb.join();

        List<SubAgentSessionStore.SubSessionInfo> list = store.list("parent-session");
        assertEquals(2, list.size());
        assertEquals(1, store.events("parent-session", "sub-para-a").stream()
                .filter(e -> "sub_start".equals(e.get("type"))).count());
        assertEquals(1, store.events("parent-session", "sub-para-b").stream()
                .filter(e -> "sub_start".equals(e.get("type"))).count());
    }

    @Test
    void multiRoundRunAppendsSecondRoundToSameSession() throws Exception {
        SubAgentSessionStore store = new SubAgentSessionStore(tempDir);
        String subSessionId = "sub-rounds";
        SubAgent sub = subAgentWithParent(store, "parent-session", subSessionId);

        // 首次执行
        assertEquals("done", sub.run("first task", null));
        // 继续对话：同一实例复用子循环与上下文，记录器续写新一轮
        assertEquals("done", sub.run("follow up", null));

        List<Map<String, Object>> events = store.events("parent-session", subSessionId);
        // 两轮 sub_start/sub_end 对
        assertEquals(2, events.stream().filter(e -> "sub_start".equals(e.get("type"))).count());
        assertEquals(2, events.stream().filter(e -> "sub_end".equals(e.get("type"))).count());
        // 两轮任务描述都落盘
        assertTrue(events.stream().anyMatch(e -> "sub_start".equals(e.get("type")) && "first task".equals(e.get("task"))));
        assertTrue(events.stream().anyMatch(e -> "sub_start".equals(e.get("type")) && "follow up".equals(e.get("task"))));
        // 每轮正文完整落盘
        assertTrue(events.stream().filter(e -> "sub_content".equals(e.get("type"))).count() >= 2);
        // 末行为最后一轮 sub_end：列表状态 completed
        assertEquals("sub_end", events.get(events.size() - 1).get("type"));
        SubAgentSessionStore.SubSessionInfo info = store.list("parent-session").get(0);
        assertEquals(subSessionId, info.subSessionId());
        assertEquals("completed", info.status());
        assertEquals("first task", info.task());
    }

    @Test
    void outputTargetRedirectsRoundEventsToNewChannel() throws Exception {
        SubAgentSessionStore store = new SubAgentSessionStore(tempDir);
        SubAgent sub = subAgentWithParent(store, "parent-session", "sub-target");
        sub.run("first", null);

        // 继续对话：输出重定向到新通道（模拟 chat 端点的新 SSE）
        List<String> sentTypes = new ArrayList<>();
        sub.setOutputTarget(new CollectingOutput(sentTypes));
        sub.run("second", null);
        sub.setOutputTarget(null);

        // 第二轮生命周期事件发往新通道
        assertTrue(sentTypes.contains("sub_start"));
        assertTrue(sentTypes.contains("sub_end"));
        assertTrue(sentTypes.contains("sub_content"));
        // 记录器仍然续写同一会话
        List<Map<String, Object>> events = store.events("parent-session", "sub-target");
        assertEquals(2, events.stream().filter(e -> "sub_start".equals(e.get("type"))).count());
        assertEquals("sub_end", events.get(events.size() - 1).get("type"));
    }

    /** 收集 sendEvent 类型的事件通道（模拟新的 SSE 输出）。 */
    private static final class CollectingOutput implements AgentOutput {
        private final List<String> types;

        private CollectingOutput(List<String> types) {
            this.types = types;
        }

        @Override
        public void onContentDelta(String token) {
        }

        @Override
        public void onContentComplete() {
        }

        @Override
        public void onReasoningDelta(String token) {
        }

        @Override
        public void onReasoning(String reasoning) {
        }

        @Override
        public void onReasoningStarted() {
        }

        @Override
        public void onToolCall(String name, String args) {
        }

        @Override
        public void onToolResult(String name, String result) {
        }

        @Override
        public void onUsage(int promptTokens, int completionTokens, int totalTokens,
                            int cacheHit, int cacheMiss) {
        }

        @Override
        public void onUsage(String model, int promptTokens, int completionTokens, int totalTokens,
                            int cacheHit, int cacheMiss) {
        }

        @Override
        public void onError(String error) {
        }

        @Override
        public void onLog(LogLevel level, String message) {
        }

        @Override
        public void onChoice(List<ChoiceOption> options) {
        }

        @Override
        public void onChoice(List<ChoiceOption> options, String title, String description) {
        }

        @Override
        public void sendEvent(String type, String data) {
            types.add(type);
        }
    }
}
