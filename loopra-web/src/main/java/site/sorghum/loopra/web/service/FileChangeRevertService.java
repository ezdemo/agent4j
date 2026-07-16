package site.sorghum.loopra.web.service;

import site.sorghum.loopra.bin.agent.model.FileChange;
import site.sorghum.loopra.web.common.ServiceException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reverses the complete-file unified diffs recorded by {@code SessionFileChangeTracker}.
 *
 * <p>Every affected file is verified against the saved post-change content before anything
 * is written. This deliberately refuses a revert after later edits, instead of overwriting
 * those edits.</p>
 */
public class FileChangeRevertService {
    private static final Pattern HUNK_HEADER = Pattern.compile("@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@");

    public int revert(Path workspace, List<FileChange> changes) {
        if (changes == null || changes.isEmpty()) {
            throw new ServiceException("没有可撤销的文件变更");
        }

        Path root = workspace.toAbsolutePath().normalize();
        List<RevertPlan> plans = new ArrayList<>();
        Set<Path> seenFiles = new HashSet<>();
        for (FileChange change : changes) {
            if (change == null || change.path() == null || change.path().isBlank() || change.diff() == null || change.diff().isBlank()) {
                throw new ServiceException("文件变更记录不完整，无法撤销");
            }

            Path file = root.resolve(change.path()).normalize();
            if (!file.startsWith(root)) {
                throw new ServiceException("文件路径超出工作区: " + change.path());
            }
            if (!seenFiles.add(file)) {
                throw new ServiceException("同一文件不能重复撤销: " + change.path());
            }

            String current = readCurrent(file);
            String reverted = reverseAll(change.path(), change.diff(), current);
            plans.add(new RevertPlan(file, reverted, change.created() && reverted.isEmpty()));
        }

        // All patches above have already been verified. Only now make filesystem changes.
        try {
            for (RevertPlan plan : plans) {
                if (plan.deleteFile()) {
                    Files.deleteIfExists(plan.file());
                } else {
                    Path parent = plan.file().getParent();
                    if (parent != null) Files.createDirectories(parent);
                    Files.writeString(plan.file(), plan.content(), StandardCharsets.UTF_8);
                }
            }
        } catch (IOException e) {
            throw new ServiceException("回打文件补丁失败: " + e.getMessage());
        }
        return plans.size();
    }

    private String readCurrent(Path file) {
        try {
            if (!Files.exists(file)) return "";
            if (!Files.isRegularFile(file)) throw new ServiceException("不是普通文件: " + file.getFileName());
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ServiceException("读取文件失败: " + file.getFileName() + " (" + e.getMessage() + ")");
        }
    }

    private String reverseAll(String path, String diff, String current) {
        List<FullFilePatch> patches = parsePatches(path, diff);
        String result = current;
        for (int index = patches.size() - 1; index >= 0; index--) {
            FullFilePatch patch = patches.get(index);
            if (!patch.matchesAfter(result)) {
                throw new ServiceException("文件已在本次 AI 修改后发生变化，未撤销: " + path);
            }
            result = patch.beforeFor(result);
        }
        return result;
    }

    private List<FullFilePatch> parsePatches(String path, String diff) {
        List<FullFilePatch> patches = new ArrayList<>();
        String[] chunks = diff.split("(?m)(?=^--- a/)");
        for (String chunk : chunks) {
            if (chunk.isBlank()) continue;
            String[] lines = chunk.split("\\n", -1);
            if (lines.length < 3 || !lines[0].startsWith("--- a/") || !lines[1].startsWith("+++ b/")) {
                throw new ServiceException("文件变更记录格式无效，无法撤销: " + path);
            }
            Matcher header = HUNK_HEADER.matcher(lines[2]);
            if (!header.matches() || !"1".equals(header.group(1)) || !"1".equals(header.group(3))) {
                throw new ServiceException("仅支持完整文件变更记录，无法撤销: " + path);
            }
            int oldCount = count(header.group(2));
            int newCount = count(header.group(4));
            List<String> before = new ArrayList<>();
            List<String> after = new ArrayList<>();
            boolean beforeMissingFinalNewline = false;
            boolean afterMissingFinalNewline = false;
            char previousPrefix = 0;
            for (int i = 3; i < lines.length; i++) {
                String line = lines[i];
                if (line.isEmpty() && i == lines.length - 1) continue;
                if (line.equals("\\ No newline at end of file")) {
                    if (previousPrefix == ' ' || previousPrefix == '-') beforeMissingFinalNewline = true;
                    if (previousPrefix == ' ' || previousPrefix == '+') afterMissingFinalNewline = true;
                    continue;
                }
                if (line.isEmpty()) throw new ServiceException("文件变更记录格式无效，无法撤销: " + path);
                switch (line.charAt(0)) {
                    case ' ' -> {
                        before.add(line.substring(1));
                        after.add(line.substring(1));
                    }
                    case '-' -> before.add(line.substring(1));
                    case '+' -> after.add(line.substring(1));
                    default -> throw new ServiceException("文件变更记录格式无效，无法撤销: " + path);
                }
                previousPrefix = line.charAt(0);
            }
            if (before.size() != oldCount || after.size() != newCount) {
                throw new ServiceException("文件变更记录不完整，无法撤销: " + path);
            }
            patches.add(new FullFilePatch(before, after, beforeMissingFinalNewline, afterMissingFinalNewline));
        }
        if (patches.isEmpty()) throw new ServiceException("文件变更记录为空，无法撤销: " + path);
        return patches;
    }

    private int count(String value) {
        return value == null ? 1 : Integer.parseInt(value);
    }

    private record RevertPlan(Path file, String content, boolean deleteFile) {
    }

    /** Tracker diffs are full-file hunks. Older snapshots use the standard final-newline default. */
    private record FullFilePatch(List<String> beforeLines, List<String> afterLines,
                                 boolean beforeMissingFinalNewline, boolean afterMissingFinalNewline) {
        boolean matchesAfter(String content) {
            return content.equals(toContent(afterLines, afterMissingFinalNewline));
        }

        String beforeFor(String ignoredAfterContent) {
            return toContent(beforeLines, beforeMissingFinalNewline);
        }

        private static String toContent(List<String> lines, boolean missingFinalNewline) {
            if (lines.isEmpty()) return "";
            String content = String.join("\n", lines);
            return missingFinalNewline ? content : content + "\n";
        }
    }
}
