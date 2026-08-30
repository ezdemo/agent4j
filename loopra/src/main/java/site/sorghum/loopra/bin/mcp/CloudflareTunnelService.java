package site.sorghum.loopra.bin.mcp;

import lombok.extern.slf4j.Slf4j;
import org.noear.snack4.Feature;
import org.noear.snack4.ONode;
import org.noear.snack4.Options;
import org.noear.snack4.json.JsonWriter;
import org.noear.solon.Solon;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.noear.solon.core.event.AppLoadEndEvent;
import org.noear.solon.core.event.EventListener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 使用 Java 进程能力管理 Cloudflare Quick Tunnel。
 *
 * <p>不拼接 shell 命令，也不依赖 cmd/bash；Windows、macOS、Linux 都通过
 * {@link ProcessBuilder} 启动对应平台的 cloudflared 可执行文件。</p>
 */
@Slf4j
@Component
public class CloudflareTunnelService implements EventListener<AppLoadEndEvent> {

    private static final String CONFIG_FILE = "cloudflare-tunnel.json";
    private static final String STATE_STOPPED = "stopped";
    private static final String STATE_STARTING = "starting";
    private static final String STATE_RUNNING = "running";
    private static final String STATE_STOPPING = "stopping";
    private static final String STATE_ERROR = "error";
    private static final Pattern QUICK_TUNNEL_URL = Pattern.compile(
            "https://[A-Za-z0-9.-]+\\.trycloudflare\\.com", Pattern.CASE_INSENSITIVE);
    private static final int START_WAIT_SECONDS = 20;
    private static final long EXECUTABLE_CACHE_MILLIS = 10_000L;

    private final Object lifecycleLock = new Object();
    private final Deque<String> outputTail = new ArrayDeque<>();

    @Inject
    private McpServerExportService mcpServerExportService;

    private volatile CloudflareTunnelConfig config;
    private volatile Process process;
    private volatile CloudflareTunnelProxy proxy;
    private volatile String activeEndpoint;
    private volatile String publicUrl;
    private volatile String state = STATE_STOPPED;
    private volatile String error;

    private boolean loaded;
    private boolean shutdownHookRegistered;
    private String executableCacheKey;
    private boolean executableCacheValue;
    private long executableCacheAt;

    @Override
    public void onEvent(AppLoadEndEvent event) {
        synchronized (lifecycleLock) {
            ensureLoadedLocked();
            registerShutdownHookLocked();
        }
    }

    /** 获取当前隧道状态。 */
    public CloudflareTunnelStatusDTO getStatus() {
        synchronized (lifecycleLock) {
            ensureLoadedLocked();
            return statusLocked();
        }
    }

    /** 保存 cloudflared 路径配置，不启动隧道。 */
    public CloudflareTunnelStatusDTO saveConfig(CloudflareTunnelConfig requested) {
        synchronized (lifecycleLock) {
            ensureLoadedLocked();
            config = normalize(requested);
            invalidateExecutableCacheLocked();
            saveToFile(config);
            return statusLocked();
        }
    }

    /**
     * 启动 Quick Tunnel。调用方可以传入新的可执行文件路径；路径会一并保存。
     */
    public CloudflareTunnelStatusDTO start(CloudflareTunnelConfig requested) {
        synchronized (lifecycleLock) {
            ensureLoadedLocked();
            if (requested != null) {
                config = normalize(requested);
                invalidateExecutableCacheLocked();
                saveToFile(config);
            }
            if (isProcessAliveLocked()) {
                return statusLocked();
            }

            McpExportConfigDTO mcp = currentMcpConfig();
            validateMcpConfig(mcp);
            int serverPort = Solon.cfg().serverPort();
            String endpoint = normalizeEndpoint(mcp.endpoint);
            String executable = resolveExecutable(config);

            CloudflareTunnelProxy startedProxy = null;
            Process startedProcess = null;
            try {
                // Quick Tunnel 只代理这个 Java 临时端口，避免暴露 Loopra 的其它 Web 路由。
                startedProxy = CloudflareTunnelProxy.start(serverPort, endpoint);
                List<String> command = buildQuickTunnelCommand(executable, startedProxy.originUrl());
                startedProcess = new ProcessBuilder(command)
                        .redirectErrorStream(true)
                        .start();

                process = startedProcess;
                proxy = startedProxy;
                activeEndpoint = endpoint;
                publicUrl = null;
                error = null;
                state = STATE_STARTING;
                outputTail.clear();
                registerShutdownHookLocked();

                Process observed = startedProcess;
                Thread monitor = new Thread(() -> monitorProcess(observed),
                        "loopra-cloudflare-tunnel-monitor");
                monitor.setDaemon(true);
                monitor.start();

                return waitForPublicUrlLocked(observed);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cleanupFailedStartLocked(startedProcess, startedProxy);
                state = STATE_ERROR;
                error = "启动 Cloudflare 隧道被中断";
                throw new IllegalStateException(error, e);
            } catch (Exception e) {
                cleanupFailedStartLocked(startedProcess, startedProxy);
                state = STATE_ERROR;
                error = describeStartError(e);
                throw new IllegalStateException(error, e);
            }
        }
    }

