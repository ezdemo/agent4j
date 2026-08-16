package site.sorghum.loopra.bin.agent;

import org.junit.jupiter.api.Test;
import site.sorghum.loopra.bin.agent.context.CompactionRangeSelector;
import site.sorghum.loopra.bin.agent.context.ContextTokenEstimator;
import site.sorghum.loopra.bin.agent.model.ChatMessage;
import site.sorghum.loopra.bin.agent.model.ToolCallEntry;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompactionRangeSelectorTest {

    @Test
    void retainsTailTokenBudget() {
        List<ChatMessage> history = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            history.add(ChatMessage.ofUser("old message " + i + " " + "x".repeat(200)));
        }
        int retainTokens = ContextTokenEstimator.estimateMessage(history.get(9))
                + ContextTokenEstimator.estimateMessage(history.get(8));

        CompactionRangeSelector.Selection selection = CompactionRangeSelector.select(history, retainTokens);

        assertNotNull(selection);
        assertEquals(0, selection.start());
        assertEquals(selection.keepFromIndex() - 1, selection.end());
        assertTrue(selection.keepFromIndex() > 0);
        assertTrue(selection.keepFromIndex() <= 8);
        assertEquals(2, history.size() - selection.keepFromIndex());
    }

    @Test
    void neverSplitsToolCallAndResult() {
        List<ChatMessage> history = new ArrayList<>();
        history.add(ChatMessage.ofUser("first"));
        history.add(ChatMessage.assistant("calling", List.of(new ToolCallEntry("call-1", "read", "{}")), null));
        history.add(ChatMessage.tool("call-1", "result"));
        history.add(ChatMessage.ofUser("latest " + "x".repeat(500)));

        CompactionRangeSelector.Selection selection = CompactionRangeSelector.select(history, 0);

        assertNotNull(selection);
        assertEquals(3, selection.keepFromIndex());
    }

    @Test
    void returnsNullWhenNoCompactableHead() {
        List<ChatMessage> history = new ArrayList<>();
        history.add(ChatMessage.ofUser("only message"));

        assertNull(CompactionRangeSelector.select(history, 1));
    }

    @Test
    void balancesMultipleToolCallsAgainstResults() {
        List<ChatMessage> history = new ArrayList<>();
        history.add(ChatMessage.ofUser("task"));
        history.add(ChatMessage.assistant("calls", List.of(
                new ToolCallEntry("call-1", "read", "{}"),
                new ToolCallEntry("call-2", "glob", "{}")
        ), null));
        history.add(ChatMessage.tool("call-1", "one"));
        history.add(ChatMessage.tool("call-2", "two"));
        history.add(ChatMessage.ofUser("latest"));

        CompactionRangeSelector.Selection selection = CompactionRangeSelector.select(history, 0);

        assertNotNull(selection);
        assertEquals(4, selection.keepFromIndex());
    }

}
