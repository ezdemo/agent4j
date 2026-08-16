package site.sorghum.loopra.bin.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.noear.snack4.ONode;
import site.sorghum.loopra.bin.agent.context.ConversationContext;
import site.sorghum.loopra.bin.agent.model.ChatMessage;
import site.sorghum.loopra.bin.agent.prompt.PromptPrefix;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SessionServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void ignoresLeadingCollapsedBlockWhenGeneratingTitle() throws Exception {
        SessionService service = new SessionService(
                new ConversationContext(new PromptPrefix("test", ONode.ofJson("[]"))), tempDir);

        assertEquals("帮我分析这个项目", service.generateSessionTitle("""
                ```折叠块
                引用文件：
                - README.md
                
                调用技能：
                /skill:analysis
                ```
                
                帮我分析这个项目
                """));
    }

    @Test
    void usesDefaultTitleWhenMessageOnlyContainsCollapsedBlock() throws Exception {
        SessionService service = new SessionService(
                new ConversationContext(new PromptPrefix("test", ONode.ofJson("[]"))), tempDir);

        assertEquals("新会话", service.generateSessionTitle("""
                ```折叠块
                调用技能：
                /skill:analysis
                ```
                """));
    }

    @Test
    void usesInjectedSessionStore() throws Exception {
        JsonlSessionStore injected = new JsonlSessionStore(tempDir);
        ConversationContext ctx = new ConversationContext(new PromptPrefix("test", ONode.ofJson("[]")));

        SessionService service = new SessionService(ctx, injected);

        assertSame(injected, service.getStore());
        assertSame(injected, ctx.getSessionStore());
    }

    @Test
    void rawEventsSurviveContextCompaction() throws Exception {
        ConversationContext ctx = new ConversationContext(new PromptPrefix("test", ONode.ofJson("[]")));
        SessionService service = new SessionService(ctx, new JsonlSessionStore(tempDir));

        ctx.addUser("原始用户消息");
        ctx.addToolResult("call-1", "原始 tool result");
        ctx.compact(List.of(ChatMessage.ofUser("[历史上下文折叠]\n<compacted-summary>checkpoint</compacted-summary>")));

        List<ChatMessage> events = service.loadRawEvents();
        assertEquals(2, events.size());
        assertEquals("原始用户消息", events.get(0).getContent());
        assertEquals("call-1", events.get(1).getToolCallId());
        assertEquals("原始 tool result", events.get(1).getContent());

        List<ChatMessage> surface = service.getStore().load();
        assertEquals(1, surface.size());
        assertTrue(surface.get(0).getContent().contains("checkpoint"));
    }
}
