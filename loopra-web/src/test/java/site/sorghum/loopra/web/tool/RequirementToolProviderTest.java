package site.sorghum.loopra.web.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import site.sorghum.loopra.bin.requirement.Requirement;
import site.sorghum.loopra.bin.requirement.RequirementStore;
import site.sorghum.loopra.tool.ToolContext;
import site.sorghum.loopra.web.service.AgentService;
import site.sorghum.loopra.web.service.RequirementManager;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 需求池专属工具单元测试：会话归属校验 + 状态流转回调。
 *
 * @author Sorghum
 */
class RequirementToolProviderTest {

    @TempDir
    Path tempDir;

    private static class StubAgentService extends AgentService {
        final List<String> comments = new ArrayList<>();

        @Override
        public String resolveProjectPath(String hash) {
            return "/tmp/" + hash;
        }

        @Override
        public void appendUserMessage(String workspacePath, String sessionName, String text) {
            comments.add(text);
        }

        @Override
        public List<site.sorghum.loopra.bin.agent.model.ChatMessage> getHistory(String workspacePath, String sessionName) {
            return comments.stream()
                    .map(site.sorghum.loopra.bin.agent.model.ChatMessage::ofUser)
                    .collect(java.util.stream.Collectors.toList());
        }

        @Override
        public void appendAssistantMessage(String workspacePath, String sessionName, String content) {
            // no-op
        }
    }

    private RequirementManager manager;
    private RequirementToolProvider provider;

    @BeforeEach
    void setUp() {
        manager = new RequirementManager(new RequirementStore(tempDir), new StubAgentService());
        provider = new RequirementToolProvider();
        provider.setRequirementManagerForTest(manager);
    }

    private static ToolContext ctx(String sessionId) {
        return new ToolContext(Map.of(), "/tmp/p1", sessionId);
    }

    private Requirement createRequirement() {
        return manager.create(Requirement.builder()
                .title("优化性能")
                .description("分页加载")
                .priority("high")
                .projectHash("p1")
                .projectName("agent4j")
                .build());
    }

    @Test
    void finishRequirementFlipsStatusInRequirementSession() {
        Requirement created = createRequirement();

        String result = provider.finishRequirement("done", "完成重构", ctx("req_" + created.getId()));

        assertTrue(result.startsWith("REQUIREMENT_FINISHED"));
        Requirement stored = manager.list().get(0);
        assertEquals("done", stored.getStatus());
        assertEquals("完成重构", stored.getSummary());
    }

    @Test
    void finishRequirementRejectsInvalidStatus() {
        Requirement created = createRequirement();

        String result = provider.finishRequirement("in_progress", "x", ctx("req_" + created.getId()));

        assertTrue(result.startsWith("INVALID_STATUS"));
        assertEquals("todo", manager.list().get(0).getStatus());
    }

    @Test
    void toolsRejectNonRequirementSessions() {
        Requirement created = createRequirement();

        // 普通会话（非 req_ 前缀）调用全部被拒
        assertTrue(provider.finishRequirement("done", "x", ctx("normal-session")).startsWith("SCOPE_ONLY"));
        assertTrue(provider.replyComment("x", ctx("normal-session")).startsWith("SCOPE_ONLY"));
        assertTrue(provider.showRequirements(ctx("normal-session")).startsWith("SCOPE_ONLY"));

        // 需求状态未被改变
        assertEquals("todo", manager.list().get(0).getStatus());
        assertNotNull(created.getId());
    }

    @Test
    void showRequirementsReturnsContextWithComments() {
        Requirement created = createRequirement();
        manager.addComment(created.getId(), "请优先处理");

        String context = provider.showRequirements(ctx("req_" + created.getId()));

        assertTrue(context.contains("优化性能"));
        assertTrue(context.contains("分页加载"));
        assertTrue(context.contains("请优先处理"));
    }

    @Test
    void replyCommentWritesIntoRequirementSession() {
        Requirement created = createRequirement();

        String result = provider.replyComment("收到，正在处理", ctx("req_" + created.getId()));

        assertTrue(result.startsWith("REPLY_SENT"));
    }
}
