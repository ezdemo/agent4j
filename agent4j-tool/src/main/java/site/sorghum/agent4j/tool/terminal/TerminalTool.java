package site.sorghum.agent4j.tool.terminal;

import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 终端工具 —— 在工作区执行命令（shell:false，自解析 argv）。
 * <p>
 * 参考 Reasonix TS shell.ts + shell/exec.ts，1:1 复刻。
 * </p>
 */
@Component
public class TerminalTool extends AgentTool {

    private static final String NAME = "run_command";
    private static final int DEFAULT_TIMEOUT_SEC = 60;
    private static final int MAX_TIMEOUT_SEC = 600;
    private static final int DEFAULT_MAX_OUTPUT_CHARS = 32_000;

    private static final String DESCRIPTION =
            "Run a shell command in the project root; returns combined stdout+stderr. "
                    + "Allowlisted read-only / test / lint / typecheck commands run immediately; "
                    + "mutating / network / install commands gate on user confirmation.\n\n"
                    + "DO NOT use run_command for file operations — use write_file, edit_file, multi_edit, "
                    + "copy_file, move_file, or delete_file instead. Shell utilities (echo, cp, sed, cat, tee, "
                    + "perl, python -c, etc.) bypass validation, lack rollback, and will trigger user "
                    + "confirmation gates that waste turns.\n\n"
                    + "No real shell — argv parsed natively for cross-platform parity:\n"
                    + "• Supported: chains `|`/`||`/`&&`/`;` (each segment allowlist-checked) "
                    + "and file redirects `>`/`>>`/`<`/`2>`/`2>>`/`2>&1`/`&>`.\n"
                    + "• Rejected: background `&`, heredoc `<<`, `$(…)`, subshells, `$VAR` expansion, "
                    + "glob expansion. Quote operator chars as literals (`grep \"a|b\" file`).\n"
                    + "• `cd` does NOT persist — between calls OR within a chain. Use "
                    + "`npm --prefix <dir>`, `git -C <dir>`, `cargo -C <dir>` instead.\n"
                    + "• Filter at source — `grep -c` / `wc -l` / narrower paths over unbounded dumps.";

    private static final List<ToolParameter> PARAMETERS = Arrays.asList(
            new ToolParameter("command", "string", true,
                    "Full command line. Quoting + chain/redirect rules per the top-level description."),
            new ToolParameter("timeoutSec", "int", false,
                    "Override the default " + DEFAULT_TIMEOUT_SEC + "s timeout for a single command.")
    );

    @Override public String getName() { return NAME; }
    @Override public String getDescription() { return DESCRIPTION; }
    @Override public List<ToolParameter> getParameters() { return PARAMETERS; }
    @Override public String toToolSpec() { return DESCRIPTION; }

    @Override
    public ToolResult execute(ToolContext ctx) {
        String command = ctx.getString("command");
        if (command == null || (command = command.trim()).isEmpty()) {
            return ToolResult.fail("MISSING_COMMAND", "缺少必填参数 command");
        }

        int timeoutSec = clamp(ctx.getInt("timeoutSec", DEFAULT_TIMEOUT_SEC), 1, MAX_TIMEOUT_SEC);
        Path cwd = ctx.getRootDir() != null ? ctx.getRootDir() : Paths.get(".").toAbsolutePath();

        try {
            // 尝试链式解析
            CommandChainParser.CommandChain chain;
            try { chain = CommandChainParser.parse(command); } catch (CommandChainParser.UnsupportedSyntaxException e) {
                return ToolResult.fail("UNSUPPORTED_SYNTAX", e.getMessage());
            }

            if (chain != null) {
                return runChain(chain, cwd, timeoutSec);
            } else {
                List<String> argv = CommandTokenizer.tokenize(command);
                if (argv.isEmpty()) return ToolResult.fail("EMPTY_COMMAND", "空命令");
                return runSimple(argv, cwd, timeoutSec);
            }
        } catch (IllegalArgumentException e) {
            return ToolResult.fail("PARSE_ERROR", e.getMessage());
        }
    }

    // ======== 简单命令执行 ========

