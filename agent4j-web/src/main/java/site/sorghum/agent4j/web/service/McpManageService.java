package site.sorghum.agent4j.web.service;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.ai.mcp.client.McpClientProvider;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.tool.solon.mcp.Agent4JMcpSkill;
import site.sorghum.agent4j.web.model.McpServerDTO;
import site.sorghum.agent4j.web.model.McpToolInfoDTO;
import site.sorghum.agent4j.web.model.McpToolListDTO;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * MCP 服务器管理服务。
 * <p>
 * 基于 Agent4JMcpSkill（McpGatewaySkill）实现 MCP 服务器的增删改查、
 * 启停控制、连接检测及工具权限管理。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
@Component
public class McpManageService {

    @Inject
    private Agent4JMcpSkill agent4JMcpSkill;

    /** 内存存储：服务器名称 → 配置（持久化待实现） */
    private final Map<String, McpServerDTO> serverStore = new ConcurrentHashMap<>();

    /** 内存存储：服务器名称 → 禁用工具列表 */
    private final Map<String, Set<String>> disallowedToolsStore = new ConcurrentHashMap<>();

    // ==================== 服务器 CRUD ====================

    /**
     * 获取所有 MCP 服务器列表。
     */
    public List<McpServerDTO> listServers() {
        // 从 serverStore 返回，确保与 skill 注册状态同步
        return new ArrayList<>(serverStore.values());
    }

    /**
     * 新增 MCP 服务器。
     */
    public McpServerDTO addServer(McpServerDTO server) {
        try {
            // 构建 McpClientProvider 并注册到 skill
            var builder = McpClientProvider.builder()
                    .name(server.name);

            if ("stdio".equals(server.type)) {
                builder.channel("stdio")
                        .command(server.command);
                if (server.args != null && !server.args.isEmpty()) {
                    builder.args(server.args);
                }
                if (server.env != null && !server.env.isEmpty()) {
                    builder.env(server.env);
                }
            } else {
                // sse / streamable
                builder.channel(server.type)
                        .url(server.url);
                if (server.headers != null && !server.headers.isEmpty()) {
                    builder.headers(server.headers);
                }
                if (server.timeout != null && !server.timeout.isBlank()) {
                    try {
                        String t = server.timeout.trim();
                        if (t.endsWith("s")) {
                            builder.timeout(Duration.ofSeconds(Long.parseLong(t.substring(0, t.length() - 1))));
                        } else {
                            builder.timeout(Duration.parse("PT" + t));
                        }
                    } catch (Exception e) {
                        log.warn("解析超时配置失败: {}", server.timeout);
                    }
                }
            }

            McpClientProvider provider = builder.build();
            agent4JMcpSkill.addMcpServer(server.name, provider);
            serverStore.put(server.name, server);
            log.info("MCP 服务器已新增并注册: {} (type={})", server.name, server.type);
        } catch (Exception e) {
            log.error("MCP 服务器注册失败: {}", server.name, e);
            throw new RuntimeException("MCP 服务器注册失败: " + e.getMessage(), e);
        }
        return server;
    }

    /**
     * 更新 MCP 服务器配置（先移除旧配置，再注册新配置）。
     */
    public McpServerDTO updateServer(String originalName, McpServerDTO server) {
        // 先移除旧的（如果名称变了）
        if (originalName != null && !originalName.equals(server.name)) {
            removeMcpServerFromSkill(originalName);
            serverStore.remove(originalName);
        } else if (originalName != null) {
            removeMcpServerFromSkill(originalName);
        }

        // 注册新的
        return addServer(server);
    }

    /**
     * 删除 MCP 服务器。
     */
    public void removeServer(String name) {
        removeMcpServerFromSkill(name);
        serverStore.remove(name);
        disallowedToolsStore.remove(name);
        log.info("MCP 服务器已删除: {}", name);
    }

    // ==================== 启停控制 ====================

