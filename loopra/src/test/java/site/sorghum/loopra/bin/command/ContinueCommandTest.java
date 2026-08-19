package site.sorghum.loopra.bin.command;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import site.sorghum.loopra.bin.agent.core.LoopraAgent;
import site.sorghum.loopra.bin.agent.environment.SessionEnvironment;
import site.sorghum.loopra.bin.agent.model.ChatMessage;
import site.sorghum.loopra.bin.agent.model.UserMessage;
import site.sorghum.loopra.bin.agent.spi.ToolPolicyProvider;
import site.sorghum.loopra.bin.command.impl.ContinueCommand;
import site.sorghum.loopra.bin.config.LoopraConfig;
import site.sorghum.loopra.bin.model.TestLoopraProvider;
import site.sorghum.loopra.integration.cutin.CutinMessageBridge;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContinueCommandTest {

    @TempDir
    Path workspace;

    @Test
    void continuesReasoningWithoutAppendingAUserMessage() throws Exception {
        List<List<ChatMessage>> requests = new ArrayList<>();
        AtomicInteger turn = new AtomicInteger();
        TestLoopraProvider provider = TestLoopraProvider.builder()
                .stream(request -> {
                    requests.add(CutinMessageBridge.toLoopra(request.messages()));
                    return TestLoopraProvider.contentStream("answer-" + turn.incrementAndGet());
                })
                .build();

        LoopraAgent agent = LoopraAgent.builder()
                .config(LoopraConfig.load())
                .modelProvider(provider)
                .environment(SessionEnvironment.local(workspace))
                .commandRegistry(registryWith(new ContinueCommand()))
                .toolPolicyProvider(EMPTY_TOOL_POLICY)
                .buildLightweight();
        try {
            assertEquals("answer-1", agent.chat(UserMessage.of("original task")));
            assertEquals("answer-2", agent.chat(UserMessage.of("/continue")));

            List<ChatMessage> continued = requests.get(1);
            assertEquals(1, continued.stream().filter(ChatMessage::isUser).count());
            assertEquals("original task", continued.stream()
                    .filter(ChatMessage::isUser)
                    .findFirst()
                    .orElseThrow()
                    .getContent());
        } finally {
            agent.dispose();
        }
    }

    private static final ToolPolicyProvider EMPTY_TOOL_POLICY = new ToolPolicyProvider() {
        @Override
        public Set<String> disabledTools() {
            return Set.of();
        }

        @Override
        public Map<String, Boolean> toolReadOnlyOverrides() {
            return Map.of();
        }
    };

    private static ChatCommandRegistry registryWith(ChatCommand command) throws Exception {
        ChatCommandRegistry registry = new ChatCommandRegistry();
        Field commands = ChatCommandRegistry.class.getDeclaredField("commands");
        commands.setAccessible(true);
        commands.set(registry, List.of(command));
        registry.init();
        return registry;
    }
}
