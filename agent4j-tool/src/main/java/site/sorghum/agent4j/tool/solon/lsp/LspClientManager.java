package site.sorghum.agent4j.tool.solon.lsp;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * LSP 客户端管理器——管理多个 Language Server 配置和活跃客户端实例。
 * <p>
 * 核心职责：
 * <ul>
 *   <li>注册/注销 Language Server 配置（{@link LspServerParameters}）</li>
 *   <li>根据文件扩展名查找匹配的 Language Server</li>
 *   <li>懒加载启动 Language Server 进程</li>
 *   <li>统一管理所有活跃客户端的生命周期</li>
 * </ul>
 * </p>
 *
 * <h3>线程安全</h3>
 * <p>所有状态读写均受 {@link #clientLock} 保护，支持并发访问。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * LspClientManager manager = new LspClientManager();
 *
 * // 注册服务器配置
 * manager.registerServer(LspServerParameters.builder()
 *     .name("gopls")
 *     .command(List.of("gopls", "serve"))
 *     .extensions(List.of(".go"))
 *     .enabled(true)
 *     .build());
 *
 * // 根据文件路径获取客户端（自动启动）
 * LspClient client = manager.getClientForFile("/path/to/main.go");
 * if (client != null) {
 *     client.touchFile("/path/to/main.go");
 *     List<? extends Location> defs = client.definition(...).get();
 * }
 *
 * // 应用关闭时清理所有进程
 * manager.shutdownAll();
 * }</pre>
 *
 * @author Sorghum
 */
@Slf4j
@Component
public class LspClientManager {

    /**
     * 已注册的 Language Server 配置（key = server name）
     */
    private final Map<String, LspServerParameters> serverConfigs = new ConcurrentHashMap<>();

    /**
     * 活跃的 Language Server 客户端实例（key = server name）
     */
    private final Map<String, LspClient> activeClients = new ConcurrentHashMap<>();

    /**
     * 客户端操作锁（保护启动/关闭竞态）
     */
    private final ReentrantLock clientLock = new ReentrantLock();

    // ======================================================================
    //  注册 & 注销
    // ======================================================================

    /**
     * 注册一个 Language Server 配置。
     * <p>如果同名配置已存在，将覆盖旧配置（但不影响已运行的客户端）。</p>
     *
     * @param params Language Server 配置参数
     */
    public void registerServer(LspServerParameters params) {
        if (params == null || params.getName() == null || params.getName().isBlank()) {
            log.warn("拒绝注册无效的 Language Server 配置: name 为空");
            return;
        }
        serverConfigs.put(params.getName(), params);
        log.info("注册 Language Server 配置: {} (extensions={}, enabled={})",
                params.getName(), params.getExtensions(), params.isEnabled());
    }

    /**
     * 批量注册 Language Server 配置。
     *
     * @param paramsList 配置列表
     */
    public void registerServers(List<LspServerParameters> paramsList) {
        if (paramsList != null) {
            paramsList.forEach(this::registerServer);
        }
    }

    /**
     * 注销一个 Language Server 配置，并关闭对应的活跃客户端。
     *
     * @param serverName Language Server 名称
     */
    public void unregisterServer(String serverName) {
        serverConfigs.remove(serverName);
        clientLock.lock();
        try {
            LspClient client = activeClients.remove(serverName);
            if (client != null) {
                log.info("注销 Language Server 并关闭客户端: {}", serverName);
                client.shutdown();
            }
        } finally {
            clientLock.unlock();
        }
    }

    /**
     * 获取所有已注册的服务器名称。
     */
    public List<String> getRegisteredServerNames() {
        return new ArrayList<>(serverConfigs.keySet());
    }

    /**
     * 获取指定服务器的配置。
     */
    public LspServerParameters getServerConfig(String serverName) {
        return serverConfigs.get(serverName);
    }

    // ======================================================================
    //  客户端获取（懒加载）
    // ======================================================================

