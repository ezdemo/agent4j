package site.sorghum.agent4j.tool.solon.common;

import site.sorghum.agent4j.bin.agent.model.FileChange;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Collects the net text changes made by TerminalTalent write/edit calls for one chat turn.
 */
public final class SessionFileChangeTracker {
    private static final ThreadLocal<String> CURRENT_SCOPE = new ThreadLocal<>();
    private static final ConcurrentHashMap<String, ConcurrentHashMap<String, ChangeSnapshot>> CHANGES =
            new ConcurrentHashMap<>();

    private SessionFileChangeTracker() {
    }

    public static void beginTurn(Path workspace, String sessionId) {
        String scope = scope(workspace, sessionId);
        CHANGES.remove(scope);
    }

    public static void bind(Path workspace, String sessionId) {
        CURRENT_SCOPE.set(scope(workspace, sessionId));
    }

    public static void clearBinding() {
        CURRENT_SCOPE.remove();
    }

    public static void record(String filePath, String before, String after, boolean created) {
        String scope = CURRENT_SCOPE.get();
        if (scope == null || before.equals(after)) return;

        CHANGES.computeIfAbsent(scope, ignored -> new ConcurrentHashMap<>())
                .compute(filePath, (ignored, existing) -> existing == null
                        ? new ChangeSnapshot(before, after, created)
                        : existing.withAfter(after));
    }

    public static List<FileChange> drain(Path workspace, String sessionId) {
        Map<String, ChangeSnapshot> changes = CHANGES.remove(scope(workspace, sessionId));
        if (changes == null || changes.isEmpty()) return List.of();

        List<FileChange> result = new ArrayList<>();
        changes.forEach((path, snapshot) -> {
            DiffSnapshot diff = DiffSnapshot.of(path, snapshot.before(), snapshot.after());
            LineStats stats = diff.stats();
            if (stats.additions() > 0 || stats.deletions() > 0) {
                result.add(new FileChange(path, stats.additions(), stats.deletions(), snapshot.created(), diff.unified()));
            }
        });
        result.sort(Comparator.comparing(FileChange::path));
        return result;
    }

    private static String scope(Path workspace, String sessionId) {
        String root = workspace == null ? "" : workspace.toAbsolutePath().normalize().toString();
        return root + "::" + (sessionId == null ? "default" : sessionId);
    }

    private record ChangeSnapshot(String before, String after, boolean created) {
        ChangeSnapshot withAfter(String value) {
            return new ChangeSnapshot(before, value, created);
        }
    }

    /** A compact line-based LCS diff used only for the visual add/remove summary. */
    private record LineStats(int additions, int deletions) {
        private static LineStats of(String before, String after) {
            String[] oldLines = lines(before);
            String[] newLines = lines(after);
            if ((long) oldLines.length * newLines.length > 4_000_000L) {
                return afterLargeChange(oldLines, newLines);
            }
            int[][] lcs = new int[oldLines.length + 1][newLines.length + 1];
            for (int i = oldLines.length - 1; i >= 0; i--) {
                for (int j = newLines.length - 1; j >= 0; j--) {
                    lcs[i][j] = oldLines[i].equals(newLines[j])
                            ? lcs[i + 1][j + 1] + 1
                            : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
            int common = lcs[0][0];
            return new LineStats(newLines.length - common, oldLines.length - common);
        }

        private static LineStats afterLargeChange(String[] oldLines, String[] newLines) {
            int prefix = 0;
            while (prefix < oldLines.length && prefix < newLines.length
                    && oldLines[prefix].equals(newLines[prefix])) {
                prefix++;
            }
            int suffix = 0;
            while (suffix < oldLines.length - prefix && suffix < newLines.length - prefix
                    && oldLines[oldLines.length - suffix - 1].equals(newLines[newLines.length - suffix - 1])) {
                suffix++;
            }
            return new LineStats(newLines.length - prefix - suffix, oldLines.length - prefix - suffix);
        }

        private static String[] lines(String content) {
            if (content.isEmpty()) return new String[0];
            String[] raw = content.split("\\R", -1);
            int length = content.endsWith("\n") || content.endsWith("\r") ? raw.length - 1 : raw.length;
            return Arrays.copyOf(raw, length);
        }
    }

    private record DiffSnapshot(LineStats stats, String unified) {
        private static DiffSnapshot of(String path, String before, String after) {
            String[] oldLines = LineStats.lines(before);
            String[] newLines = LineStats.lines(after);
            StringBuilder out = new StringBuilder("--- a/").append(path).append('\n')
                    .append("+++ b/").append(path).append('\n')
                    .append("@@ -1,").append(oldLines.length).append(" +1,").append(newLines.length).append(" @@\n");
            if ((long) oldLines.length * newLines.length > 4_000_000L) {
                for (String line : oldLines) out.append('-').append(line).append('\n');
                for (String line : newLines) out.append('+').append(line).append('\n');
                return new DiffSnapshot(new LineStats(newLines.length, oldLines.length), out.toString());
            }
            int[][] lcs = new int[oldLines.length + 1][newLines.length + 1];
            for (int i = oldLines.length - 1; i >= 0; i--) for (int j = newLines.length - 1; j >= 0; j--) {
                lcs[i][j] = oldLines[i].equals(newLines[j]) ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
            int additions = 0, deletions = 0, i = 0, j = 0;
            while (i < oldLines.length || j < newLines.length) {
                if (i < oldLines.length && j < newLines.length && oldLines[i].equals(newLines[j])) {
                    out.append(' ').append(oldLines[i++]).append('\n'); j++;
                } else if (j < newLines.length && (i == oldLines.length || lcs[i][j + 1] >= lcs[i + 1][j])) {
                    out.append('+').append(newLines[j++]).append('\n'); additions++;
                } else {
                    out.append('-').append(oldLines[i++]).append('\n'); deletions++;
                }
            }
            return new DiffSnapshot(new LineStats(additions, deletions), out.toString());
        }
    }
}
