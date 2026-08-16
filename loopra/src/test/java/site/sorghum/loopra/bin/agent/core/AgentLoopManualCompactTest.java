package site.sorghum.loopra.bin.agent.core;

import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;
import site.sorghum.loopra.bin.agent.context.ConversationContext;
import site.sorghum.loopra.bin.agent.model.ChatMessage;
import site.sorghum.loopra.bin.agent.prompt.PromptPrefix;
import site.sorghum.loopra.bin.model.TestLoopraProvider;
import site.sorghum.loopra.bin.tool.ToolRegistry;
import site.sorghum.loopra.tool.AgentOutput;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLoopManualCompactTest {

    @Test
    void compactNowUsesTokenBudgetAndFoldsOldestRange() throws Exception {
        ToolRegistry registry = new ToolRegistry().setDisabledTools(Set.of());
        ConversationContext context = new ConversationContext(
                new PromptPrefix("system", new ONode().asArray()));
        for (int i = 0; i < 40; i++) {
            context.addUser("历史消息 " + i + " " + "x".repeat(3000));
        }

        AgentLoop loop = new AgentLoop(TestLoopraProvider.builder()
                .call(request -> TestLoopraProvider.response("历史摘要"))
                .contextTokens(1000)
                .build(), registry, context, null);
        loop.freezePromptPrefix();
        loop.setOutput(AgentOutput.NOOP);
        loop.compactNow();

        assertTrue(context.size() < 40);
        assertTrue(context.getHistory().get(0).getContent().startsWith("[历史上下文折叠"));
    }

}
