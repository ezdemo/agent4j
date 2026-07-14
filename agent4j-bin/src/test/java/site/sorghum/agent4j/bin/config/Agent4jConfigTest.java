package site.sorghum.agent4j.bin.config;

import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Agent4jConfigTest {

    @Test
    void terminatesOnNoToolCallByDefault() throws Exception {
        assertTrue(config("{}").terminateOnNoToolCall());
    }

    @Test
    void supportsContinuingAfterNoToolCallWhenConfigured() throws Exception {
        assertFalse(config("{\"terminateOnNoToolCall\":false}").terminateOnNoToolCall());
    }

    private static Agent4jConfig config(String json) throws Exception {
        Constructor<Agent4jConfig> constructor = Agent4jConfig.class.getDeclaredConstructor(ONode.class);
        constructor.setAccessible(true);
        return constructor.newInstance(ONode.ofJson(json));
    }
}
