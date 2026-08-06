package site.sorghum.loopra.bin.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.noear.snack4.ONode;
import site.sorghum.loopra.bin.agent.context.ContextFolding;
import site.sorghum.loopra.bin.agent.memory.ProjectMemoryStore;
import site.sorghum.loopra.bin.agent.model.LoopraChatMessage;
import site.sorghum.loopra.bin.model.ModelClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContextFoldingTest {

    @Test
    void estimateCharsSingleMessage() {
        LoopraChatMessage msg = LoopraChatMessage.ofUser("hello world");
        int n = ContextFolding.estimateChars(msg);
        assertEquals(4 + 11, n, "role=4 chars + content=11 chars");
    }

    @Test
    void estimateCharsMultipleMessages() {
        List<LoopraChatMessage> msgs = new ArrayList<>();
        msgs.add(LoopraChatMessage.ofUser("hi"));
        msgs.add(LoopraChatMessage.assistant("hello", null, null));
        int n = ContextFolding.estimateChars(msgs);
        assertTrue(n > 0);
    }

    @Test
    void estimateCharsWithReasoning() {
        LoopraChatMessage msg = LoopraChatMessage.assistant("ok", null, "thinking...");
        int n = ContextFolding.estimateChars(msg);
        assertTrue(n > 4 + 2, "reasoning_content 也应计入字符数");
    }

    @Test
    void foldReturnsOriginalIfUnderThreshold() throws Exception {
        List<LoopraChatMessage> msgs = new ArrayList<>();
        msgs.add(LoopraChatMessage.ofUser("short"));
        List<LoopraChatMessage> result = ContextFolding.fold(msgs, 1000, 500, null);
        assertSame(msgs, result, "未超阈值应返回原列表");
    }

    /**
     * mock ModelClient：返回固定双段格式（会话摘要 + 长期记忆），
     * 用于验证 foldKeepLast 的"提炼 + 沉淀"行为。
     */
    private static ModelClient mockClientReturning(String fullText) {
        return new ModelClient() {
            @Override
            public ONode chat(List<LoopraChatMessage> messages, ONode tools) {
                ONode resp = ONode.ofJson("{}");
                resp.set("content", fullText);
                return resp;
            }

            @Override
            public void chatStream(List<LoopraChatMessage> messages, ONode tools, StreamCallback callback) {
            }

            @Override
            public String getModel() {
                return "mock";
            }

            @Override
            public void setModel(String model) {
            }
        };
    }

    /**
     * 构造超过 keepCount 的消息列表：1 条 system + N 条 user/assistant 交替。
     */
    private static List<LoopraChatMessage> buildLongHistory(int rounds) {
        List<LoopraChatMessage> msgs = new ArrayList<>();
        msgs.add(LoopraChatMessage.ofSystem("system prompt"));
        for (int i = 0; i < rounds; i++) {
            msgs.add(LoopraChatMessage.ofUser("用户第" + i + "轮：请帮我改 AgentLoop"));
            msgs.add(LoopraChatMessage.assistant("已修改第" + i + "轮", null, null));
        }
        return msgs;
    }

    @Test
    void foldKeepLastPreservesWebHiddenOnRetainedMessages(@TempDir Path dir) throws Exception {
        List<LoopraChatMessage> msgs = buildLongHistory(30);
        LoopraChatMessage hidden = msgs.get(msgs.size() - 2);
        hidden.setWebHidden(true);

        List<LoopraChatMessage> folded = ContextFolding.foldKeepLast(
                msgs, 20, mockClientReturning("<摘要>摘要</摘要>"), dir);

        LoopraChatMessage retained = folded.stream()
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
                - 架构约定：AgentLoop 通过 workspace 字段传递工作区给 ContextFolding
                - 改动 ContextFolding 后必须跑 ContextFoldingTest
                </记忆>""";
        ModelClient client = mockClientReturning(llmOutput);
        List<LoopraChatMessage> msgs = buildLongHistory(30); // 60 条历史 + 1 system

        List<LoopraChatMessage> folded = ContextFolding.foldKeepLast(msgs, 20, client, dir);

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
        ModelClient client = mockClientReturning(llmOutput);
        List<LoopraChatMessage> msgs = buildLongHistory(30);

        ContextFolding.foldKeepLast(msgs, 20, client, null);

        Path memFile = ProjectMemoryStore.memoryFilePath(dir);
        assertFalse(Files.exists(memFile), "workspace=null 时不应写记忆文件");
    }

    @Test
    void foldKeepLastDegradesWhenNoMemorySection(@TempDir Path dir) throws Exception {
        // LLM 未按格式输出，无 <<<MEMORY>>> 分隔 —— 降级：整段当摘要，不沉淀
        ModelClient client = mockClientReturning("只是一段普通摘要，没有记忆分隔标记。");
        List<LoopraChatMessage> msgs = buildLongHistory(30);

        List<LoopraChatMessage> folded = ContextFolding.foldKeepLast(msgs, 20, client, dir);

        assertTrue(folded.size() < msgs.size() - 1, "仍应完成历史压缩");
        Path memFile = ProjectMemoryStore.memoryFilePath(dir);
        assertFalse(Files.exists(memFile), "格式不符时不应沉淀记忆");
    }
}
