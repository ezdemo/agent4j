package site.sorghum.loopra.bin.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.noear.snack4.ONode;
import site.sorghum.loopra.bin.agent.context.CompactionRangeSelector;
import site.sorghum.loopra.bin.agent.context.ContextFolding;
import site.sorghum.loopra.bin.agent.memory.ProjectMemoryStore;
import site.sorghum.loopra.bin.agent.model.ChatMessage;
import site.sorghum.loopra.bin.agent.model.ToolCallEntry;
import site.sorghum.loopra.bin.model.TestLoopraProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ContextFoldingTest {

    @Test
    void estimateCharsSingleMessage() {
        ChatMessage msg = ChatMessage.ofUser("hello world");
        int n = ContextFolding.estimateChars(msg);
        assertEquals(4 + 11, n, "role=4 chars + content=11 chars");
    }

    @Test
    void estimateCharsMultipleMessages() {
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ChatMessage.ofUser("hi"));
        msgs.add(ChatMessage.assistant("hello", null, null));
        int n = ContextFolding.estimateChars(msgs);
        assertTrue(n > 0);
    }

    @Test
    void estimateCharsWithReasoning() {
        ChatMessage msg = ChatMessage.assistant("ok", null, "thinking...");
        int n = ContextFolding.estimateChars(msg);
        assertTrue(n > 4 + 2, "reasoning_content 也应计入字符数");
    }

    @Test
    void foldReturnsOriginalIfUnderThreshold() throws Exception {
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ChatMessage.ofUser("short"));
        List<ChatMessage> result = ContextFolding.fold(msgs, 1000, 500, null);
        assertSame(msgs, result, "未超阈值应返回原列表");
    }

    /**
     * mock LoopraModelProvider：返回固定双段格式（会话摘要 + 长期记忆），
     * 用于验证 foldKeepLast 的"提炼 + 沉淀"行为。
     */
    private static TestLoopraProvider mockClientReturning(String fullText) {
        return TestLoopraProvider.builder()
                .model("mock")
                .call(request -> TestLoopraProvider.response(fullText))
                .build();
    }

    /**
     * 构造超过 keepCount 的消息列表：1 条 system + N 条 user/assistant 交替。
     */
    private static List<ChatMessage> buildLongHistory(int rounds) {
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ChatMessage.ofSystem("system prompt"));
        for (int i = 0; i < rounds; i++) {
            msgs.add(ChatMessage.ofUser("用户第" + i + "轮：请帮我改 AgentLoop"));
            msgs.add(ChatMessage.assistant("已修改第" + i + "轮", null, null));
        }
        return msgs;
    }

    @Test
    void foldKeepLastPreservesWebHiddenOnRetainedMessages(@TempDir Path dir) throws Exception {
        List<ChatMessage> msgs = buildLongHistory(30);
        ChatMessage hidden = msgs.get(msgs.size() - 2);
        hidden.setWebHidden(true);

        List<ChatMessage> folded = ContextFolding.foldKeepLast(
                msgs, 20, mockClientReturning("<摘要>摘要</摘要>"), dir);

        ChatMessage retained = folded.stream()
                .filter(message -> hidden.getContent().equals(message.getContent()))
                .findFirst()
                .orElseThrow();
        assertTrue(retained.isWebHidden());
    }

    @Test
    void foldKeepLastWithWorkspacePersistsMemoryAndCompressesHistory(@TempDir Path dir) throws Exception {
        String llmOutput = """
                <摘要>用户在给 Loopra 加记忆系统，已改 AgentLoop 和 ContextFolding，尚未写测试。</摘要>
                <<<MEMORY>>>
                <记忆>
                - 架构约定：AgentLoop 通过 workspace 字段传递项目给 ContextFolding
                - 改动 ContextFolding 后必须跑 ContextFoldingTest
                </记忆>""";
        TestLoopraProvider client = mockClientReturning(llmOutput);
        List<ChatMessage> msgs = buildLongHistory(30); // 60 条历史 + 1 system

        List<ChatMessage> folded = ContextFolding.foldKeepLast(msgs, 20, client, dir);

        // 1. 历史被压缩（含一条摘要 + 近 20 条尾部）
        assertTrue(folded.size() < msgs.size() - 1, "折叠后消息数应显著减少");
        assertTrue(folded.get(0).getContent().contains("历史上下文折叠"),
                "首条应为折叠摘要");

        // 2. 长期记忆已沉淀到记忆文件
        Path memFile = ProjectMemoryStore.memoryFilePath(dir);
        assertTrue(Files.exists(memFile), "记忆文件应被创建");
        String persisted = Files.readString(memFile);
        assertTrue(persisted.contains("AgentLoop 通过 workspace 字段"),
                "长期记忆要点应被沉淀");
        assertFalse(persisted.contains("用户在给 Loopra 加记忆系统"),
                "会话摘要不应进入记忆文件");
    }

    @Test
    void foldKeepLastWithNullWorkspaceSkipsMemoryPersistence(@TempDir Path dir) throws Exception {
        String llmOutput = """
                <摘要>摘要内容</摘要>
                <<<MEMORY>>>
                <记忆>
                - 应被沉淀的记忆
                </记忆>""";
        TestLoopraProvider client = mockClientReturning(llmOutput);
        List<ChatMessage> msgs = buildLongHistory(30);

        ContextFolding.foldKeepLast(msgs, 20, client, null);

        Path memFile = ProjectMemoryStore.memoryFilePath(dir);
        assertFalse(Files.exists(memFile), "workspace=null 时不应写记忆文件");
    }

    @Test
    void foldKeepLastDegradesWhenNoMemorySection(@TempDir Path dir) throws Exception {
        // LLM 未按格式输出，无 <<<MEMORY>>> 分隔 —— 降级：整段当摘要，不沉淀
        TestLoopraProvider client = mockClientReturning("只是一段普通摘要，没有记忆分隔标记。");
        List<ChatMessage> msgs = buildLongHistory(30);

        List<ChatMessage> folded = ContextFolding.foldKeepLast(msgs, 20, client, dir);

        assertTrue(folded.size() < msgs.size() - 1, "仍应完成历史压缩");
        Path memFile = ProjectMemoryStore.memoryFilePath(dir);
        assertFalse(Files.exists(memFile), "格式不符时不应沉淀记忆");
    }

    @Test
    void foldRangeReplacesSelectedRangeAndKeepsRecentTail() throws Exception {
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ChatMessage.ofSystem("system prompt"));
        for (int i = 0; i < 5; i++) {
            msgs.add(ChatMessage.ofUser("old message " + i + " " + "x".repeat(200)));
        }

        List<ChatMessage> folded = ContextFolding.foldRange(
                msgs,
                new CompactionRangeSelector.Selection(0, 2, 3),
                mockClientReturning("<摘要>早期任务摘要</摘要>"),
                null);

        assertEquals(3, folded.size());
        assertTrue(folded.get(0).getContent().contains("[历史上下文折叠"));
        assertTrue(folded.get(1).getContent().contains("old message 3"));
        assertTrue(folded.get(2).getContent().contains("old message 4"));
    }

    @Test
    void foldRangeKeepsHistoryBeforeSelectedStart() throws Exception {
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ChatMessage.ofSystem("system prompt"));
        msgs.add(ChatMessage.ofUser("prefix retained"));
        for (int i = 0; i < 4; i++) {
            msgs.add(ChatMessage.ofUser("middle " + i + " " + "x".repeat(200)));
        }

        List<ChatMessage> folded = ContextFolding.foldRange(
                msgs,
                new CompactionRangeSelector.Selection(1, 2, 3),
                mockClientReturning("<摘要>中间摘要</摘要>"),
                null);

        assertEquals(4, folded.size());
        assertTrue(folded.get(0).getContent().contains("prefix retained"));
        assertTrue(folded.get(1).getContent().contains("[历史上下文折叠"));
        assertTrue(folded.get(2).getContent().contains("middle 2"));
        assertTrue(folded.get(3).getContent().contains("middle 3"));
    }

    @Test
    void foldRangeDoesNotLeaveOrphanToolResultInTail() throws Exception {
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ChatMessage.ofSystem("system prompt"));
        msgs.add(ChatMessage.ofUser("first " + "x".repeat(200)));
        msgs.add(ChatMessage.assistant("calling",
                List.of(new ToolCallEntry("call-1", "read", "{}")), null));
        msgs.add(ChatMessage.tool("call-1", "result " + "x".repeat(200)));
        msgs.add(ChatMessage.ofUser("latest"));

        List<ChatMessage> folded = ContextFolding.foldRange(
                msgs,
                new CompactionRangeSelector.Selection(0, 2, 3),
                mockClientReturning("<摘要>工具任务摘要</摘要>"),
                null);

        assertEquals(2, folded.size());
        assertTrue(folded.get(0).getContent().contains("[历史上下文折叠"));
        assertTrue(folded.get(1).getContent().contains("latest"));
        assertTrue(folded.stream().noneMatch(ChatMessage::isTool));
    }

    @Test
    void foldRangeSkipsWhenSummaryIsNotSmaller() throws Exception {
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ChatMessage.ofSystem("system prompt"));
        msgs.add(ChatMessage.ofUser("old"));
        msgs.add(ChatMessage.ofUser("new"));

        List<ChatMessage> folded = ContextFolding.foldRange(
                msgs,
                new CompactionRangeSelector.Selection(0, 0, 1),
                mockClientReturning("<摘要>" + "x".repeat(5000) + "</摘要>"),
                null);

        assertEquals(2, folded.size());
        assertTrue(folded.get(0).getContent().contains("old"));
        assertTrue(folded.get(1).getContent().contains("new"));
    }

    @Test
    void foldRangeUsesStructuredPromptAndMergesPreviousCheckpoint() throws Exception {
        AtomicReference<List<ChatMessage>> summaryInput = new AtomicReference<>();
        String structuredOutput = """
                <摘要>
                ## 主要请求与意图
                - 继续实现上下文压缩
                ## 关键技术与约定
                - 保留 tool-call/result 配对
                ## 文件与代码
                - /src/ContextFolding.java
                ## 错误与修复
                - 摘要未变小则跳过
                ## 待办事项
                - (none)
                ## 当前进度
                - 阶段 2 进行中
                ## 下一步
                - 结构化摘要落地
                ## 关键上下文
                - 已有 checkpoint 需要合并
                </摘要>
                <<<MEMORY>>>
                <记忆>
                - 上下文压缩必须校验摘要是否变小
                </记忆>""";
        TestLoopraProvider client = TestLoopraProvider.builder()
                .model("mock")
                .call(request -> {
                    summaryInput.set(new ArrayList<>(request.messages().stream()
                            .map(message -> ChatMessage.ofUser(message.content()))
                            .toList()));
                    return TestLoopraProvider.response(structuredOutput);
                })
                .build();

        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ChatMessage.ofSystem("system prompt"));
        msgs.add(ChatMessage.ofUser("[历史上下文折叠——以下 3 条较早消息已被摘要]\n"
                + "<compacted-summary>旧的 checkpoint</compacted-summary>"));
        msgs.add(ChatMessage.ofUser("new work " + "x".repeat(2000)));
        msgs.add(ChatMessage.ofUser("latest"));

        List<ChatMessage> folded = ContextFolding.foldRange(
                msgs,
                new CompactionRangeSelector.Selection(0, 1, 2),
                client,
                null);

        String systemPrompt = summaryInput.get().get(0).getContent();
        assertTrue(systemPrompt.contains("合并"));
        assertTrue(systemPrompt.contains("## 主要请求与意图"));
        assertEquals(2, folded.size());
        assertTrue(folded.get(0).getContent().contains("<compacted-summary>"));
        assertTrue(folded.get(0).getContent().contains("## 主要请求与意图"));
        assertTrue(folded.get(1).getContent().contains("latest"));
    }
}