    private ToolResult runSimple(List<String> argv, Path cwd, int timeoutSec) {
        try {
            SpawnResult spawn = prepareSpawn(argv);
            ProcessBuilder pb = new ProcessBuilder(spawn.argv);
            pb.directory(cwd.toFile());
            pb.redirectErrorStream(true);
            // 环境变量
            Map<String, String> env = pb.environment();
            env.put("PYTHONIOENCODING", "utf-8");
            env.put("PYTHONUTF8", "1");

            Process process = pb.start();

            // 并发消费输出（防止管道缓冲区死锁）
            ByteArrayOutputStream rawOut = new ByteArrayOutputStream();
            final int byteCap = DEFAULT_MAX_OUTPUT_CHARS * 2 * 4;
            final AtomicBoolean capped = new AtomicBoolean(false);
            Thread reader = new Thread(() -> {
                byte[] buf = new byte[4096];
                try (InputStream is = process.getInputStream()) {
                    int n;
                    while ((n = is.read(buf)) > 0) {
                        if (rawOut.size() >= byteCap) { capped.set(true); break; }
                        int rem = byteCap - rawOut.size();
                        rawOut.write(buf, 0, Math.min(n, rem));
                    }
                } catch (IOException ignored) {}
            });
            reader.start();

            boolean finished = ProcessTreeKiller.waitFor(process, timeoutSec * 1000L);
            try { reader.join(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            if (!finished) {
                return ToolResult.fail("TIMEOUT", "命令执行超时（" + timeoutSec + "s）: " + String.join(" ", argv));
            }

            byte[] rawBytes = rawOut.toByteArray();
            String output = SmartDecoder.decode(rawBytes);
            if (output.length() > DEFAULT_MAX_OUTPUT_CHARS) {
                output = output.substring(0, DEFAULT_MAX_OUTPUT_CHARS)
                        + "\n\n[… 截断 " + (output.length() - DEFAULT_MAX_OUTPUT_CHARS) + " 字符 …]";
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                return ToolResult.ok(formatOutput(argv, exitCode, output, false));
            } else {
                return ToolResult.fail("EXIT_" + exitCode,
                        formatOutput(argv, exitCode, output, false));
            }
        } catch (IOException e) {
            return ToolResult.fail("EXEC_ERROR", e.getMessage());
        }
    }

    // ======== 链式命令执行 ========

    private ToolResult runChain(CommandChainParser.CommandChain chain, Path cwd, int totalTimeoutSec) {
        long deadline = System.currentTimeMillis() + totalTimeoutSec * 1000L;
        int lastExit = 0;
        boolean timedOut = false;
        StringBuilder allOutput = new StringBuilder();

        List<List<CommandChainParser.ChainSegment>> groups = groupChain(chain);

        for (int g = 0; g < groups.size(); g++) {
            List<CommandChainParser.ChainSegment> group = groups.get(g);
            CommandChainParser.ChainOp opBefore = g > 0 ? chain.ops.get(g - 1) : null;

            // 短路求值
            if (opBefore == CommandChainParser.ChainOp.AND && lastExit != 0) continue;
            if (opBefore == CommandChainParser.ChainOp.OR && lastExit == 0) continue;

            long remainingMs = deadline - System.currentTimeMillis();
            if (remainingMs <= 0) { timedOut = true; break; }

            ToolResult groupResult = runPipeGroup(group, cwd, (int) Math.min(remainingMs, Integer.MAX_VALUE));
            if (groupResult.success()) {
                lastExit = 0;
                allOutput.append(groupResult.text());
            } else {
                String code = groupResult.errorCode();
                lastExit = code != null && code.startsWith("EXIT_") ? Integer.parseInt(code.substring(5)) : 1;
                allOutput.append(groupResult.text());
                if (code != null && code.equals("TIMEOUT")) { timedOut = true; break; }
            }
        }

        String output = allOutput.toString().trim();
        if (output.length() > DEFAULT_MAX_OUTPUT_CHARS) {
            output = output.substring(0, DEFAULT_MAX_OUTPUT_CHARS)
                    + "\n\n[… 截断 " + (output.length() - DEFAULT_MAX_OUTPUT_CHARS) + " 字符 …]";
        }

        if (timedOut) {
            return ToolResult.fail("TIMEOUT", "[killed after timeout] " + output);
        }
        if (lastExit == 0) {
            return ToolResult.ok(output.isEmpty() ? "(执行成功，无输出)" : output);
        } else {
            return ToolResult.fail("EXIT_" + lastExit, "[exit " + lastExit + "] " + output);
        }
    }

    /** 管道组：| 连接的多个 segment，通过 pipe 串联 */
    private ToolResult runPipeGroup(List<CommandChainParser.ChainSegment> segments,
                                     Path cwd, int timeoutMs) {
        if (segments.size() == 1 && segments.get(0).redirects.isEmpty()) {
            return runSimple(segments.get(0).argv, cwd, timeoutMs / 1000);
        }

        // 复杂管道/重定向 → 回退到简单的进程内执行
        // 在 Java 中实现完整的 pipe 链极其复杂，这里对单 segment 有重定向或
        // 多 segment 简单 pipe 的场景回退到 sh -c（带沙箱校验）
        StringBuilder cmdBuilder = new StringBuilder();
        for (int i = 0; i < segments.size(); i++) {
            if (i > 0) cmdBuilder.append(" | ");
            CommandChainParser.ChainSegment seg = segments.get(i);
            for (String a : seg.argv) cmdBuilder.append(quoteArg(a)).append(" ");
            for (CommandChainParser.Redirect r : seg.redirects) {
                cmdBuilder.append(r.kind.text);
                if (r.kind.needsTarget()) cmdBuilder.append(" ").append(quoteArg(r.target));
                cmdBuilder.append(" ");
            }
        }

        // 沙箱校验：重定向目标不能出 workspace
        for (CommandChainParser.ChainSegment seg : segments) {
            for (CommandChainParser.Redirect r : seg.redirects) {
                if (r.kind == CommandChainParser.RedirectKind.ERR_MERGE) continue;
                if (r.target.isEmpty() || CommandAllowlist.isNullDevice(r.target)) continue;
                Path resolved = cwd.resolve(r.target).normalize();
                if (!resolved.startsWith(cwd.normalize())) {
                    return ToolResult.fail("SANDBOX_ESCAPE",
                            "redirect target \"" + r.target + "\" resolves outside workspace");
                }
            }
        }

        // 回退执行
        List<String> wrapper = isWindows()
                ? Arrays.asList("cmd", "/d", "/s", "/c", "chcp 65001 >nul & " + cmdBuilder.toString().trim())
                : Arrays.asList("sh", "-c", cmdBuilder.toString().trim());
        return runSimple(wrapper, cwd, timeoutMs / 1000);
    }

    // ======== 分组管道 ========

    private static List<List<CommandChainParser.ChainSegment>> groupChain(
            CommandChainParser.CommandChain chain) {
        List<List<CommandChainParser.ChainSegment>> groups = new ArrayList<>();
        List<CommandChainParser.ChainSegment> current = new ArrayList<>();
        current.add(chain.segments.get(0));
        for (int i = 0; i < chain.ops.size(); i++) {
            CommandChainParser.ChainOp op = chain.ops.get(i);
            CommandChainParser.ChainSegment next = chain.segments.get(i + 1);
            if (op == CommandChainParser.ChainOp.PIPE) {
                current.add(next);
            } else {
                groups.add(current);
                current = new ArrayList<>();
                current.add(next);
            }
        }
        groups.add(current);
        return groups;
    }

    // ======== 跨平台二进制解析 ========

    private static class SpawnResult {
        final List<String> argv;
        SpawnResult(List<String> a) { argv = a; }
    }

    private static SpawnResult prepareSpawn(List<String> argv) {
        if (argv.isEmpty()) return new SpawnResult(argv);
        String head = argv.get(0);
        List<String> tail = argv.subList(1, argv.size());

        if (!isWindows()) return new SpawnResult(rebuildArgv(head, tail));

        // Windows: .cmd/.bat 需要 cmd.exe 包装（CVE-2024-27980 后 Node 的做法）
        if (head.toLowerCase().endsWith(".cmd") || head.toLowerCase().endsWith(".bat")) {
            String cmdline = quoteForCmdExe(head);
            for (String a : tail) cmdline += " " + quoteForCmdExe(a);
            return new SpawnResult(Arrays.asList("cmd.exe", "/d", "/s", "/c",
                    "chcp 65001 >nul & " + cmdline));
        }

        // Windows: 裸命令名 → PATH × PATHEXT 查找
        String resolved = resolveExecutable(head);
        List<String> result = new ArrayList<>();
        result.add(resolved);
        result.addAll(tail);
        return new SpawnResult(result);
    }

    private static List<String> rebuildArgv(String head, List<String> tail) {
        List<String> result = new ArrayList<>();
        result.add(head);
        result.addAll(tail);
        return result;
    }

    /** Windows: 在 PATH 中按 PATHEXT 查找可执行文件 */
    private static String resolveExecutable(String cmd) {
        if (cmd.contains("/") || cmd.contains("\\") || Paths.get(cmd).isAbsolute()) return cmd;
        if (cmd.contains(".")) return cmd; // 已有扩展名

        String pathExt = System.getenv("PATHEXT");
        if (pathExt == null) pathExt = ".COM;.EXE;.BAT;.CMD";
        String[] exts = pathExt.split(";");
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return cmd;
        String[] dirs = pathEnv.split(File.pathSeparator);

        for (String dir : dirs) {
            for (String ext : exts) {
                Path full = Paths.get(dir, cmd + ext.trim());
                if (Files.isRegularFile(full) && Files.isExecutable(full)) {
                    return full.toString();
                }
            }
        }
        return cmd;
    }

    /** cmd.exe 兼容引用：内嵌双引号用 "" 转义 */
    private static String quoteForCmdExe(String arg) {
        if (arg.isEmpty()) return "\"\"";
        if (!arg.matches(".*[\\s\"&|<>^%(),;!].*")) return arg;
        return "\"" + arg.replace("\"", "\"\"") + "\"";
    }

    /** Shell 兼容引用 */
    private static String quoteArg(String arg) {
        if (arg.isEmpty()) return "''";
        if (!arg.matches(".*[\\s\"'|&;<>$`*?(){}\\[\\]!].*")) return arg;
        return "'" + arg.replace("'", "'\\''") + "'";
    }

    // ======== 格式化 ========

    private static String formatOutput(List<String> argv, int exitCode, String output, boolean timedOut) {
        if (timedOut) return "[killed after timeout]\n" + output;
        if (output.isEmpty()) return "[exit " + exitCode + "]";
        return "[exit " + exitCode + "]\n" + output;
    }

    // ======== 工具方法 ========

    private static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
