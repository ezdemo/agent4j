package site.sorghum.loopra.bin.model;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import site.sorghum.cutin.core.context.Message;
import site.sorghum.cutin.core.model.ModelCallRequest;
import site.sorghum.cutin.integrations.model.AnthropicMessagesProvider;
import site.sorghum.cutin.integrations.model.OpenAiChatCompletionsProvider;
import site.sorghum.cutin.integrations.model.OpenAiResponsesProvider;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LoopraModelProvider 直接实现 cutin ModelProvider 后的核心行为测试。
 */
class LoopraModelProviderTest {

    @Test
    void defaultsToChatCompletionsProtocol() {
        LoopraModelProvider provider = provider("openai", "chat_completions", "gpt-4o");
        assertInstanceOf(OpenAiChatCompletionsProvider.class, provider.provider());
    }

    @Test
    void supportsResponsesProtocol() {
        LoopraModelProvider provider = provider("openai", "responses", "gpt-5");
        assertInstanceOf(OpenAiResponsesProvider.class, provider.provider());
    }

    @Test
    void supportsAnthropicProtocol() {
        LoopraModelProvider provider = provider("anthropic", "anthropic", "claude-3-7-sonnet");
        assertInstanceOf(AnthropicMessagesProvider.class, provider.provider());
    }

    @Test
    void stripsContextSizeSuffixForProviderAndCapabilities() {
        LoopraModelProvider provider = provider("openai", "chat_completions", "mimo-v2.5[512k]");
        assertEquals("mimo-v2.5[512k]", provider.getModel());
        assertEquals("mimo-v2.5", provider.effectiveModel());
        assertTrue(provider.capabilities().models().contains("mimo-v2.5"));
    }

    @Test
    void forkKeepsProtocolAndModelConfig() {
        LoopraModelProvider provider = provider("openai", "responses", "mimo-v2.5[512k]");
        LoopraModelProvider fork = provider.fork();

        assertEquals("mimo-v2.5[512k]", fork.getModel());
        assertEquals("mimo-v2.5", fork.effectiveModel());
        assertInstanceOf(OpenAiResponsesProvider.class, fork.provider());
    }

    @BeforeEach
    void clearLogSession() {
        LoopraModelProvider.CURRENT_LOG_SESSION.remove();
    }
    
    @AfterEach
    void clearLogSessionAfter() {
        LoopraModelProvider.CURRENT_LOG_SESSION.remove();
    }
    
    @Test
    void preparesReasoningEffortForResponsesRequests() {
        LoopraModelProvider provider = provider("openai", "responses", "gpt-5");
        ModelCallRequest prepared = provider.prepareRequest(new ModelCallRequest(
            "gpt-5",
            List.of(new Message("user", "hi")),
            List.of(),
            Map.of()
        ));

        assertEquals("high", prepared.options().get("reasoningEffort"));
    }

    @Test
    void fallsBackToLogSessionForSessionAffinity() {
        LoopraModelProvider provider = provider("openai", "responses", "gpt-5");
        LoopraModelProvider.CURRENT_LOG_SESSION.set("session-abc");
        ModelCallRequest prepared = provider.prepareRequest(new ModelCallRequest(
            "gpt-5",
            List.of(new Message("user", "hi")),
            List.of(),
            Map.of()
        ));

        assertEquals("session-abc", prepared.options().get("sessionAffinity"));
    }

    @Test
    void explicitSessionAffinityWinsOverLogSessionFallback() {
        LoopraModelProvider provider = provider("openai", "responses", "gpt-5");
        LoopraModelProvider.CURRENT_LOG_SESSION.set("log-session");
        provider.setSessionAffinity("explicit-session");
        ModelCallRequest prepared = provider.prepareRequest(new ModelCallRequest(
            "gpt-5",
            List.of(new Message("user", "hi")),
            List.of(),
            Map.of()
        ));

        assertEquals("explicit-session", prepared.options().get("sessionAffinity"));
    }

    @Test
    void skipsSessionAffinityWhenExplicitAndLogSessionBothAbsent() {
        LoopraModelProvider provider = provider("openai", "responses", "gpt-5");
        ModelCallRequest prepared = provider.prepareRequest(new ModelCallRequest(
            "gpt-5",
            List.of(new Message("user", "hi")),
            List.of(),
            Map.of()
        ));

        assertEquals(null, prepared.options().get("sessionAffinity"));
    }

    @Test
    void keepsRequestCarriedSessionAffinityWhenExplicitAndLogSessionAbsent() {
        LoopraModelProvider provider = provider("openai", "responses", "gpt-5");
        ModelCallRequest prepared = provider.prepareRequest(new ModelCallRequest(
            "gpt-5",
            List.of(new Message("user", "hi")),
            List.of(),
            Map.of("sessionAffinity", "session-abc")
        ));

        assertEquals("session-abc", prepared.options().get("sessionAffinity"));
    }

    @Test
    void explicitSessionAffinityOverridesRequestCarriedValue() {
        LoopraModelProvider provider = provider("openai", "responses", "gpt-5");
        provider.setSessionAffinity("sub-agent:nonce");
        ModelCallRequest prepared = provider.prepareRequest(new ModelCallRequest(
            "gpt-5",
            List.of(new Message("user", "hi")),
            List.of(),
            Map.of("sessionAffinity", "parent-session")
        ));

        assertEquals("sub-agent:nonce", prepared.options().get("sessionAffinity"));
    }

    private static LoopraModelProvider provider(String channelId, String protocol, String model) {
        return new LoopraModelProvider(
                "http://localhost/v1",
                "test-key",
                model,
                "high",
                channelId,
                protocol);
    }
}