    /**
     * 启用或禁用 MCP 服务器。
     */
    public McpServerDTO toggleServer(String name, boolean enabled) {
        McpServerDTO server = serverStore.get(name);
        if (server == null) return null;

        if (enabled) {
            // 启用：重新注册
            addServer(server);
        } else {
            // 禁用：从 skill 移除
            removeMcpServerFromSkill(name);
        }

        server.setEnabled(enabled);
        log.info("MCP 服务器 {} 已{}", name, enabled ? "启用" : "禁用");
        return server;
    }

    // ==================== 连接检测 ====================

    /**
     * 检测 MCP 服务器连接是否可达。
     * 通过尝试构建 Provider 并获取工具列表来验证连通性。
     */
    public boolean checkConnection(McpServerDTO server) {
        try {
            var builder = McpClientProvider.builder()
                    .name(server.name + "-check");

            if ("stdio".equals(server.type)) {
                builder.channel("stdio")
                        .command(server.command);
                if (server.args != null && !server.args.isEmpty()) {
                    builder.args(server.args);
                }
                if (server.env != null && !server.env.isEmpty()) {
                    builder.env(server.env);
                }
                // stdio 检测：超时设短一些
                builder.timeout(Duration.ofSeconds(5));
            } else {
                builder.channel(server.type)
                        .url(server.url);
                if (server.headers != null && !server.headers.isEmpty()) {
                    builder.headers(server.headers);
                }
                builder.timeout(Duration.ofSeconds(5));
            }

            McpClientProvider provider = builder.build();
            // 尝试获取工具列表，若成功则连接正常
            Collection<FunctionTool> tools = provider.getTools();
            provider.close();
            log.info("MCP 服务器连接检测成功: {} (type={}, tools={})", server.name, server.type, tools.size());
            return true;
        } catch (Exception e) {
            log.warn("MCP 服务器连接检测失败: {} (type={}): {}", server.name, server.type, e.getMessage());
            return false;
        }
    }

    // ==================== 工具管理 ====================

    /**
     * 获取指定服务器的工具列表及权限状态。
     */
    public McpToolListDTO listTools(String serverName) {
        McpServerDTO server = serverStore.get(serverName);
        if (server == null) {
            return new McpToolListDTO(false, Collections.emptyList(), Collections.emptyList());
        }

        Set<String> disallowed = disallowedToolsStore.getOrDefault(serverName, Collections.emptySet());

        // 从 skill 获取已注册的 provider 来获取工具列表
        try {
            McpClientProvider provider = agent4JMcpSkill.getMcpServer(serverName);
            if (provider == null) {
                return new McpToolListDTO(false, Collections.emptyList(), new ArrayList<>(disallowed));
            }

            Collection<FunctionTool> tools = provider.getTools();
            List<McpToolInfoDTO> toolList = tools.stream()
                    .map(t -> new McpToolInfoDTO(t.name(), t.description() != null ? t.description() : ""))
                    .collect(Collectors.toList());

            return new McpToolListDTO(true, toolList, new ArrayList<>(disallowed));
        } catch (Exception e) {
            log.warn("获取 MCP 服务器工具列表失败: {}", serverName, e);
            return new McpToolListDTO(false, Collections.emptyList(), new ArrayList<>(disallowed));
        }
    }

    /**
     * 保存工具权限配置。
     */
    public void saveToolPermissions(String serverName, List<String> disallowedTools) {
        disallowedToolsStore.put(serverName, disallowedTools != null
                ? new LinkedHashSet<>(disallowedTools)
                : Collections.emptySet());
        log.info("MCP 服务器 {} 工具权限已保存, 禁用 {} 个工具", serverName,
                disallowedTools != null ? disallowedTools.size() : 0);
    }

    // ==================== 私有辅助 ====================

    private void removeMcpServerFromSkill(String name) {
        try {
            agent4JMcpSkill.removeMcpServer(name);
        } catch (Exception e) {
            log.warn("从 Skill 移除 MCP 服务器失败: {}", name, e);
        }
    }
}
