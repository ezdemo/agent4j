package site.sorghum.loopra.bin.agent.core;

import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;
import site.sorghum.cutin.core.context.Usage;
import site.sorghum.loopra.bin.agent.context.ConversationContext;
import site.sorghum.loopra.bin.agent.listener.AgentLoopListener;
import site.sorghum.loopra.bin.agent.model.ChatMessage;
import site.sorghum.loopra.bin.agent.model.UserMessage;
import site.sorghum.loopra.bin.agent.prompt.PromptPrefix;
import site.sorghum.loopra.bin.model.TestLoopraProvider;
import site.sorghum.loopra.bin.tool.ToolRegistry;
import site.sorghum.loopra.tool.AgentOutput;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CutinUsagePropagationTest {

    @Test
    void cutinLoopUpdatesLastPromptTokensAndSessionUsageSink() throws Exception {
        ConversationContext context = new ConversationContext(
                new PromptPrefix("system", new ONode().asArray()));
        TestLoopraProvider client = TestLoopraProvider.usageStream(
                "hello-from-usage-stub",
                new Usage(12_345, 678, 0, 100, 20));
        AgentLoop loop = new AgentLoop(
                client, new ToolRegistry().setDisabledTools(java.util.Set.of()),
                context, null);
        loop.freezePromptPrefix();
        loop.setOutput(AgentOutput.NOOP);

        AtomicInteger sinkPrompt = new AtomicInteger();
        loop.setSessionUsageSink(prompt -> sinkPrompt.set(prompt));
        AtomicReference<String> listenerModel = new AtomicReference<>();
        AtomicInteger listenerPrompt = new AtomicInteger();
        AtomicInteger listenerCacheHit = new AtomicInteger();
        AtomicInteger listenerCacheMiss = new AtomicInteger();
        loop.setListener(new AgentLoopListener() {
            @Override
            public void onUsage(String model, int promptTokens, int completionTokens, int totalTokens,
                                int cacheHit, int cacheMiss) {
                listenerModel.set(model);
                listenerPrompt.set(promptTokens);
                listenerCacheHit.set(cacheHit);
                listenerCacheMiss.set(cacheMiss);
            }
        });

        String result = loop.run(UserMessage.of("hello"));

        assertEquals("hello-from-usage-stub", result);
        assertEquals(12_345, loop.getLastPromptTokens());
        assertEquals(12_345, sinkPrompt.get());
        assertEquals(12_345, listenerPrompt.get());
        assertEquals(100, listenerCacheHit.get());
        assertEquals(12_245, listenerCacheMiss.get());
        assertEquals("usage-stub", listenerModel.get());
        assertNotNull(loop.getLastContextEstimate());
    }

    @Test
    void cutinUsageReportPropagatesCacheStats() {
        ConversationContext context = new ConversationContext(
                new PromptPrefix("system", new ONode().asArray()));
        TestLoopraProvider client = TestLoopraProvider.usageStream("", Usage.ZERO);
        AgentLoop loop = new AgentLoop(
                client, new ToolRegistry().setDisabledTools(java.util.Set.of()),
                context, null);
        loop.freezePromptPrefix();
        loop.setOutput(AgentOutput.NOOP);

        AtomicReference<String> listenerModel = new AtomicReference<>();
        AtomicInteger listenerPrompt = new AtomicInteger();
        AtomicInteger listenerCacheHit = new AtomicInteger();
        AtomicInteger listenerCacheMiss = new AtomicInteger();
        loop.setListener(new AgentLoopListener() {
            @Override
            public void onUsage(String model, int promptTokens, int completionTokens, int totalTokens,
                                int cacheHit, int cacheMiss) {
                listenerModel.set(model);
                listenerPrompt.set(promptTokens);
                listenerCacheHit.set(cacheHit);
                listenerCacheMiss.set(cacheMiss);
            }
        });

        loop.reportCutinUsage(new Usage(12_345, 678, 0, 100, 20));

        assertEquals(12_345, listenerPrompt.get());
        assertEquals(100, listenerCacheHit.get());
        assertEquals(12_245, listenerCacheMiss.get());
        assertEquals("usage-stub", listenerModel.get());
    }

}
