package site.sorghum.loopra.bin.tool;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.noear.snack4.ONode;
import site.sorghum.loopra.bin.agent.core.AgentLoop;
import site.sorghum.loopra.bin.agent.core.LoopraAgent;
import site.sorghum.loopra.bin.agent.model.LoopraChatMessage;
import site.sorghum.loopra.bin.agent.prompt.PromptPrefix;
import site.sorghum.loopra.bin.command.ChatCommand;
import site.sorghum.loopra.bin.command.ChatCommandContext;
import site.sorghum.loopra.bin.command.MessageWrapper;
import site.sorghum.loopra.bin.command.impl.ExecuteCommand;
import site.sorghum.loopra.bin.command.impl.PlanCommand;
import site.sorghum.loopra.bin.config.LoopraConfig;
import site.sorghum.loopra.bin.model.ModelClient;
import site.sorghum.loopra.bin.session.JsonlSessionStore;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PlanCommandTest {

    private static Path home;

    @TempDir
    Path workspace;

    private String originalUserHome;
    private JsonlSessionStore store;
    private LoopraAgent agent;
    private ToolSystemInitializer.Result toolSystem;

    @BeforeAll
    static void createIsolatedHome() throws IOException {
        home = Files.createTempDirectory("loopra-plan-command-home");
    }

    @BeforeEach
    void setUp() throws Exception {
        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());

        ToolRegistry registry = new ToolRegistry().setDisabledTools(Set.of());
        registry.setRefreshContext(workspace, null, null, List.of());
        PromptPrefix prefix = new PromptPrefix("test", registry.toOpenAiTools());
        toolSystem = new ToolSystemInitializer.Result(registry, prefix, "test");
        store = new JsonlSessionStore(workspace.resolve("sessions"));
        agent = createAgent();
    }

    @AfterEach
    void tearDown() {
        if (agent != null) agent.dispose();
        if (store != null) store.shutdown();
        System.setProperty("user.home", originalUserHome);
    }

    @Test
    void submittedPlanIsApprovedAndInjectedForExecution() throws Exception {
        ChatCommandContext context = new ChatCommandContext(agent, new Scanner(""), () -> {});
        MessageWrapper planning = new MessageWrapper("/plan inspect authentication");

        ChatCommand.CommandResult planResult = new PlanCommand().execute(planning, context);

        assertEquals(ChatCommand.CommandResult.LOOP, planResult);
        assertEquals("inspect authentication", planning.getMessage());
        assertTrue(agent.isPlanMode());
        assertTrue(store.isPlanMode(store.currentName()));

        loopOf(agent).submitPlan("1. inspect\n2. implement");
        MessageWrapper execute = new MessageWrapper("/execute");
        ChatCommand.CommandResult executeResult = new ExecuteCommand().execute(execute, context);

        assertEquals(ChatCommand.CommandResult.LOOP, executeResult);
        assertTrue(execute.getMessage().contains("1. inspect\n2. implement"));
        assertFalse(agent.isPlanMode());
        assertFalse(store.isPlanMode(store.currentName()));
        assertNull(agent.consumePendingPlan());
    }

    @Test
    void webApprovalCanRestorePendingPlanBeforeExecutionStarts() throws Exception {
        agent.setPlanMode(true);
        loopOf(agent).submitPlan("1. inspect\n2. implement");

        String executionMessage = agent.preparePendingPlanExecution();
        assertNotNull(executionMessage);
        assertFalse(agent.isPlanMode());
        assertEquals("1. inspect\n2. implement", agent.getPendingPlan());

        agent.restorePendingPlanExecution();
        assertTrue(agent.isPlanMode());
        assertEquals("1. inspect\n2. implement", agent.getPendingPlan());

        agent.preparePendingPlanExecution();
        agent.completePendingPlanExecution();
        assertNull(agent.getPendingPlan());
    }

    @Test
    void pendingPlanSurvivesAgentRebuild() throws Exception {
        agent.setPlanMode(true);
        loopOf(agent).submitPlan("1. inspect\n2. implement");
        String sessionName = store.currentName();

        agent.dispose();
        agent = createAgent();
        agent.bindSession(sessionName);

        assertTrue(agent.isPlanMode());
        MessageWrapper execute = new MessageWrapper("/execute");
        ChatCommand.CommandResult result = new ExecuteCommand().execute(
                execute, new ChatCommandContext(agent, new Scanner(""), () -> {}));
        assertEquals(ChatCommand.CommandResult.LOOP, result);
        assertTrue(execute.getMessage().contains("1. inspect\n2. implement"));
        assertNull(store.getPendingPlan(sessionName));
    }

    private LoopraAgent createAgent() {
        return LoopraAgent.builder()
                .loopraConfig(LoopraConfig.load())
                .modelClient(new NoOpModelClient())
                .workspace(workspace)
                .sessionStore(store)
                .toolSystem(toolSystem)
                .buildLightweight();
    }

    private static AgentLoop loopOf(LoopraAgent agent) throws Exception {
        Field field = LoopraAgent.class.getDeclaredField("loop");
        field.setAccessible(true);
        return (AgentLoop) field.get(agent);
    }

    private static final class NoOpModelClient implements ModelClient {
        @Override
        public ONode chat(List<LoopraChatMessage> messages, ONode tools) {
            return null;
        }

        @Override
        public void chatStream(List<LoopraChatMessage> messages, ONode tools, StreamCallback callback) {
            callback.onContentDelta("done");
            callback.onDone();
        }

        @Override
        public String getModel() {
            return "test";
        }

        @Override
        public void setModel(String model) {
        }
    }
}
