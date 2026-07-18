package site.sorghum.loopra.bin.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import site.sorghum.loopra.bin.agent.resilient.StormBreaker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StormBreakerTest {

    private StormBreaker breaker;

    @BeforeEach
    void setUp() {
        breaker = new StormBreaker();
    }

    @Test
    void suppressesThirdIdenticalCall() {
        String args = "{\"path\":\"a.java\",\"value\":1}";

        assertFalse(breaker.inspect("edit", args, false).suppressed());
        assertFalse(breaker.inspect("edit", args, false).suppressed());
        StormBreaker.SuppressResult result = breaker.inspect("edit", args, false);

        assertTrue(result.suppressed());
        assertTrue(result.reason().contains("风暴断路器"));
    }

    @Test
    void resetStartsANewWindow() {
        String args = "{\"path\":\"a.java\"}";
        breaker.inspect("edit", args, false);
        breaker.inspect("edit", args, false);
        breaker.reset();

        assertFalse(breaker.inspect("edit", args, false).suppressed());
    }

    @Test
    void differentToolCallResetsTheConsecutiveCounter() {
        String args = "{\"path\":\"a.java\",\"value\":1}";

        assertFalse(breaker.inspect("edit", args, false).suppressed());
        assertFalse(breaker.inspect("edit", args, false).suppressed());
        assertFalse(breaker.inspect("read", "{\"path\":\"a.java\"}", true).suppressed());
        assertFalse(breaker.inspect("edit", args, false).suppressed());
        assertFalse(breaker.inspect("edit", args, false).suppressed());
    }

    @Test
    void mutatingCallInvalidatesOlderReadHistory() {
        String args = "{\"path\":\"a.java\"}";
        breaker.inspect("read", args, true);
        breaker.inspect("read", args, true);
        breaker.inspect("edit", "{\"path\":\"b.java\"}", false);

        assertFalse(breaker.inspect("read", args, true).suppressed());
    }
}
