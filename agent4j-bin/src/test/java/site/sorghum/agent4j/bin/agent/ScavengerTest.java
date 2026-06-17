package site.sorghum.agent4j.bin.agent;

import org.junit.jupiter.api.Test;
import site.sorghum.agent4j.bin.agent.context.Scavenger;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScavengerTest {

    @Test
    void emptyContentReturnsNothing() {
        List<Scavenger.ToolCall> result = Scavenger.scavenge(null, null, new ArrayList<>());
        assertTrue(result.isEmpty());
    }

    @Test
    void noToolCallsInContent() {
        List<Scavenger.ToolCall> result = Scavenger.scavenge("just thinking", "regular reply", new ArrayList<>());
        assertTrue(result.isEmpty());
    }

    @Test
    void existingCallsDeduplicated() {
        String content = "I will use read_file";
        Scavenger.ToolCall existing = new Scavenger.ToolCall("tc1", "read_file", "{}");
        List<Scavenger.ToolCall> existingList = new ArrayList<>();
        existingList.add(existing);
        List<Scavenger.ToolCall> result = Scavenger.scavenge(null, content, existingList);
        for (Scavenger.ToolCall tc : result) {
            assertFalse(tc.name().equals("read_file") && tc.arguments().equals("{}"),
                    "已存在的调用不应重复回收");
        }
    }
}
