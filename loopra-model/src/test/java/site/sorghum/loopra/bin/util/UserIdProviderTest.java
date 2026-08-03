package site.sorghum.loopra.bin.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserIdProviderTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        System.setProperty("user.home", tempDir.toString());
        UserIdProvider.resetForTesting();
    }

    @Test
    void shouldCreateUserIdFileWhenAbsent() {
        String userId = UserIdProvider.getUserId();

        assertNotNull(userId);
        assertDoesNotThrow(() -> UUID.fromString(userId));

        Path userIdFile = tempDir.resolve(".loopra").resolve(".user_id");
        assertTrue(Files.exists(userIdFile));
        assertDoesNotThrow(() -> assertEquals(userId, Files.readString(userIdFile).trim()));
    }

    @Test
    void shouldReuseExistingValidUserId() throws IOException {
        Path loopraDir = tempDir.resolve(".loopra");
        Files.createDirectories(loopraDir);
        String existing = UUID.randomUUID().toString();
        Files.writeString(loopraDir.resolve(".user_id"), existing + System.lineSeparator());

        String userId = UserIdProvider.getUserId();

        assertEquals(existing, userId);
    }

    @Test
    void shouldRegenerateWhenFileEmpty() throws IOException {
        Path loopraDir = tempDir.resolve(".loopra");
        Files.createDirectories(loopraDir);
        Files.writeString(loopraDir.resolve(".user_id"), "");

        String userId = UserIdProvider.getUserId();

        assertNotNull(userId);
        assertDoesNotThrow(() -> UUID.fromString(userId));
    }

    @Test
    void shouldRegenerateWhenFileInvalid() throws IOException {
        Path loopraDir = tempDir.resolve(".loopra");
        Files.createDirectories(loopraDir);
        Files.writeString(loopraDir.resolve(".user_id"), "invalid");

        String userId = UserIdProvider.getUserId();

        assertNotNull(userId);
        assertDoesNotThrow(() -> UUID.fromString(userId));
    }
}
