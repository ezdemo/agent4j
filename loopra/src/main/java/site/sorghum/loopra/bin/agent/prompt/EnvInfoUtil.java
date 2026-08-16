package site.sorghum.loopra.bin.agent.prompt;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 环境信息工具 —— 为系统提示词拼装一段「环境信息」。
 * <p>
 * 内容随项目而变（主要是工作目录不同），用于让 LLM 感知当前运行环境。
 * 不含 git 信息；日期精度只到天，对齐 ZCode 行为，一天内保持稳定以命中前缀缓存。
 * </p>
 *
 * @author Sorghum
 */
public final class EnvInfoUtil {

    /** 日期格式：yyyy-MM-dd */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private EnvInfoUtil() {
    }

    /**
     * 构建环境信息 Markdown 段。
     *
     * @param workspace 当前项目路径，为 null 时回退到 user.dir
     * @return 以 "## 环境信息" 开头的 Markdown 文本
     */
    public static String buildEnvInfo(Path workspace) {
        String workDir = resolveWorkDir(workspace);
        String platform = detectPlatform();
        String shell = detectShell(platform);
        String osVersion = detectOsVersion();
        String today = LocalDate.now().format(DATE_FMT);

        return """
                ## 环境信息

                - 主要工作目录：%s
                - 平台：%s
                - Shell：%s
                - 操作系统版本：%s
                - 当前日期：%s

                关于日期的说明：此信息可能与当前任务相关，也可能不相关。除非高度相关，否则不主动引用。"""
                .formatted(workDir, platform, shell, osVersion, today);
    }

    /** 解析工作目录：优先用传入 workspace 的绝对路径，回退 user.dir。 */
    private static String resolveWorkDir(Path workspace) {
        Path dir = (workspace != null) ? workspace.toAbsolutePath().normalize()
                : Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        return dir.toString();
    }

    /** 平台规范化为 win32 / mac / linux。 */
    private static String detectPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return "win32";
        if (os.contains("mac") || os.contains("darwin")) return "mac";
        return "linux";
    }

    /**
     * 推断 Shell 类型：
     * - win：读 ComSpec 推断（cmd / powershell），否则默认 cmd
     * - 其它：读 SHELL 环境变量，取 basename，否则默认 sh
     */
    private static String detectShell(String platform) {
        try {
            if ("win32".equals(platform)) {
                String comSpec = System.getenv("ComSpec");
                if (comSpec != null) {
                    String lower = comSpec.toLowerCase(Locale.ROOT);
                    if (lower.contains("powershell") || lower.contains("pwsh")) return "PowerShell";
                    if (lower.endsWith("cmd.exe") || lower.contains("cmd")) return "cmd";
                }
                // PowerShell 进程的环境块通常不带 ComSpec 之外的提示，简单回退
                return "cmd";
            }
            String shell = System.getenv("SHELL");
            if (shell != null && !shell.isEmpty()) {
                int slash = shell.lastIndexOf('/');
                return slash >= 0 ? shell.substring(slash + 1) : shell;
            }
            return "sh";
        } catch (Exception e) {
            return "未知";
        }
    }

    /** 操作系统版本：os.name + os.version + 架构（x64 / arm64）。 */
    private static String detectOsVersion() {
        String name = System.getProperty("os.name", "unknown");
        String version = System.getProperty("os.version", "");
        String arch = normalizeArch(System.getProperty("os.arch", ""));
        return name + " " + version + " " + arch;
    }

    /** 架构规范化为 x64 / arm64 / 原值。 */
    private static String normalizeArch(String arch) {
        if (arch == null || arch.isEmpty()) return "";
        String a = arch.toLowerCase(Locale.ROOT);
        if (a.contains("64") && a.contains("arm")) return "arm64";
        if (a.contains("aarch64")) return "arm64";
        if (a.contains("64")) return "x64";
        if (a.contains("86") || a.contains("32")) return "x86";
        return arch;
    }
}
