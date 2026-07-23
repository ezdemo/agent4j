package site.sorghum.loopra.tool.solon.common;

import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.ai.talents.cli.TerminalTalent;
import org.noear.solon.ai.talents.mount.MountManager;
import org.noear.solon.annotation.Param;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * TerminalTalent with passive write/edit change tracking. The underlying tool behavior is unchanged.
 */
public class SessionTerminalTalent extends TerminalTalent {
    private static final long MAX_TRACKED_BYTES = 512 * 1024;

    private final Path workspace;

    public SessionTerminalTalent(MountManager mountManager, String workspace) {
        super(mountManager);
        this.workspace = Path.of(workspace).toAbsolutePath().normalize();
    }

    @Override
    @ToolMapping(name = TOOL_WRITE, description = "创建新文件或覆盖现有文件。")
    public String write(@Param(value = "file_path", description = "文件相对路径（如 'src/demo.md'）。'.' 表示当前根目录。") String filePath,
                        @Param(value = PARAM_CONTENT, description = "完整文本内容。") String content,
                        String __cwd) throws IOException {
        return track(filePath, __cwd, () -> super.write(filePath, content, __cwd));
    }

    @Override
    @ToolMapping(name = TOOL_EDIT, description = "对文件进行精准文本替换。支持单次调用执行一处或多处编辑。具有原子性：所有编辑成功才会写入，否则全部回滚。")
    public String edit(@Param(value = "file_path", description = "文件相对路径（如 'src/demo.md'）。'.' 表示当前根目录。") String filePath,
                       @Param(value = PARAM_EDITS, description = "编辑操作列表") List<EditOp> edits,
                       String __cwd) throws IOException {
        return track(filePath, __cwd, () -> super.edit(filePath, edits, __cwd));
    }

    private String track(String filePath, String cwd, IoCall action) throws IOException {
        Path target = resolveTrackedPath(filePath, cwd);
        Snapshot before = readText(target);
        String result = action.call();
        Snapshot after = readText(target);
        if (before != null && after != null) {
            SessionFileChangeTracker.record(displayPath(filePath, target), before.content(), after.content(), !before.exists());
        }
        return result;
    }

    private Path resolveTrackedPath(String filePath, String cwd) {
        if (filePath == null || filePath.startsWith("@")) return null;
        try {
            Path path = Path.of(filePath);
            if (path.isAbsolute()) return path.normalize();
            Path root = cwd == null || cwd.isBlank() ? workspace : Path.of(cwd);
            return root.resolve(path).normalize();
        } catch (Exception ignored) {
            return null;
        }
    }

    private Snapshot readText(Path path) {
        try {
            if (path == null) return null;
            if (!Files.exists(path)) return new Snapshot(false, "");
            if (!Files.isRegularFile(path) || Files.size(path) > MAX_TRACKED_BYTES) return null;
            return new Snapshot(true, Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            return null;
        }
    }

    private String displayPath(String filePath, Path target) {
        try {
            if (target != null && target.startsWith(workspace)) {
                return workspace.relativize(target).toString().replace('\\', '/');
            }
        } catch (Exception ignored) {
            // Fall back to the tool argument.
        }
        return filePath == null ? "未知文件" : filePath.replace('\\', '/');
    }

    @FunctionalInterface
    private interface IoCall {
        String call() throws IOException;
    }

    private record Snapshot(boolean exists, String content) {
    }
}
