package site.sorghum.loopra.bin.model;

import org.junit.jupiter.api.Test;
import site.sorghum.cutin.integrations.model.AnthropicMessagesProvider;
import site.sorghum.cutin.integrations.model.OpenAiChatCompletionsProvider;
import site.sorghum.cutin.integrations.model.OpenAiResponsesProvider;

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
