package site.sorghum.loopra.tool.solon.common;

import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.ai.talents.cli.TerminalSessionManager;
import org.noear.solon.ai.talents.cli.TerminalTalent;
import org.noear.solon.ai.talents.mount.MountManager;
import org.noear.solon.annotation.Param;
import site.sorghum.loopra.bin.session.SessionFileChangeTracker;
import site.sorghum.loopra.tool.AgentLoopController;
import site.sorghum.loopra.tool.ToolContext;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
  * 带被动写入/编辑变更追踪的 TerminalTalent。底层工具行为不变。
 */
public class SessionTerminalTalent extends TerminalTalent {
    private static final long MAX_TRACKED_BYTES = 512 * 1024;
    /** 单个后台会话累积输出日志的字符上限，超出时保留尾部。 */
    private static final int MAX_LOG_CHARS = 512 * 1024;
    /** 已结束会话在镜像中的保留时长 —— 供前端短暂展示“已结束”状态，与底层 10 分钟 TTL 无关。 */
    private static final long COMPLETED_VISIBLE_TTL_MS = 60_000;

    private final Path workspace;

    /** bash_start/bash_wait/bash_stdin/bash_stop 后台会话镜像（sessionId → 会话信息）。 */
    private final ConcurrentMap<String, BashSessionInfo> bashSessions = new ConcurrentHashMap<>();

    public SessionTerminalTalent(MountManager mountManager, String workspace) {
        super(mountManager);
        this.workspace = Path.of(workspace).toAbsolutePath().normalize();
        applyWindowsOutputCharsetFix();
    }

    /**
     * Windows 下 cmd 子进程输出采用系统 OEM 代码页（中文系统为 GBK/936），而底层
     * TerminalSessionManager 固定用 UTF-8 解码，导致中文输出乱码（bash 工具与后台会话日志均受影响）。
     * 通过反射将 bashSessionManager 替换为按活动代码页构造的实例；非 Windows 或失败时保持默认行为。
     */
    private void applyWindowsOutputCharsetFix() {
        if (!isWindowsOs()) return;
        String codePage = queryWindowsCodePage();
        if (codePage == null || codePage.isEmpty()) return;
        final Charset charset;
        try {
            charset = Charset.forName("CP" + codePage);
        } catch (Exception ignored) {
            return; // 未知代码页（如 65001 即 UTF-8），保持默认
        }
        try {
            Field field = TerminalTalent.class.getDeclaredField("bashSessionManager");
            field.setAccessible(true);
            field.set(this, new TerminalSessionManager(charset));
        } catch (Exception e) {
            // 反射失败时保持默认 UTF-8 行为，不影响功能
        }
    }

    private static boolean isWindowsOs() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    /** 通过 cmd 内置 chcp 查询活动代码页（如 "936"），失败返回 null。 */
    private static String queryWindowsCodePage() {
        try {
            Process process = new ProcessBuilder("cmd", "/c", "chcp")
                    .redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
            Matcher matcher = Pattern.compile("(\\d{3,5})").matcher(output);
            return matcher.find() ? matcher.group(1) : null;
        } catch (Exception ignored) {
            return null;
        }
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
                            @Param(value = "workdir", required = false, description = "工作目录。默认使用当前项目。") String workdir,
                            @Param(value = "yield_time_ms", required = false, defaultValue = "1000") Integer yieldTimeMs,
                            @Param(value = "max_output_chars", required = false, defaultValue = "64000") Integer maxOutputChars,
                            @Param(value = "hard_timeout_ms", required = false, defaultValue = "120000") Integer hardTimeoutMs,
                            String __cwd) throws IOException {
        String result = super.bashStart(command, workdir, yieldTimeMs, maxOutputChars, hardTimeoutMs, __cwd);
        String sessionId = commandSessionId(result);
        if (sessionId != null && isCommandRunning(result)) {
            BashSessionInfo info = new BashSessionInfo(
                    sessionId, workspace.toString(), command, commandWorkdir(result));
            info.appendOutput(extractCommandOutput(result));
            bashSessions.put(sessionId, info);
        }
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
        appendBashOutput(sessionId, result);
        markBashSessionCompleted(sessionId, result);
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
        appendBashOutput(sessionId, result);
        markBashSessionCompleted(sessionId, result);
        clearCompletedCommand(sessionId, result);
        return result;
    }

