package site.sorghum.cutin.core.context;

import org.junit.jupiter.api.Test;
import site.sorghum.cutin.core.loop.DefaultLoopEngine;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultLoopContextWorkingDirectoryTest {

    @Test
    void carriesWorkingDirectoryIntoSnapshotAndRestoredContext() {
        DefaultLoopEngine engine = new DefaultLoopEngine();
        Path workingDirectory = Path.of(".").toAbsolutePath().normalize().resolve("project").normalize();

        DefaultLoopContext context = engine.newContext(
            "loop-1",
            Map.of("sessionId", "session-1"),
            workingDirectory
        );

        assertEquals(workingDirectory, context.workingDirectory());
        assertEquals(workingDirectory, context.snapshot().workingDirectory());
        assertEquals(workingDirectory, engine.restore(context.snapshot()).workingDirectory());
    }
}