    /** 停止当前 Quick Tunnel。 */
    public CloudflareTunnelStatusDTO stop() {
        Process current;
        synchronized (lifecycleLock) {
            ensureLoadedLocked();
            current = process;
            if (current == null) {
                CloudflareTunnelProxy orphan = proxy;
                proxy = null;
                publicUrl = null;
                activeEndpoint = null;
                state = STATE_STOPPED;
                error = null;
                closeProxy(orphan);
                return statusLocked();
            }
            state = STATE_STOPPING;
            error = null;
            publicUrl = null;
        }

        current.destroy();
        try {
            if (!current.waitFor(3, TimeUnit.SECONDS)) {
                current.destroyForcibly();
                current.waitFor(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            current.destroyForcibly();
        }

        CloudflareTunnelProxy toClose = null;
        synchronized (lifecycleLock) {
            if (process == current) {
                process = null;
                toClose = proxy;
                proxy = null;
                activeEndpoint = null;
                publicUrl = null;
                state = STATE_STOPPED;
                error = null;
                lifecycleLock.notifyAll();
            }
            CloudflareTunnelStatusDTO result = statusLocked();
            closeProxy(toClose);
            return result;
        }
    }

    /**
     * MCP 配置发生变化时调用。如果端点路径或传输方式已不适合当前 Quick Tunnel，停止旧隧道，
     * 避免旧公网地址继续指向错误的本地路由。
     */
    public void onMcpConfigChanged() {
        boolean shouldStop;
        synchronized (lifecycleLock) {
            if (!isProcessAliveLocked()) return;
            McpExportConfigDTO mcp = currentMcpConfig();
            shouldStop = mcp == null || !mcp.enabled || "sse".equalsIgnoreCase(mcp.channel)
                    || !Objects.equals(activeEndpoint, mcp.endpoint);
        }
        if (shouldStop) {
            stop();
        }
    }

    /** 仅用于 JVM shutdown hook，不能再触发配置读取。 */
    private void stopAtShutdown() {
        Process current = process;
        if (current != null) {
            current.destroy();
            try {
                if (!current.waitFor(2, TimeUnit.SECONDS)) current.destroyForcibly();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                current.destroyForcibly();
            }
        }
        CloudflareTunnelProxy currentProxy = proxy;
        if (currentProxy != null) currentProxy.close();
    }

    private void monitorProcess(Process observed) {
        int exitCode = -1;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                observed.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                handleOutputLine(observed, line);
            }
            exitCode = observed.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            log.debug("[cloudflare] 读取 cloudflared 输出失败: {}", e.getMessage());
        } finally {
            CloudflareTunnelProxy toClose = null;
            synchronized (lifecycleLock) {
                if (process != observed) return;
                boolean requestedStop = STATE_STOPPING.equals(state);
                process = null;
                toClose = proxy;
                proxy = null;
                activeEndpoint = null;
                publicUrl = null;
                if (requestedStop) {
                    state = STATE_STOPPED;
                    error = null;
                } else {
                    state = STATE_ERROR;
                    error = "cloudflared 已退出 (code=" + exitCode + ")"
                            + outputTailSummaryLocked();
                }
                lifecycleLock.notifyAll();
            }
            closeProxy(toClose);
        }
    }

    private void handleOutputLine(Process observed, String line) {
        synchronized (lifecycleLock) {
            if (process != observed) return;
            if (line != null && !line.isBlank()) {
                outputTail.addLast(line.trim());
                while (outputTail.size() > 20) outputTail.removeFirst();
            }
            Matcher matcher = QUICK_TUNNEL_URL.matcher(line == null ? "" : line);
            if (matcher.find()) {
                publicUrl = appendPath(matcher.group(), activeEndpoint);
                state = STATE_RUNNING;
                error = null;
                lifecycleLock.notifyAll();
            }
        }
    }

