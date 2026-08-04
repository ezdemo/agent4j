package site.sorghum.loopra.bin.agent.core;

import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;
import site.sorghum.loopra.bin.agent.model.ChatMessage;
import site.sorghum.loopra.bin.model.ModelClient;
import site.sorghum.loopra.bin.tool.ToolRegistry;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubAgentSessionAffinityTest {

    @Test
    void derivesStableUniqueAffinityFromParentSession() {
        RecordingModelClient firstClient = new RecordingModelClient();
        SubAgent first = new SubAgent(firstClient, new ToolRegistry(), "system");

        first.setSessionId("parent-session");
        String firstAffinity = firstClient.lastAffinity();
        first.setSessionId("parent-session");

        RecordingModelClient secondClient = new RecordingModelClient();
        SubAgent second = new SubAgent(secondClient, new ToolRegistry(), "system");
        second.setSessionId("parent-session");
        String secondAffinity = secondClient.lastAffinity();

        assertTrue(firstAffinity.startsWith("parent-session:sub-agent:"));
        assertEquals(firstAffinity, firstClient.lastAffinity());
        assertNotEquals(firstAffinity, secondAffinity);
    }

    @Test
    void inheritsPlanModeFromParentController() throws Exception {
        ToolRegistry registry = new ToolRegistry().setDisabledTools(Set.of());
        registry.setRefreshContext(Paths.get(".").toAbsolutePath(), null, null, List.of());
        AgentLoop parent = new AgentLoop(new RecordingModelClient(), registry, null);
        parent.setPlanMode(true);
        SubAgent child = new SubAgent(new RecordingModelClient(), registry, "system", parent);

        assertEquals("done", child.run("inspect", null));

        Field field = SubAgent.class.getDeclaredField("subLoop");
        field.setAccessible(true);
        AgentLoop childLoop = (AgentLoop) field.get(child);
        assertTrue(childLoop.isPlanMode());
    }

    private static final class RecordingModelClient implements ModelClient {
        private final List<String> affinities = new ArrayList<>();

        String lastAffinity() {
            return affinities.get(affinities.size() - 1);
        }

        @Override
        public ONode chat(List<ChatMessage> messages, ONode tools) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void chatStream(List<ChatMessage> messages, ONode tools, StreamCallback callback) {
            callback.onContentDelta("done");
            callback.onDone();
        }

        @Override
        public String getModel() {
            return "test-model";
        }

        @Override
        public void setModel(String model) {
        }

        @Override
        public void setSessionAffinity(String sessionAffinity) {
            affinities.add(sessionAffinity);
        }
    }
}
