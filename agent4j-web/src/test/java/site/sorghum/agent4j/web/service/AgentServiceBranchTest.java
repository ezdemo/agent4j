package site.sorghum.agent4j.web.service;

import org.junit.jupiter.api.Test;
import site.sorghum.agent4j.bin.agent.model.ChatMessage;
import site.sorghum.agent4j.bin.agent.model.ToolCallEntry;
import site.sorghum.agent4j.web.common.ServiceException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentServiceBranchTest {

    @Test
    void copiesTheExactRequestedPrefix() {
        ChatMessage assistant = ChatMessage.assistant("done", List.of(new ToolCallEntry("1", "read", Map.of())), "thinking");
        ChatMessage tool = ChatMessage.tool("1", "result");
        List<ChatMessage> source = List.of(assistant, tool);

        List<ChatMessage> copied = AgentService.copyBranchMessages(source, 2);

        assertEquals(source, copied);
        assertNotSame(source, copied);
    }

    @Test
    void rejectsAnOutOfRangeBoundary() {
        assertThrows(ServiceException.class,
                () -> AgentService.copyBranchMessages(List.of(ChatMessage.ofUser("x")), 2));
    }

    @Test
    void usesTheSourceTitleForTheReplicaTitle() {
        assertEquals("1+1=?[复刻]", AgentService.branchTitle("1+1=?", "agent4j-20260713170740"));
    }
}
