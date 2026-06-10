package site.sorghum.agent4j.web.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebErrorMessages 常量测试。
 */
class WebErrorMessagesTest {

    @Test
    void agentNotReadyIsNotEmpty() {
        assertNotNull(WebErrorMessages.AGENT_NOT_READY);
        assertFalse(WebErrorMessages.AGENT_NOT_READY.isEmpty());
    }

    @Test
    void allConstantsAreUnique() {
        String[] messages = {
                WebErrorMessages.AGENT_NOT_READY
        };
        for (int i = 0; i < messages.length; i++) {
            for (int j = i + 1; j < messages.length; j++) {
                assertNotEquals(messages[i], messages[j],
                        "error messages should be unique");
            }
        }
    }
}
