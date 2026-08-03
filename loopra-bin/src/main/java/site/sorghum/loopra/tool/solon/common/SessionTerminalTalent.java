package site.sorghum.loopra.tool.solon.common;

import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.ai.talents.cli.TerminalTalent;
import org.noear.solon.ai.talents.mount.MountManager;
import org.noear.solon.annotation.Param;
import site.sorghum.loopra.tool.AgentLoopController;
import site.sorghum.loopra.tool.ToolContext;

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

    @Override
    @ToolMapping(name = "bash", description = "在终端执行非交互式 Shell 指令。支持多行脚本，支持逻辑路径（如 @pool）自动转环境变量。")
    public String bash(@Param(value = "command", description = "要执行的指令。") String command,
                       @Param(name = "timeout", required = false, defaultValue = "120000") Integer timeout,
                       @Param(name = "max_output_chars", required = false, defaultValue = "64000") Integer maxOutputChars,
                       String __cwd) {
        int timeoutMs = timeout == null || timeout < 0 ? 120_000 : timeout;
        int outputLimit = maxOutputChars == null || maxOutputChars <= 0 ? 64_000 : maxOutputChars;
        final String result;
        try {
            result = super.bashStart(command, null, timeoutMs, outputLimit, timeoutMs, __cwd);
        } catch (IOException e) {
            return "系统失败: " + e.getMessage();
        }
        String sessionId = commandSessionId(result);
        AgentLoopController controller = ToolContext.getCurrentController();
        if (controller != null && sessionId != null && isCommandRunning(result)) {
            controller.registerAbortResource(sessionId,
                    () -> bashSessionManager.terminate(sessionId, "用户停止生成", 64_000));
        }
        if (result != null && result.contains("hard_timeout: true")) {
            return "执行超时：运行时间超过 " + timeoutMs + " 毫秒。";
        }
        return commandOutput(result);
    }

    @Override
    @ToolMapping(name = "bash_start", description = "启动 shell 命令会话。命令超过 yield-time_ms 仍未结束时不会失败，而是返回 session_id，后续可用 bash_wait 继续等待、bash_stdin 输入或 bash_stop 终止。")
    public String bashStart(@Param(value = "command", description = "要执行的 shell 命令。") String command,
                            @Param(value = "workdir", required = false, description = "工作目录。默认使用当前工作区。") String workdir,
                            @Param(value = "yield_time_ms", required = false, defaultValue = "1000") Integer yieldTimeMs,
                            @Param(value = "max_output_chars", required = false, defaultValue = "64000") Integer maxOutputChars,
                            @Param(value = "hard_timeout_ms", required = false, defaultValue = "120000") Integer hardTimeoutMs,
                            String __cwd) throws IOException {
        String result = super.bashStart(command, workdir, yieldTimeMs, maxOutputChars, hardTimeoutMs, __cwd);
        String sessionId = commandSessionId(result);
        AgentLoopController controller = ToolContext.getCurrentController();
        if (controller != null && sessionId != null && isCommandRunning(result)) {
            controller.registerAbortResource(sessionId,
                    () -> bashSessionManager.terminate(sessionId, "用户停止生成", 64_000));
        }
        return result;
    }

    @Override
    @ToolMapping(name = "bash_wait", description = "继续等待仍在运行的命令会话，返回自上次读取后的新增输出或最终状态。")
    public String bashWait(@Param(value = "session_id") String sessionId,
                           @Param(value = "yield_time_ms", required = false, defaultValue = "1000") Integer yieldTimeMs,
                           @Param(value = "max_output_chars", required = false, defaultValue = "64000") Integer maxOutputChars) throws IOException {
        String result = super.bashWait(sessionId, yieldTimeMs, maxOutputChars);
        clearCompletedCommand(sessionId, result);
        return result;
    }

    @Override
    @ToolMapping(name = "bash_stdin", description = "向仍在运行的命令会话写入 stdin，然后等待新增输出或进程结束。")
    public String bashStdin(@Param(value = "session_id") String sessionId,
                            @Param(value = "chars") String chars,
                            @Param(value = "yield_time_ms", required = false, defaultValue = "1000") Integer yieldTimeMs,
                            @Param(value = "max_output_chars", required = false, defaultValue = "64000") Integer maxOutputChars) throws IOException {
        String result = super.bashStdin(sessionId, chars, yieldTimeMs, maxOutputChars);
        clearCompletedCommand(sessionId, result);
        return result;
    }

    @Override
    @ToolMapping(name = "bash_stop", description = "终止仍在运行的命令会话及其子进程树。")
    public String bashStop(@Param(value = "session_id") String sessionId,
                           @Param(value = "reason", required = false) String reason,
                           @Param(value = "max_output_chars", required = false, defaultValue = "64000") Integer maxOutputChars) {
        try {
            return super.bashStop(sessionId, reason, maxOutputChars);
        } finally {
            AgentLoopController controller = ToolContext.getCurrentController();
            if (controller != null) controller.clearAbortResource(sessionId);
        }
    }

    private static String commandOutput(String result) {
        if (result == null) return "";
        String marker = "\nOutput:\n";
        int outputStart = result.indexOf(marker);
        return outputStart >= 0 ? result.substring(outputStart + marker.length()).trim() : result;
    }

    private static String commandSessionId(String result) {
        if (result == null) return null;
        for (String line : result.split("\\R")) {
            if (line.startsWith("session_id: ")) return line.substring("session_id: ".length()).trim();
        }
        return null;
    }

    private static boolean isCommandRunning(String result) {
        return result != null && result.contains("status: running");
    }

    private static void clearCompletedCommand(String sessionId, String result) {
        AgentLoopController controller = ToolContext.getCurrentController();
        if (controller != null && !isCommandRunning(result)) {
            controller.clearAbortResource(sessionId);
        }
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
