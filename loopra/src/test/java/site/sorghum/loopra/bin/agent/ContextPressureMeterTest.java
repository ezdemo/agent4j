package site.sorghum.loopra.bin.agent;

import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;
import site.sorghum.loopra.bin.agent.context.ContextCompactionPolicy;
import site.sorghum.loopra.bin.agent.context.ContextPressure;
import site.sorghum.loopra.bin.agent.context.ContextPressureMeter;
import site.sorghum.loopra.bin.agent.model.ChatMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContextPressureMeterTest {

    @Test
    void usesServerPromptUsageWhenOfflineEstimateIsLower() {
        ContextPressure pressure = measure(10, 20_000, 256_000);
        assertEquals(20_000, pressure.effectivePromptTokens());
    }

    @Test
    void triggersAtEightyPercentOfContextWindow() {
        ContextPressure under = measure(10, 100_000, 128_000);
        assertEquals(102_400, under.thresholdTokens());
        assertEquals(20_480, under.retainTokens());
        assertFalse(under.shouldCompact());

        ContextPressure overflow = measure(10, 110_000, 128_000);
        assertTrue(overflow.shouldCompact());
    }

    private static ContextPressure measure(int contentLength, int lastPromptTokens, int contextWindow) {
        ChatMessage message = ChatMessage.ofUser("x".repeat(contentLength));
        return ContextPressureMeter.measure(
                List.of(message),
                new ONode(),
                null,
                lastPromptTokens,
                contextWindow,
                ContextCompactionPolicy.defaults()
        );
    }
}
