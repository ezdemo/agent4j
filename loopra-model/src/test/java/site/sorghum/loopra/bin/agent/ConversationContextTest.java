package site.sorghum.loopra.bin.agent;

import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;
import site.sorghum.loopra.bin.agent.context.ConversationContext;
import site.sorghum.loopra.bin.agent.model.LoopraChatMessage;
import site.sorghum.loopra.bin.agent.prompt.PromptPrefix;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConversationContextTest {

    private ConversationContext createContext() {
        PromptPrefix prefix = new PromptPrefix("system prompt", new ONode());
        return new ConversationContext(prefix);
    }

    @Test
    void promotesLegacyToolCallReasoningToMessageLevel() {
        String responseReasoning = "{\"type\":\"reasoning\",\"encrypted_content\":\"encrypted\"}";
        LoopraChatMessage message = LoopraChatMessage.fromMap(Map.of(
                "role", "assistant",
                "tool_calls", List.of(
                        Map.of("id", "call-1", "name", "read", "arguments", "{}",
                                "response_reasoning", responseReasoning),
                        Map.of("id", "call-2", "name", "glob", "arguments", "{}",
                                "response_reasoning", responseReasoning)
                )
        ));

        assertEquals(responseReasoning, message.getResponseReasoning());
        assertNull(message.getToolCalls().get(0).responseReasoning());
        assertNull(message.getToolCalls().get(1).responseReasoning());
        assertEquals(responseReasoning, message.toMap().get("response_reasoning"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> persistedCalls = (List<Map<String, Object>>) message.toMap().get("tool_calls");
        assertNull(persistedCalls.get(0).get("response_reasoning"));
        assertNull(persistedCalls.get(1).get("response_reasoning"));
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
        List<LoopraChatMessage> msgs = ctx.buildMessages();
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

        List<LoopraChatMessage> folded = new ArrayList<>();
        folded.add(LoopraChatMessage.ofUser("summary"));

        ctx.compact(folded);
        assertEquals(1, ctx.size());
        assertEquals("summary", ctx.getHistory().get(0).getContent());
    }

    @Test
    void injectHistoryDoesNotAffectSize() {
        ConversationContext ctx = createContext();
        LoopraChatMessage msg = LoopraChatMessage.ofUser("loaded");
        ctx.injectHistory(msg);
        assertEquals(1, ctx.size());
    }

    @Test
    void getHistoryReturnsCopy() {
        ConversationContext ctx = createContext();
        ctx.addUser("hello");
        List<LoopraChatMessage> history = ctx.getHistory();
        history.clear();
        assertEquals(1, ctx.size(), "getHistory 返回副本，修改不应影响内部状态");
    }

}
