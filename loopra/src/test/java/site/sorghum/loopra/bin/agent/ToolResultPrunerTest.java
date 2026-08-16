package site.sorghum.loopra.bin.agent;

import org.junit.jupiter.api.Test;
import site.sorghum.loopra.bin.agent.context.ToolResultPruner;
import site.sorghum.loopra.bin.agent.model.ChatMessage;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ToolResultPrunerTest {

    @Test
    void rejectsBudgetsThatCannotShrink() {
        assertThrows(IllegalArgumentException.class, () ->
                new ToolResultPruner.Config(10, 10, 10));
        assertThrows(IllegalArgumentException.class, () ->
                new ToolResultPruner.Config(10, -1, 2));
        assertThrows(IllegalArgumentException.class, () ->
                new ToolResultPruner.Config(0, 0, 0));
    }

    @Test
    void prunesTextMiddleAndKeepsHeadAndTail() {
        ToolResultPruner.Config config = new ToolResultPruner.Config(200, 8, 4);
        String content = "A".repeat(150) + "B".repeat(150);

        String pruned = ToolResultPruner.pruneContent(content, config);

        assertTrue(pruned.startsWith("AAAAAAAA"));
        assertTrue(pruned.endsWith("BBBB"));
        assertTrue(pruned.contains(ToolResultPruner.PRUNE_MARKER));
        assertTrue(pruned.codePointCount(0, pruned.length())
                < content.codePointCount(0, content.length()));
    }

    @Test
    void doesNotSplitSurrogatePairs() {
        ToolResultPruner.Config config = new ToolResultPruner.Config(60, 8, 4);
        String content = "😀a😀b😀c😀d😀e".repeat(7);

        String pruned = ToolResultPruner.pruneContent(content, config);

        assertTrue(pruned.startsWith("😀a😀"));
        assertTrue(pruned.endsWith("😀e"));
    }

    @Test
    void leavesToolResultsUnderBudgetUntouched() {
        ToolResultPruner.Config config = ToolResultPruner.Config.defaults();
        List<ChatMessage> history = new ArrayList<>();
        history.add(ChatMessage.tool("call-1", "short"));

        ToolResultPruner.PruneResult result = ToolResultPruner.prune(history, config);

        assertFalse(result.changed());
        assertEquals("short", result.messages().get(0).getContent());
        assertEquals(0, result.prunedCount());
    }

    @Test
    void preservesToolMetadataWhenPruning() {
        ToolResultPruner.Config config = new ToolResultPruner.Config(200, 3, 2);
        ChatMessage tool = ChatMessage.tool("call-1", "x".repeat(300), 100L, 200L);
        List<ChatMessage> history = new ArrayList<>();
        history.add(tool);

        ToolResultPruner.PruneResult result = ToolResultPruner.prune(history, config);

        assertTrue(result.changed());
        ChatMessage pruned = result.messages().get(0);
        assertEquals("call-1", pruned.getToolCallId());
        assertEquals(100L, pruned.getToolStartedAt());
        assertEquals(200L, pruned.getToolFinishedAt());
        assertTrue(pruned.getContent().contains(ToolResultPruner.PRUNE_MARKER));
    }

    @Test
    void returnsEmptyResultForNullHistory() {
        ToolResultPruner.PruneResult result = ToolResultPruner.prune(null, ToolResultPruner.Config.defaults());
        assertTrue(result.messages().isEmpty());
    }
}
