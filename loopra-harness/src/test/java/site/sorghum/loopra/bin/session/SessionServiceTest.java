package site.sorghum.loopra.bin.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.noear.snack4.ONode;
import site.sorghum.loopra.bin.agent.context.ConversationContext;
import site.sorghum.loopra.bin.agent.prompt.PromptPrefix;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

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
}
