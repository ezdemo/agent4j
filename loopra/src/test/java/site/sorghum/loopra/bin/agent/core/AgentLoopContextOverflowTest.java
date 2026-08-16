package site.sorghum.loopra.bin.agent.core;

import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;
import site.sorghum.loopra.bin.agent.context.ConversationContext;
import site.sorghum.loopra.bin.agent.model.ChatMessage;
import site.sorghum.loopra.bin.agent.model.UserMessage;
import site.sorghum.loopra.bin.agent.prompt.PromptPrefix;
import site.sorghum.loopra.bin.model.TestLoopraProvider;
import site.sorghum.loopra.bin.tool.ToolRegistry;
import site.sorghum.loopra.tool.AgentOutput;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLoopContextOverflowTest {

    @Test
    void usesServerPromptUsageWhenOfflineEstimateIsLower() {
        assertEquals(230_000, AgentLoop.effectivePromptTokens(190_000, 230_000));
        assertEquals(190_000, AgentLoop.effectivePromptTokens(190_000, 0));
    }

    @Test
    void foldsHistoryAndRetriesOnceAfterContextOverflow() throws Exception {
        ToolRegistry registry = new ToolRegistry().setDisabledTools(Set.of());
        ConversationContext context = new ConversationContext(
                new PromptPrefix("system", new ONode().asArray()));
        for (int i = 0; i < 20; i++) {
            context.addUser("历史消息 " + i + " " + "x".repeat(100));
        }

        AtomicInteger streamCalls = new AtomicInteger();
        TestLoopraProvider client = TestLoopraProvider.builder()
                .call(request -> TestLoopraProvider.response("历史摘要"))
                .stream(request -> {
                    if (streamCalls.incrementAndGet() == 1) {
                        return TestLoopraProvider.errorStream(
                                "{\"type\":\"response.failed\",\"response\":{\"error\":{\"code\":\"context_length_exceeded\"}}}");
                    }
                    return TestLoopraProvider.contentStream("ok");
                })
                .build();
        AgentLoop loop = new AgentLoop(client, registry, context, null);
        loop.freezePromptPrefix();
        loop.setOutput(AgentOutput.NOOP);

        String result = loop.run(UserMessage.of("继续执行"));

        assertEquals("ok", result);
        assertEquals(2, streamCalls.get());
        assertTrue(context.size() < 22);
        assertTrue(context.getHistory().get(0).getContent().startsWith("[历史上下文折叠"));
    }

}