    @Override
    @ToolMapping(name = "bash_stop", description = "终止仍在运行的命令会话及其子进程树。")
    public String bashStop(@Param(value = "session_id") String sessionId,
                           @Param(value = "reason", required = false) String reason,
                           @Param(value = "max_output_chars", required = false, defaultValue = "64000") Integer maxOutputChars) {
        String result = null;
        try {
            result = super.bashStop(sessionId, reason, maxOutputChars);
            return result;
        } finally {
            if (sessionId != null) {
                BashSessionInfo info = bashSessions.get(sessionId);
                if (info != null) info.appendOutput(extractCommandOutput(result));
                bashSessions.remove(sessionId);
            }
            AgentLoopController controller = ToolContext.getCurrentController();
            if (controller != null) controller.clearAbortResource(sessionId);
        }
    }

    /**
     * 将 bash_start/wait/stdin/stop 返回文本中的增量输出追加到会话日志镜像。
     */
    private void appendBashOutput(String sessionId, String result) {
        if (sessionId == null) return;
        BashSessionInfo info = bashSessions.get(sessionId);
        if (info != null) info.appendOutput(extractCommandOutput(result));
    }

    /**
     * 从工具返回文本中提取 Output: 段（增量输出），无输出时返回空串。
     */
    private static String extractCommandOutput(String result) {
        if (result == null) return "";
        String marker = "\nOutput:\n";
        int outputStart = result.indexOf(marker);
        if (outputStart < 0) return "";
        String output = result.substring(outputStart + marker.length());
        return "(no new output)".equals(output.trim()) ? "" : output;
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

    private static String commandWorkdir(String result) {
        if (result == null) return null;
        for (String line : result.split("\\R")) {
            if (line.startsWith("workdir: ")) return line.substring("workdir: ".length()).trim();
        }
        return null;
    }

    /**
     * bash_wait / bash_stdin 检测到会话已结束（非 running）时，将镜像标记为 completed。
     */
    private void markBashSessionCompleted(String sessionId, String result) {
        if (sessionId == null) return;
        BashSessionInfo info = bashSessions.get(sessionId);
        if (info != null && !isCommandRunning(result)) {
            info.markCompleted();
        }
    }

    /**
     * 返回本项目的会话镜像快照（先清理过期 completed 项）。
     */
    public List<BashSessionInfo> snapshotBashSessions() {
        long now = System.currentTimeMillis();
        bashSessions.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
        return new ArrayList<>(bashSessions.values());
    }

    /**
     * 跨项目聚合所有 bash 后台会话镜像，按启动时间倒序（新 → 旧）。
     */
    public static List<BashSessionInfo> aggregateBashSessions() {
        List<BashSessionInfo> all = new ArrayList<>();
        for (LoopraSkillProvider provider : LoopraSkillProvider.cliSkillProviderMap.values()) {
            SessionTerminalTalent talent = provider.terminalTalent;
            if (talent != null) {
                all.addAll(talent.snapshotBashSessions());
            }
        }
        all.sort(Comparator.comparingLong(BashSessionInfo::getStartedAt).reversed());
        return all;
    }

    /**
     * 终止本实例的指定 bash 会话（前端“手动关闭”入口的实例能力）。
     *
     * @return 终止后的状态日志文本（含 session_id/状态/原因/最后输出）；未找到会话返回 null
     */
    public String terminateSession(String sessionId, String reason) {
        if (sessionId == null || sessionId.isBlank()) return null;
        if (!bashSessions.containsKey(sessionId)) return null;
        try {
            TerminalSessionManager.CommandSnapshot snapshot = bashSessionManager.terminate(sessionId,
                    reason == null ? "用户手动关闭" : reason, 64_000);
            return formatTerminateMessage(snapshot);
        } catch (Exception e) {
            // 底层会话可能已结束（镜像滞后），仍清理镜像
            return null;
        } finally {
            bashSessions.remove(sessionId);
        }
    }

    /**
     * 手动终止指定 bash 后台会话（前端“手动关闭”入口）。
     *
     * @param sessionId   命令会话 ID
     * @param workspace   项目绝对路径；为空时在所有项目中查找
     * @param reason      终止原因（仅日志诊断）
     * @return 终止后的状态日志文本；未找到会话返回 null
     */
    public static String terminateBashSession(String sessionId, String workspace, String reason) {
        if (sessionId == null || sessionId.isBlank()) return null;
        for (LoopraSkillProvider provider : LoopraSkillProvider.cliSkillProviderMap.values()) {
            SessionTerminalTalent talent = provider.terminalTalent;
            if (talent == null) continue;
            if (workspace != null && !workspace.isEmpty()
                    && !talent.workspace.equals(Path.of(workspace).toAbsolutePath().normalize())) {
                continue;
            }
            String message = talent.terminateSession(sessionId, reason);
            if (message != null) return message;
        }
        return null;
    }

    /**
     * 按 sessionId（可限定项目）查找后台会话镜像，供前端查询累积输出日志。
     *
     * @param sessionId 命令会话 ID
     * @param workspace 项目绝对路径；为空时在所有项目中查找
     * @return 会话镜像；未找到返回 null
     */
    public static BashSessionInfo findBashSession(String sessionId, String workspace) {
        if (sessionId == null || sessionId.isBlank()) return null;
        for (LoopraSkillProvider provider : LoopraSkillProvider.cliSkillProviderMap.values()) {
            SessionTerminalTalent talent = provider.terminalTalent;
            if (talent == null) continue;
            if (workspace != null && !workspace.isEmpty()
                    && !talent.workspace.equals(Path.of(workspace).toAbsolutePath().normalize())) {
                continue;
            }
            BashSessionInfo info = talent.bashSessions.get(sessionId);
            if (info != null) return info;
        }
        return null;
    }

    /**
     * 将终止快照格式化为提示日志文本（供前端展示）。
     */
    private static String formatTerminateMessage(TerminalSessionManager.CommandSnapshot snapshot) {
        if (snapshot == null) return null;
        StringBuilder sb = new StringBuilder();
        sb.append("✅ 已关闭后台进程\n");
        sb.append("session_id: ").append(snapshot.sessionId()).append('\n');
        sb.append("status: ").append(snapshot.running() ? "running" : "completed").append('\n');
        if (snapshot.terminated()) {
            sb.append("terminated: true\n");
        }
        if (snapshot.terminateReason() != null) {
            sb.append("terminate_reason: ").append(snapshot.terminateReason()).append('\n');
        }
        if (snapshot.exitCode() != null) {
            sb.append("exit_code: ").append(snapshot.exitCode()).append('\n');
        }
        String output = snapshot.output();
        if (output != null && !output.isBlank()) {
            String tail = output.length() > 300 ? output.substring(output.length() - 300) : output;
            sb.append("最后输出:\n").append(tail);
        }
        return sb.toString();
    }

    /**
     * bash 后台会话镜像条目。status 为 running / completed；completed 保留 {@link #COMPLETED_VISIBLE_TTL_MS} 后清理。
     */
    public static final class BashSessionInfo {
        private final String sessionId;
        private final String workspace;
        private final String command;
        private final String workdir;
        private final long startedAt;
        private volatile String status;
        private volatile long completedAt;
        /** 累积输出日志（bash_start 初始输出 + bash_wait/stdin 增量输出），超出上限时保留尾部。 */
        private final StringBuilder outputLog = new StringBuilder();

        BashSessionInfo(String sessionId, String workspace, String command, String workdir) {
            this.sessionId = sessionId;
            this.workspace = workspace;
            this.command = command;
            this.workdir = workdir;
            this.startedAt = System.currentTimeMillis();
            this.status = "running";
        }

        public String getSessionId() {
            return sessionId;
        }

        public String getWorkspace() {
            return workspace;
        }

        public String getCommand() {
            return command;
        }

        public String getWorkdir() {
            return workdir;
        }

        public long getStartedAt() {
            return startedAt;
        }

        public String getStatus() {
            return status;
        }

        public long getCompletedAt() {
            return completedAt;
        }

        public boolean isRunning() {
            return "running".equals(status);
        }

        void markCompleted() {
            this.status = "completed";
            this.completedAt = System.currentTimeMillis();
        }

        void appendOutput(String chunk) {
            if (chunk == null || chunk.isEmpty()) return;
            synchronized (outputLog) {
                outputLog.append(chunk);
                if (outputLog.length() > MAX_LOG_CHARS) {
                    outputLog.delete(0, outputLog.length() - MAX_LOG_CHARS);
                }
            }
        }

        public String getOutput() {
            synchronized (outputLog) {
                return outputLog.toString();
            }
        }

        boolean isExpired(long now) {
            return completedAt > 0 && now - completedAt > COMPLETED_VISIBLE_TTL_MS;
        }
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
             // 回退到工具参数。
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
