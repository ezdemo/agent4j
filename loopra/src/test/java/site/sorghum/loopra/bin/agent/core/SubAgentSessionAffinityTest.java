package site.sorghum.loopra.bin.agent.core;

import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;
import site.sorghum.loopra.bin.agent.environment.SessionEnvironment;
import site.sorghum.loopra.bin.agent.model.ChatMessage;
import site.sorghum.loopra.bin.model.TestLoopraProvider;
import site.sorghum.loopra.bin.tool.ToolRegistry;

import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SubAgentSessionAffinityTest {

    @Test
    void derivesStableUniqueAffinityFromParentSession() {
        TestLoopraProvider firstClient = recordingProvider();
        SubAgent first = new SubAgent(firstClient, new ToolRegistry(), "system");

        first.setSessionId("parent-session");
        String firstAffinity = lastAffinity(firstClient);
        first.setSessionId("parent-session");

        TestLoopraProvider secondClient = recordingProvider();
        SubAgent second = new SubAgent(secondClient, new ToolRegistry(), "system");
        second.setSessionId("parent-session");
        String secondAffinity = lastAffinity(secondClient);

        assertTrue(firstAffinity.startsWith("parent-session:sub-agent:"));
        assertEquals(firstAffinity, lastAffinity(firstClient));
        assertNotEquals(firstAffinity, secondAffinity);
    }

    @Test
    void inheritsPlanModeFromParentController() throws Exception {
        ToolRegistry registry = new ToolRegistry().setDisabledTools(Set.of());
        registry.setEnvironment(SessionEnvironment.local(Paths.get(".").toAbsolutePath()));
        AgentLoop parent = new AgentLoop(recordingProvider(), registry, null, null);
        parent.setPlanMode(true);
        SubAgent child = new SubAgent(recordingProvider(), registry, "system", parent);

        assertEquals("done", child.run("inspect", null));

        Field field = SubAgent.class.getDeclaredField("subLoop");
        field.setAccessible(true);
        AgentLoop childLoop = (AgentLoop) field.get(child);
        assertTrue(childLoop.isPlanMode());
    }

    private static TestLoopraProvider recordingProvider() {
        return TestLoopraProvider.builder()
                .model("test-model")
                .stream(request -> TestLoopraProvider.contentStream("done"))
                .build();
    }

    private static String lastAffinity(TestLoopraProvider provider) {
        List<String> affinities = provider.sessionAffinities();
        return affinities.get(affinities.size() - 1);
    }
}
