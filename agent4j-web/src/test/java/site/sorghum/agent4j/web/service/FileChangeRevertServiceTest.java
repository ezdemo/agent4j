package site.sorghum.agent4j.web.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import site.sorghum.agent4j.bin.agent.model.FileChange;
import site.sorghum.agent4j.web.common.ServiceException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileChangeRevertServiceTest {
    @TempDir
    Path workspace;

    private final FileChangeRevertService service = new FileChangeRevertService();

    @Test
    void revertsAnEditedFileFromItsSavedDiff() throws Exception {
        Path file = workspace.resolve("src/App.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "new\n", StandardCharsets.UTF_8);

        service.revert(workspace, List.of(change("src/App.java", false, patch("src/App.java", "old", "new"))));

        assertEquals("old\n", Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void deletesAFileCreatedByThisAssistantReply() throws Exception {
        Path file = workspace.resolve("src/New.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "new\n", StandardCharsets.UTF_8);

        service.revert(workspace, List.of(change("src/New.java", true, patch("src/New.java", "", "new"))));

        assertFalse(Files.exists(file));
    }

    @Test
    void rejectsRevertWhenTheFileChangedAfterTheReply() throws Exception {
        Path file = workspace.resolve("App.java");
        Files.writeString(file, "changed later\n", StandardCharsets.UTF_8);

        assertThrows(ServiceException.class,
                () -> service.revert(workspace, List.of(change("App.java", false, patch("App.java", "old", "new")))));

        assertEquals("changed later\n", Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void reversesConcatenatedChangesInReverseOrder() throws Exception {
        Path file = workspace.resolve("App.java");
        Files.writeString(file, "new\n", StandardCharsets.UTF_8);
        String diff = patch("App.java", "old", "middle") + patch("App.java", "middle", "new");

        service.revert(workspace, List.of(change("App.java", false, diff)));

        assertEquals("old\n", Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void preservesAFileWithoutFinalNewline() throws Exception {
        Path file = workspace.resolve("App.java");
        Files.writeString(file, "new", StandardCharsets.UTF_8);
        String diff = "--- a/App.java\n+++ b/App.java\n@@ -1,1 +1,1 @@\n-old\n"
                + "\\ No newline at end of file\n+new\n\\ No newline at end of file\n";

        service.revert(workspace, List.of(change("App.java", false, diff)));

        assertEquals("old", Files.readString(file, StandardCharsets.UTF_8));
    }

    private static FileChange change(String path, boolean created, String diff) {
        return new FileChange(path, 1, 1, created, diff);
    }

    private static String patch(String path, String before, String after) {
        StringBuilder diff = new StringBuilder("--- a/").append(path).append('\n')
                .append("+++ b/").append(path).append('\n')
                .append("@@ -1,").append(before.isEmpty() ? 0 : 1)
                .append(" +1,").append(after.isEmpty() ? 0 : 1).append(" @@\n");
        if (!before.isEmpty()) diff.append('-').append(before).append('\n');
        if (!after.isEmpty()) diff.append('+').append(after).append('\n');
        return diff.toString();
    }
}