    private CloudflareTunnelStatusDTO waitForPublicUrlLocked(Process observed)
            throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(START_WAIT_SECONDS).toNanos();
        while (process == observed && observed.isAlive() && publicUrl == null
                && System.nanoTime() < deadline) {
            lifecycleLock.wait(100L);
        }
        if (process != observed && STATE_ERROR.equals(state)) {
            throw new IllegalStateException(error == null ? "Cloudflare 隧道启动失败" : error);
        }
        if (process == observed && !observed.isAlive() && STATE_ERROR.equals(state)) {
            throw new IllegalStateException(error == null ? "Cloudflare 隧道启动失败" : error);
        }
        return statusLocked();
    }

    private void cleanupFailedStartLocked(Process startedProcess,
                                           CloudflareTunnelProxy startedProxy) {
        if (startedProcess != null) {
            startedProcess.destroyForcibly();
        }
        if (process == startedProcess) process = null;
        if (proxy == startedProxy) proxy = null;
        activeEndpoint = null;
        publicUrl = null;
        closeProxy(startedProxy);
    }

    private void validateMcpConfig(McpExportConfigDTO mcp) {
        if (mcp == null || !mcp.enabled) {
            throw new IllegalStateException("请先启用 Loopra MCP 工具发布");
        }
        if (mcp.endpoint == null || mcp.endpoint.isBlank() || "/".equals(mcp.endpoint)) {
            throw new IllegalStateException("Cloudflare 隧道不允许把 MCP endpoint 设置为 /，以免暴露整个 Web 服务");
        }
        if ("sse".equalsIgnoreCase(mcp.channel)) {
            throw new IllegalStateException("Cloudflare Quick Tunnel 不支持 SSE，请先将 MCP 传输方式改为 Streamable HTTP");
        }
    }

    private McpExportConfigDTO currentMcpConfig() {
        return mcpServerExportService == null ? null : mcpServerExportService.getConfig();
    }

    private CloudflareTunnelStatusDTO statusLocked() {
        McpExportConfigDTO mcp = currentMcpConfig();
        String endpoint = mcp == null || mcp.endpoint == null || mcp.endpoint.isBlank()
                ? "/mcp" : mcp.endpoint;
        String localUrl = buildLocalUrl(endpoint);
        Process current = process;
        boolean running = current != null && current.isAlive()
                && (STATE_STARTING.equals(state) || STATE_RUNNING.equals(state));
        boolean installed = running || isExecutableAvailableLocked();
        return new CloudflareTunnelStatusDTO(
                state,
                running,
                installed,
                true,
                mcp != null && mcp.enabled,
                platformLabel(),
                configuredExecutableLabel(),
                executableLabel(),
                localUrl,
                publicUrl,
                endpoint,
                error,
                current == null ? 0L : current.pid());
    }

    private boolean isProcessAliveLocked() {
        Process current = process;
        return current != null && current.isAlive();
    }

    private boolean isExecutableAvailableLocked() {
        String executable;
        try {
            executable = resolveExecutable(config);
        } catch (RuntimeException e) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (Objects.equals(executableCacheKey, executable)
                && now - executableCacheAt < EXECUTABLE_CACHE_MILLIS) {
            return executableCacheValue;
        }

        boolean available = false;
        Process probe = null;
        try {
            probe = new ProcessBuilder(executable, "--version")
                    .redirectErrorStream(true)
                    .start();
            available = probe.waitFor(3, TimeUnit.SECONDS) && probe.exitValue() == 0;
            if (!available) probe.destroyForcibly();
        } catch (Exception e) {
            if (probe != null) probe.destroyForcibly();
        }
        executableCacheKey = executable;
        executableCacheValue = available;
        executableCacheAt = now;
        return available;
    }

    static List<String> buildQuickTunnelCommand(String executable, String originUrl) {
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.add("tunnel");
        command.add("--url");
        command.add(originUrl);
        return List.copyOf(command);
    }

    private String resolveExecutable(CloudflareTunnelConfig value) {
        String configured = value == null || value.executablePath == null
                ? "" : value.executablePath.trim();
        if (configured.isEmpty()) {
            String environmentPath = System.getenv("CLOUDFLARED_PATH");
            if (environmentPath != null && !environmentPath.isBlank()) {
                configured = environmentPath.trim();
            } else {
                configured = isWindows() ? "cloudflared.exe" : "cloudflared";
            }
        }

        boolean looksLikePath = configured.contains("\\") || configured.contains("/");
        if (looksLikePath) {
            Path path = Paths.get(configured).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) {
                throw new IllegalArgumentException("找不到 cloudflared 可执行文件: " + path);
            }
            if (!isWindows() && !Files.isExecutable(path)) {
                throw new IllegalArgumentException("cloudflared 没有执行权限: " + path);
            }
            return path.toString();
        }
        return configured;
    }

    private String executableLabel() {
        try {
            return resolveExecutable(config);
        } catch (RuntimeException e) {
            return config == null ? "cloudflared" : config.executablePath;
        }
    }

    private String configuredExecutableLabel() {
        return config == null || config.executablePath == null ? "" : config.executablePath;
    }

    private static String normalizeEndpoint(String endpoint) {
        String value = endpoint == null || endpoint.isBlank() ? "/mcp" : endpoint.trim();
        if (!value.startsWith("/") || value.contains(" ") || value.contains("\t")
                || value.contains("\r") || value.contains("\n")) {
            throw new IllegalArgumentException("MCP endpoint 必须是以 / 开头的不含空格路径");
        }
        while (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String buildLocalUrl(String endpoint) {
        try {
            int port = Solon.cfg().serverPort();
            return "http://127.0.0.1:" + port + normalizeEndpoint(endpoint);
        } catch (Exception e) {
            return normalizeEndpoint(endpoint);
        }
    }

    private static String appendPath(String base, String endpoint) {
        if (endpoint == null || endpoint.isBlank() || "/".equals(endpoint)) return base;
        return base.replaceAll("/+$", "") + endpoint;
    }

    private static String describeStartError(Exception e) {
        if (e instanceof IOException) {
            return "无法启动 cloudflared，请确认已安装并加入 PATH，或在设置中填写可执行文件路径";
        }
        return e.getMessage() == null || e.getMessage().isBlank()
                ? "启动 Cloudflare 隧道失败" : e.getMessage();
    }

    private String outputTailSummaryLocked() {
        if (outputTail.isEmpty()) return "";
        String last = outputTail.peekLast();
        if (last == null || last.isBlank()) return "";
        String sanitized = last.length() > 240 ? last.substring(0, 240) + "..." : last;
        return ": " + sanitized;
    }

    private static String platformLabel() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return "windows";
        if (os.contains("mac") || os.contains("darwin")) return "macos";
        if (os.contains("linux")) return "linux";
        return os.isBlank() ? "unknown" : os;
    }

    private static boolean isWindows() {
        return platformLabel().equals("windows");
    }

    private void ensureLoadedLocked() {
        if (loaded) return;
        CloudflareTunnelConfig loadedConfig = loadFromFile();
        config = normalize(loadedConfig);
        loaded = true;
    }

    private static CloudflareTunnelConfig normalize(CloudflareTunnelConfig source) {
        CloudflareTunnelConfig target = new CloudflareTunnelConfig();
        if (source != null && source.executablePath != null) {
            target.executablePath = source.executablePath.trim();
        }
        return target;
    }

    private void invalidateExecutableCacheLocked() {
        executableCacheKey = null;
        executableCacheAt = 0L;
    }

    private void registerShutdownHookLocked() {
        if (shutdownHookRegistered) return;
        Runtime.getRuntime().addShutdownHook(new Thread(this::stopAtShutdown,
                "loopra-cloudflare-tunnel-shutdown"));
        shutdownHookRegistered = true;
    }

    private Path configPath() {
        return Paths.get(System.getProperty("user.home"), ".loopra", CONFIG_FILE);
    }

    private void saveToFile(CloudflareTunnelConfig value) {
        try {
            Path path = configPath();
            Files.createDirectories(path.getParent());
            String json = JsonWriter.write(ONode.ofBean(value), Options.of(Feature.Write_PrettyFormat));
            Files.writeString(path, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("保存 Cloudflare 隧道配置失败", e);
        }
    }

    private CloudflareTunnelConfig loadFromFile() {
        Path path = configPath();
        if (!Files.exists(path)) return new CloudflareTunnelConfig();
        try {
            return ONode.ofJson(Files.readString(path, StandardCharsets.UTF_8))
                    .toBean(CloudflareTunnelConfig.class);
        } catch (Exception e) {
            log.warn("[cloudflare] 读取隧道配置失败，使用默认配置: {}", path);
            return new CloudflareTunnelConfig();
        }
    }

    private static void closeProxy(CloudflareTunnelProxy value) {
        if (value == null) return;
        try {
            value.close();
        } catch (RuntimeException e) {
            log.debug("[cloudflare] 关闭本地 MCP 代理失败", e);
        }
    }
}
