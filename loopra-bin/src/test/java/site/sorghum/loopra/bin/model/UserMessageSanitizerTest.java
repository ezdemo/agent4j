package site.sorghum.loopra.bin.model;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import site.sorghum.loopra.bin.agent.model.UserMessage;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class UserMessageSanitizerTest {

    @AfterEach
    void resetDependencies() {
        UserMessageSanitizer.modalityProvider = null;
        UserMessageSanitizer.visionService = null;
    }

    @Test
    void usesChannelFromModelClientWhenResolvingImageInputCapability() {
        AtomicReference<String> resolvedChannel = new AtomicReference<>();
        UserMessageSanitizer.modalityProvider = new ModelModalityProvider() {
            @Override
            public ModalitySupport _getModalitySupport(String modelName) {
                return ModalitySupport.TEXT_ONLY;
            }

            @Override
            public ModalitySupport getModalitySupport(String channelId, String modelName) {
                resolvedChannel.set(channelId);
                return new ModalitySupport(true, false, false, false, false, false, false, true, true);
            }
        };
        HttpModelClient client = new HttpModelClient(
                "https://api.example.test/v1/chat/completions", "key", "shared-model", "high", "vision-channel");
        UserMessage message = UserMessage.of("describe", List.of("https://example.test/image.png"));

        assertSame(message, UserMessageSanitizer.sanitize(message, client));
        assertEquals("vision-channel", resolvedChannel.get());
    }
}
