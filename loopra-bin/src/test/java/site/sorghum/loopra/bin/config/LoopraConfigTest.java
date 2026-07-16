package site.sorghum.loopra.bin.config;

import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoopraConfigTest {

    @Test
    void terminatesOnNoToolCallByDefault() throws Exception {
        assertTrue(config("{}").terminateOnNoToolCall());
    }

    @Test
    void supportsContinuingAfterNoToolCallWhenConfigured() throws Exception {
        assertFalse(config("{\"terminateOnNoToolCall\":false}").terminateOnNoToolCall());
    }

    @Test
    void migratesTaskToolConfigurationToSubAgent() throws Exception {
        LoopraConfig config = config("""
                {"autoWhitelist":["task","goal_mark_step","read"],"disabledTools":["task","goal_mark_step"]}
                """);

        assertTrue(config.autoWhitelist().contains("sub_agent"));
        assertTrue(config.autoWhitelist().contains("goal_update_step"));
        assertFalse(config.autoWhitelist().contains("task"));
        assertFalse(config.autoWhitelist().contains("goal_mark_step"));
        assertTrue(config.disabledTools().contains("sub_agent"));
        assertTrue(config.disabledTools().contains("goal_update_step"));
        assertFalse(config.disabledTools().contains("task"));
        assertFalse(config.disabledTools().contains("goal_mark_step"));
    }

    private static LoopraConfig config(String json) throws Exception {
        Constructor<LoopraConfig> constructor = LoopraConfig.class.getDeclaredConstructor(ONode.class);
        constructor.setAccessible(true);
        return constructor.newInstance(ONode.ofJson(json));
    }
}
