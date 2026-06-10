package site.sorghum.agent4j.tool.terminal;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

/**
 * 跨平台进程树终止工具。
 * <p>
 * Windows: taskkill /T /F，POSIX: kill(-pid, SIGKILL)。
 * </p>
 */
@Slf4j
public class ProcessTreeKiller {

    /**
     * 终止进程及其所有子进程。
     * Windows: taskkill /T /F /PID。
     * POSIX:   kill(-pid, SIGKILL) -> fallback destroyForcibly。
     */
    public static void kill(Process process) {
        if (process == null || !process.isAlive()) return;
        long pid = getPid(process);
        String os = System.getProperty("os.name", "").toLowerCase();

        // Windows: taskkill /T /F
        if (os.contains("win")) {
            try {
                new ProcessBuilder("taskkill", "/PID", String.valueOf(pid), "/T", "/F")
                        .redirectErrorStream(true)
                        .start()
                        .waitFor();
                return;
            } catch (Exception e) {
                log.debug("Windows taskkill 失败，尝试 POSIX kill: {}", e.getMessage());
            }
        }

        // POSIX: kill process group
        try {
            Runtime.getRuntime().exec(new String[]{"kill", "-9", "-" + pid}).waitFor();
            return;
        } catch (Exception e) {
            log.debug("POSIX kill 失败，回退到 destroyForcibly: {}", e.getMessage());
        }

        // Final fallback
        process.destroyForcibly();
    }

    private static long getPid(Process process) {
        try {
            // Java 9+ 有 Process.pid()
            return (long) Process.class.getMethod("pid").invoke(process);
        } catch (Exception e) {
            // Java 8 fallback: 通过反射访问 private pid 字段（OpenJDK/HotSpot 特有）
            try {
                Field f = process.getClass().getDeclaredField("pid");
                f.setAccessible(true);
                Object val = f.get(process);
                if (val instanceof Integer) return ((Integer) val).longValue();
                if (val instanceof Long) return (Long) val;
            } catch (Exception ex) {
                log.debug("反射获取 PID 失败(Java 8 fallback): {}", ex.getMessage());
            }
            return -1;
        }
    }

    /**
     * 等待进程结束，超时后杀进程树
     */
    public static boolean waitFor(Process process, long timeoutMs) {
        try {
            if (process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                return true;
            }
            kill(process);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            kill(process);
            return false;
        }
    }
}
