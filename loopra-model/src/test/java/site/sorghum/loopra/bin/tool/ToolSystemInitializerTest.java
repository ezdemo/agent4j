package site.sorghum.loopra.bin.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ToolSystemInitializerTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsSupportedProjectRuleFilesCaseInsensitively() throws Exception {
        Files.writeString(tempDir.resolve("AGENTS.md"), "# Agents rule");
        Files.writeString(tempDir.resolve("CLAUDE.md"), "# Claude rule");
        Files.writeString(tempDir.resolve("loopra.md"), "# Loopra rule");

        String result = ToolSystemInitializer.loadProjectMd(tempDir);

        assertTrue(result.contains("# Agents rule"));
        assertTrue(result.contains("# Claude rule"));
        assertTrue(result.contains("# Loopra rule"));
    }

    @Test
    void loadsLowercaseAgentMdVariant() throws Exception {
        Files.writeString(tempDir.resolve("agent.md"), "agent instructions");

        String result = ToolSystemInitializer.loadProjectMd(tempDir);

        assertTrue(result.contains("agent instructions"));
    }

    @Test
    void returnsEmptyWhenProjectDirectoryDoesNotExist() {
        assertEquals("", ToolSystemInitializer.loadProjectMd(tempDir.resolve("missing")));
        assertEquals("", ToolSystemInitializer.loadProjectMd(null));
    }

    @Test
    void ignoresUnrelatedFiles() throws Exception {
        Files.writeString(tempDir.resolve("README.md"), "not a rule");

        assertEquals("", ToolSystemInitializer.loadProjectMd(tempDir));
    }
}
