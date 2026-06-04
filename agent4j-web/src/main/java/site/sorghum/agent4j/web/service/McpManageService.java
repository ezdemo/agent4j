package site.sorghum.agent4j.web.service;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.web.model.McpServerDTO;
import site.sorghum.agent4j.web.model.McpToolListDTO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 服务器管理服务。
 * <p>
 * 管理 MCP 服务器配置的增删改查、启停控制、连接检测及工具权限管理。
 * TODO: 业务逻辑待实现，目前仅提供空壳方法，后续接入 MCP 客户端库。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
@Component
public class McpManageService {

    /** 内存存储：服务器名称 → 配置 */
    private final Map<String, McpServerDTO> serverStore = new ConcurrentHashMap<>();

    /** 内存存储：服务器名称 → 禁用工具列表 */
    private final Map<String, List<String>> disallowedToolsStore = new ConcurrentHashMap<>();

    // ==================== 服务器 CRUD ====================

    /**
     * 获取所有 MCP 服务器列表。
     */
    public List<McpServerDTO> listServers() {
        // TODO: 从持久化存储加载
        return new ArrayList<>(serverStore.values());
    }

    /**
     * 新增 MCP 服务器。
     */
    public McpServerDTO addServer(McpServerDTO server) {
        // TODO: 实现服务器注册逻辑
        serverStore.put(server.name, server);
        log.info("MCP 服务器已新增: {}", server.name);
        return server;
    }

    /**
     * 更新 MCP 服务器配置。
     *
     * @param originalName 原名称（用于查找）
     * @param server       新的配置数据
     */
    public McpServerDTO updateServer(String originalName, McpServerDTO server) {
        // TODO: 实现服务器更新逻辑
        if (originalName != null && !originalName.equals(server.name)) {
            serverStore.remove(originalName);
        }
        serverStore.put(server.name, server);
        log.info("MCP 服务器已更新: {} -> {}", originalName, server.name);
        return server;
    }

    /**
     * 删除 MCP 服务器。
     */
    public void removeServer(String name) {
        // TODO: 实现服务器删除逻辑
        serverStore.remove(name);
        disallowedToolsStore.remove(name);
        log.info("MCP 服务器已删除: {}", name);
    }

    // ==================== 启停控制 ====================

    /**
     * 启用或禁用 MCP 服务器。
     */
    public McpServerDTO toggleServer(String name, boolean enabled) {
        // TODO: 实现启停逻辑（连接/断开 MCP 服务器）
        McpServerDTO server = serverStore.get(name);
        if (server != null) {
            server.setEnabled(enabled);
            log.info("MCP 服务器 {} 已{}", name, enabled ? "启用" : "禁用");
        }
        return server;
    }

    // ==================== 连接检测 ====================

    /**
     * 检测 MCP 服务器连接是否可达。
     *
     * @param server 服务器配置（基于当前表单输入实时组装）
     * @return true 表示连接成功，false 表示连接失败
     */
    public boolean checkConnection(McpServerDTO server) {
        // TODO: 实现连接检测逻辑（启动进程或发送 HTTP 请求测试）
        log.info("检测 MCP 服务器连接: {} (type={})", server.name, server.type);
        return false;
    }

    // ==================== 工具管理 ====================

    /**
     * 获取指定服务器的工具列表及权限状态。
     */
    public McpToolListDTO listTools(String serverName) {
        // TODO: 实现工具列表获取逻辑（连接 MCP 服务器查询工具清单）
        McpServerDTO server = serverStore.get(serverName);
        if (server == null || !server.isEnabled()) {
            return new McpToolListDTO(false, Collections.emptyList(),
                    disallowedToolsStore.getOrDefault(serverName, Collections.emptyList()));
        }
        List<String> disallowed = disallowedToolsStore.getOrDefault(serverName, Collections.emptyList());
        return new McpToolListDTO(true, Collections.emptyList(), disallowed);
    }

    /**
     * 保存工具权限配置。
     *
     * @param serverName      服务器名称
     * @param disallowedTools 需禁用的工具名称列表
     */
    public void saveToolPermissions(String serverName, List<String> disallowedTools) {
        // TODO: 实现工具权限持久化逻辑
        disallowedToolsStore.put(serverName, disallowedTools != null ? disallowedTools : Collections.emptyList());
        log.info("MCP 服务器 {} 工具权限已保存, 禁用 {} 个工具", serverName,
                disallowedTools != null ? disallowedTools.size() : 0);
    }
}
