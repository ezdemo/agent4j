package site.sorghum.loopra.bin.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.noear.snack4.ONode;

import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoopraConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void createsDefaultConfigWithoutStoppingStartup() throws Exception {
        String originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());
        try {
            LoopraConfig config = LoopraConfig.load();

            assertTrue(Files.exists(tempDir.resolve(".loopra/config.json")));
            assertEquals("sk-your-api-key", config.apiKey());
            assertEquals(tempDir.resolve(".loopra/defaultWorkSpace").toAbsolutePath().normalize(),
                    config.workspaceDir());
        } finally {
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    void assignsDefaultWorkspaceWhenExistingConfigOmitsWorkspace() throws Exception {
        Path configDir = tempDir.resolve(".loopra");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve("config.json"), "{}");

        String originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());
        try {
            LoopraConfig config = LoopraConfig.load();
            Path expectedWorkspace = configDir.resolve("defaultWorkSpace").toAbsolutePath().normalize();

            assertEquals(expectedWorkspace, config.workspaceDir());
            assertTrue(Files.isDirectory(expectedWorkspace));
            assertEquals(expectedWorkspace.toString(), ONode.ofJson(Files.readString(configDir.resolve("config.json")))
                    .select("$.workspaceDir").getString());
        } finally {
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    void migratesLegacyAgent4jDataExceptJreAndBin() throws Exception {
        Path legacyDir = tempDir.resolve(".agent4j");
        Files.createDirectories(legacyDir.resolve("sessions"));
        Files.createDirectories(legacyDir.resolve("jre"));
        Files.createDirectories(legacyDir.resolve("jre25/bin"));
        Files.createDirectories(legacyDir.resolve("bin"));
        Files.writeString(legacyDir.resolve("config.json"), "{\"apiKey\":\"legacy-key\"}");
        Files.writeString(legacyDir.resolve("sessions/session.jsonl"), "legacy session");
        Files.writeString(legacyDir.resolve("jre/excluded.txt"), "excluded");
        Files.writeString(legacyDir.resolve("jre25/bin/extnet.dll"), "excluded runtime");
        Files.writeString(legacyDir.resolve("bin/excluded.txt"), "excluded");

        String originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());
        try {
            LoopraConfig config = LoopraConfig.load();
            Path configDir = tempDir.resolve(".loopra");

            assertEquals("legacy-key", config.apiKey());
            assertEquals("legacy session", Files.readString(configDir.resolve("sessions/session.jsonl")));
            assertFalse(Files.exists(configDir.resolve("jre")));
            assertFalse(Files.exists(configDir.resolve("jre25")));
            assertFalse(Files.exists(configDir.resolve("bin")));
            assertTrue(Files.exists(configDir.resolve(".agent4j-migration-complete")));
        } finally {
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    void migratesLegacyDataWhenInstallerAlreadyCreatedLoopraDirectory() throws Exception {
        Path legacyDir = tempDir.resolve(".agent4j");
        Files.createDirectories(legacyDir.resolve("sessions"));
        Files.writeString(legacyDir.resolve("config.json"), "{\"apiKey\":\"legacy-key\"}");
        Files.writeString(legacyDir.resolve("sessions/legacy.jsonl"), "legacy session");

        Path configDir = tempDir.resolve(".loopra");
        Files.createDirectories(configDir.resolve("bin"));
        Files.createDirectories(configDir.resolve("jre"));
        Files.writeString(configDir.resolve("bin/loopra.cmd"), "installed command");
        Files.writeString(configDir.resolve("jre/runtime.txt"), "installed runtime");
        Files.writeString(configDir.resolve("config.json"), "{\"apiKey\":\"sk-your-api-key\"}");

        String originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());
        try {
            LoopraConfig config = LoopraConfig.load();

            assertEquals("legacy-key", config.apiKey());
            assertEquals("legacy session", Files.readString(configDir.resolve("sessions/legacy.jsonl")));
            assertEquals("installed command", Files.readString(configDir.resolve("bin/loopra.cmd")));
            assertEquals("installed runtime", Files.readString(configDir.resolve("jre/runtime.txt")));
            assertTrue(Files.exists(configDir.resolve(".agent4j-migration-complete")));
        } finally {
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    void overwritesExistingLoopraDataOnFirstLegacyMigration() throws Exception {
        Path legacyDir = tempDir.resolve(".agent4j");
        Files.createDirectories(legacyDir.resolve("sessions"));
        Files.writeString(legacyDir.resolve("config.json"), "{\"apiKey\":\"legacy-key\"}");
        Files.writeString(legacyDir.resolve("sessions/existing.jsonl"), "legacy existing session");
        Files.writeString(legacyDir.resolve("sessions/missing.jsonl"), "legacy missing session");

        Path configDir = tempDir.resolve(".loopra");
        Files.createDirectories(configDir.resolve("sessions"));
        Files.writeString(configDir.resolve("config.json"), "{\"apiKey\":\"current-key\"}");
        Files.writeString(configDir.resolve("sessions/existing.jsonl"), "current session");

        String originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());
        try {
            LoopraConfig config = LoopraConfig.load();

            assertEquals("legacy-key", config.apiKey());
            assertEquals("legacy existing session", Files.readString(configDir.resolve("sessions/existing.jsonl")));
            assertEquals("legacy missing session", Files.readString(configDir.resolve("sessions/missing.jsonl")));
            assertTrue(Files.exists(configDir.resolve(".agent4j-migration-complete")));

            Files.writeString(configDir.resolve("sessions/existing.jsonl"), "changed after migration");
            Files.writeString(legacyDir.resolve("sessions/existing.jsonl"), "changed legacy session");
            LoopraConfig.load();

            assertEquals("changed after migration", Files.readString(configDir.resolve("sessions/existing.jsonl")));
        } finally {
            System.setProperty("user.home", originalUserHome);
        }
    }

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
