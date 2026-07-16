package site.sorghum.agent4j.tool.solon.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionFileChangeTrackerTest {
    @TempDir
    Path workspace;

    @Test
    void reportsNetChangeForRepeatedEditsOfTheSameFile() {
        SessionFileChangeTracker.beginTurn(workspace, "session-a");
        SessionFileChangeTracker.bind(workspace, "session-a");
        try {
            SessionFileChangeTracker.record("src/App.java", "one\ntwo\n", "one\ntwo\nthree\n", false);
            SessionFileChangeTracker.record("src/App.java", "one\ntwo\nthree\n", "one\nthree\n", false);
        } finally {
            SessionFileChangeTracker.clearBinding();
        }

        List<SessionFileChangeTracker.FileChange> changes =
                SessionFileChangeTracker.drain(workspace, "session-a");

        assertEquals(1, changes.size());
        assertEquals("src/App.java", changes.get(0).path());
        assertEquals(1, changes.get(0).additions());
        assertEquals(1, changes.get(0).deletions());
    }
}
