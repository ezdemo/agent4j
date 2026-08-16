package site.sorghum.loopra.bin.agent;

import org.junit.jupiter.api.Test;
import site.sorghum.loopra.bin.agent.context.ContextCompactionPolicy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContextCompactionPolicyTest {

    @Test
    void defaultsUseTokenBudgetRatios() {
        ContextCompactionPolicy policy = ContextCompactionPolicy.defaults();
        assertEquals(0.8, policy.thresholdRatio());
        assertEquals(0.16, policy.retainRatio());
        assertEquals(1, policy.compactionRetries());
    }

    @Test
    void rejectsRetentionAtOrAboveThreshold() {
        assertThrows(IllegalArgumentException.class, () ->
                new ContextCompactionPolicy(0.5, 0.5, 1));
        assertThrows(IllegalArgumentException.class, () ->
                new ContextCompactionPolicy(0.5, 0.6, 1));
    }

    @Test
    void rejectsInvalidRatios() {
        assertThrows(IllegalArgumentException.class, () ->
                new ContextCompactionPolicy(0, 0.1, 1));
        assertThrows(IllegalArgumentException.class, () ->
                new ContextCompactionPolicy(1.1, 0.1, 1));
    }
}
