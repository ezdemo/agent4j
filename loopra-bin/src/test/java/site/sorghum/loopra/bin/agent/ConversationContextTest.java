package site.sorghum.loopra.bin.agent;

import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;
import site.sorghum.loopra.bin.agent.context.ConversationContext;
import site.sorghum.loopra.bin.agent.model.ChatMessage;
import site.sorghum.loopra.bin.agent.prompt.PromptPrefix;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConversationContextTest {

    private ConversationContext createContext() {
        PromptPrefix prefix = new PromptPrefix("system prompt", new ONode());
        return new ConversationContext(prefix);
    }

    @Test
    void addUserIncreasesSize() {
        ConversationContext ctx = createContext();
        assertEquals(0, ctx.size());
        ctx.addUser("hello");
        assertEquals(1, ctx.size());
    }

    @Test
    void addAssistantIncreasesSize() {
        ConversationContext ctx = createContext();
        ctx.addAssistant("hi", null, null);
        assertEquals(1, ctx.size());
    }

    @Test
    void addToolResultIncreasesSize() {
        ConversationContext ctx = createContext();
        ctx.addToolResult("tc_1", "result");
        assertEquals(1, ctx.size());
    }

    @Test
    void buildMessagesIncludesSystemPrefix() {
        ConversationContext ctx = createContext();
        ctx.addUser("hi");
        List<ChatMessage> msgs = ctx.buildMessages();
        assertEquals(2, msgs.size());
        assertEquals("system", msgs.get(0).getRole());
        assertEquals("system prompt", msgs.get(0).getContent());
        assertEquals("user", msgs.get(1).getRole());
    }

    @Test
    void clearResetsHistory() {
        ConversationContext ctx = createContext();
        ctx.addUser("msg1");
        ctx.addUser("msg2");
        assertEquals(2, ctx.size());
        ctx.clear();
        assertEquals(0, ctx.size());
    }

    @Test
    void retryLastUserRemovesFromLastUser() {
        ConversationContext ctx = createContext();
        ctx.addUser("msg1");
        ctx.addAssistant("reply1", null, null);
        ctx.addUser("msg2");
        ctx.addAssistant("reply2", null, null);

        String removed = ctx.retryLastUser();
        assertEquals("msg2", removed);
        assertEquals(2, ctx.size(), "应移除 msg2 及其后的消息");
    }

    @Test
    void retryLastUserReturnsNullIfNoUser() {
        ConversationContext ctx = createContext();
        ctx.addAssistant("only assistant", null, null);
        String removed = ctx.retryLastUser();
        assertNull(removed);
    }

    @Test
    void rewindToUser() {
        ConversationContext ctx = createContext();
        ctx.addUser("round1");
        ctx.addAssistant("reply1", null, null);
        ctx.addUser("round2");
        ctx.addAssistant("reply2", null, null);
        ctx.addUser("round3");
        ctx.addAssistant("reply3", null, null);

        String removed = ctx.rewindToUser(1);
        assertEquals("round2", removed);
        assertEquals(2, ctx.size(), "应回退到 round2，保留 round1 + reply1");
    }

    @Test
    void rewindToUserOutOfRange() {
        ConversationContext ctx = createContext();
        ctx.addUser("msg1");
        String removed = ctx.rewindToUser(5);
        assertNull(removed);
        assertEquals(1, ctx.size(), "无效索引不应修改历史");
    }

    @Test
    void compactReplacesHistory() {
        ConversationContext ctx = createContext();
        ctx.addUser("msg1");
        ctx.addAssistant("reply1", null, null);
        ctx.addUser("msg2");

        List<ChatMessage> folded = new ArrayList<>();
        folded.add(ChatMessage.ofUser("summary"));

        ctx.compact(folded);
        assertEquals(1, ctx.size());
        assertEquals("summary", ctx.getHistory().get(0).getContent());
    }

    @Test
    void injectHistoryDoesNotAffectSize() {
        ConversationContext ctx = createContext();
        ChatMessage msg = ChatMessage.ofUser("loaded");
        ctx.injectHistory(msg);
        assertEquals(1, ctx.size());
    }

    @Test
    void getHistoryReturnsCopy() {
        ConversationContext ctx = createContext();
        ctx.addUser("hello");
        List<ChatMessage> history = ctx.getHistory();
        history.clear();
        assertEquals(1, ctx.size(), "getHistory 返回副本，修改不应影响内部状态");
    }

}
