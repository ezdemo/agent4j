package site.sorghum.loopra.bin.agent.core;

import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;
import site.sorghum.loopra.bin.agent.context.ConversationContext;
import site.sorghum.loopra.bin.agent.model.LoopraChatMessage;
import site.sorghum.loopra.bin.agent.model.UserMessage;
import site.sorghum.loopra.bin.agent.prompt.PromptPrefix;
import site.sorghum.loopra.bin.model.ModelClient;
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

        OverflowThenSuccessClient client = new OverflowThenSuccessClient();
        AgentLoop loop = new AgentLoop(client, registry, context);
        loop.freezePromptPrefix();
        loop.setOutput(AgentOutput.NOOP);

        String result = loop.run(UserMessage.of("继续执行"));

        assertEquals("ok", result);
        assertEquals(2, client.streamCalls.get());
        assertEquals(22, context.size());
        assertTrue(context.getHistory().get(0).getContent().startsWith("[历史上下文折叠"));
    }

    private static final class OverflowThenSuccessClient implements ModelClient {
        private final AtomicInteger streamCalls = new AtomicInteger();

        @Override
        public ONode chat(List<LoopraChatMessage> messages, ONode tools) {
            return ONode.ofJson("{\"content\":\"历史摘要\"}");
        }

        @Override
        public void chatStream(List<LoopraChatMessage> messages, ONode tools, StreamCallback callback) {
            if (streamCalls.incrementAndGet() == 1) {
                callback.onError("{\"type\":\"response.failed\",\"response\":{\"error\":{\"code\":\"context_length_exceeded\"}}}");
                return;
            }
            callback.onContentDelta("ok");
            callback.onDone();
        }

        @Override
        public String getModel() {
            return "test-model";
        }

        @Override
        public void setModel(String model) {
        }
    }
}