    /**
     * 根据文件路径获取匹配的 LSP 客户端（懒加载启动）。
     * <p>
     * 匹配逻辑：
     * <ol>
     *   <li>遍历所有已注册且启用的服务器配置</li>
     *   <li>使用 {@link LspServerParameters#matchesExtension(String)} 匹配文件扩展名</li>
     *   <li>返回第一个匹配的客户端（同一文件不会匹配多个服务器）</li>
     * </ol>
     * 如果客户端尚未启动，则自动调用 {@link #getOrCreateClient(String)} 启动。
     * </p>
     *
     * @param filePath 文件的绝对路径
     * @return 匹配的 LSP 客户端，无匹配时返回 null
     */
    public LspClient getClientForFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return null;
        }

        for (LspServerParameters config : serverConfigs.values()) {
            if (!config.isEnabled()) {
                continue;
            }
            if (config.matchesExtension(filePath)) {
                return getOrCreateClient(config.getName());
            }
        }

        log.debug("未找到匹配的 Language Server: {}", filePath);
        return null;
    }

    /**
     * 获取或创建指定名称的 LSP 客户端（懒加载启动 Language Server 进程）。
     * <p>
     * 如果客户端已运行，直接返回；否则创建新的 {@link LspClientImpl} 实例并调用 {@code start()}。
     * </p>
     *
     * @param serverName Language Server 名称
     * @return LSP 客户端实例，配置不存在或启动失败时返回 null
     */
    public LspClient getOrCreateClient(String serverName) {
        LspServerParameters config = serverConfigs.get(serverName);
        if (config == null) {
            log.warn("Language Server 配置不存在: {}", serverName);
            return null;
        }

        // 快速路径：已运行
        LspClient existing = activeClients.get(serverName);
        if (existing != null && existing.isRunning()) {
            return existing;
        }

        clientLock.lock();
        try {
            // 双重检查
            existing = activeClients.get(serverName);
            if (existing != null && existing.isRunning()) {
                return existing;
            }

            // 清理已停止的旧实例
            if (existing != null) {
                activeClients.remove(serverName);
            }

            // 创建并启动
            log.info("创建 Language Server 客户端: {}", serverName);
            LspClientImpl client = new LspClientImpl(config);
            try {
                client.start();
                activeClients.put(serverName, client);
                log.info("Language Server 启动成功: {}", serverName);
                return client;
            } catch (IOException e) {
                log.error("Language Server 进程启动失败 [{}]: {}", serverName, e.getMessage());
            } catch (ExecutionException e) {
                log.error("Language Server 初始化失败 [{}]: {}", serverName, e.getCause() != null
                        ? e.getCause().getMessage() : e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Language Server 启动被中断 [{}]", serverName);
            } catch (TimeoutException e) {
                log.error("Language Server 初始化超时 [{}] ({}s)", serverName, 30);
                client.shutdown();
            }
            return null;
        } finally {
            clientLock.unlock();
        }
    }

    /**
     * 获取已运行的客户端（不会启动新进程）。
     *
     * @param serverName Language Server 名称
     * @return 运行中的客户端，不存在或未运行时返回 null
     */
    public LspClient getRunningClient(String serverName) {
        LspClient client = activeClients.get(serverName);
        return (client != null && client.isRunning()) ? client : null;
    }

    // ======================================================================
    //  生命周期管理
    // ======================================================================

    /**
     * 关闭所有活跃的 Language Server 进程。
     * <p>通常在应用关闭时调用，确保所有子进程被正确终止。</p>
     */
    public void shutdownAll() {
        clientLock.lock();
        try {
            log.info("关闭所有 Language Server (共 {} 个)...", activeClients.size());
            for (Map.Entry<String, LspClient> entry : activeClients.entrySet()) {
                try {
                    log.info("关闭 Language Server: {}", entry.getKey());
                    entry.getValue().shutdown();
                } catch (Exception e) {
                    log.warn("关闭 Language Server 异常 [{}]: {}", entry.getKey(), e.getMessage());
                }
            }
            activeClients.clear();
            log.info("所有 Language Server 已关闭");
        } finally {
            clientLock.unlock();
        }
    }

    /**
     * 重启指定名称的 Language Server。
     *
     * @param serverName Language Server 名称
     * @return 重启后的客户端，失败返回 null
     */
    public LspClient restartServer(String serverName) {
        clientLock.lock();
        try {
            LspClient old = activeClients.remove(serverName);
            if (old != null) {
                old.shutdown();
            }
            return getOrCreateClient(serverName);
        } finally {
            clientLock.unlock();
        }
    }

    /**
     * 获取所有活跃客户端的名称列表。
     */
    public List<String> getActiveServerNames() {
        return new ArrayList<>(activeClients.keySet());
    }

    /**
     * 活跃客户端数量。
     */
    public int getActiveClientCount() {
        return activeClients.size();
    }

    /**
     * 获取所有活跃客户端（只读快照）。
     */
    public Map<String, LspClient> getActiveClients() {
        return Collections.unmodifiableMap(activeClients);
    }
}
