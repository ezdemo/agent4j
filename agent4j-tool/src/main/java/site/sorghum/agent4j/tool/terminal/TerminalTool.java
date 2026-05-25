package site.sorghum.agent4j.tool.terminal;

import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 终端工具——在工作区执行 shell 命令。
 * <p>
 * 支持超时控制、工作区根目录设定。命令在独立进程中执行，
 * stdout 和 stderr 合并返回。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class TerminalTool extends AgentTool {

    private static final String NAME = "run_command";

    private static final String DESCRIPTION =
            "在工作区根目录执行 shell 命令，返回 stdout+stderr。\n"
                    + "支持超时控制（默认 60 秒）。\n"
                    + "链式命令（| && || ;）和文件重定向（> >> < 2>）均支持。";

    private static final List<ToolParameter> PARAMETERS = Arrays.asList(
            new ToolParameter("command", "string", true,
                    "要执行的命令，如 \"dir\" 或 \"mvn compile -pl agent4j-tool\""),
            new ToolParameter("timeoutSec", "int", false,
                    "超时秒数，默认 60，上限 300")
    );

    @Override
    public String getName() { return NAME; }

    @Override
    public String getDescription() { return DESCRIPTION; }

    @Override
    public List<ToolParameter> getParameters() { return PARAMETERS; }

    @Override
    public ToolResult execute(ToolContext ctx) {
        String command = ctx.getString("command");
        if (command == null || command.isEmpty()) {
            return ToolResult.fail("MISSING_COMMAND", "缺少必填参数 command");
        }

        int timeoutSec = Math.min(ctx.getInt("timeoutSec", 60), 300);

        Path cwd = ctx.getRootDir() != null ? ctx.getRootDir() : Paths.get(".").toAbsolutePath();

        try {
            ProcessBuilder pb;
            // Windows: cmd /c, 其他: sh -c
            String osName = System.getProperty("os.name", "").toLowerCase();
            if (osName.contains("win")) {
                pb = new ProcessBuilder("cmd", "/c", command);
            } else {
                pb = new ProcessBuilder("sh", "-c", command);
            }
            pb.directory(cwd.toFile());
            pb.redirectErrorStream(true); // 合并 stderr → stdout

            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            boolean finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                return ToolResult.fail("TIMEOUT",
                        "命令执行超时（" + timeoutSec + "s）: " + command);
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.exitValue();
            String result = output.toString().trim();

            if (exitCode == 0) {
                return ToolResult.ok(result.isEmpty() ? "(执行成功，无输出)" : result);
            } else {
                return ToolResult.fail("EXIT_" + exitCode,
                        "[exit " + exitCode + "] " + command + "\n" + result);
            }
        } catch (Exception e) {
            return ToolResult.fail("EXEC_ERROR", e.getMessage());
        }
    }
}
