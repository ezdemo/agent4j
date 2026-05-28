package site.sorghum.agent4j.web.service;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Init;

import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.bin.agent.Agent4jAgent;
import site.sorghum.agent4j.bin.agent.AgentLoopListener;
import site.sorghum.agent4j.bin.agent.AgentOutput;
import site.sorghum.agent4j.bin.command.ChatCommandRegistry;
import site.sorghum.agent4j.bin.config.Agent4jConfig;
import site.sorghum.agent4j.bin.session.SessionStore;
import site.sorghum.agent4j.bin.tool.ToolDef;
import site.sorghum.agent4j.bin.workspace.WorkspaceManager;

import org.noear.snack4.ONode;
import org.noear.solon.Solon;

import java.io.IOException;
import java.util.*;

/**
 * Agent 单例服务 —— 管理 Agent4jAgent 的生命周期和并发访问。
 * <p>
 * Web 模式下全局只有一个 Agent 实例。所有聊天请求通过串行锁保证线程安全。
 * 同时提供 SSE 事件桥接：将 AgentOutput 事件实时转发到 SseEmitter。
 * </p>
 *
 * <p>
 * <strong>关于命令操作：</strong>retry/rewind/compact/plan/hitl/agree/deny 等命令
 * 已由 {@link site.sorghum.agent4j.bin.command.ChatCommandRegistry} 在
 * {@link Agent4jAgent#chat(String)} 中统一处理。前端直接发送命令字符串
 * （如 {@code "/retry"}、{@code "/compact"}）到聊天接口即可，
<skilltt> * 无需额外 REST API。</p>
 *
 * @author Sorghum
 */
@Component
public class AgentService {

    @Inject
    ChatCommandRegistry commandRegistry;
    private volatile Agent4jAgent agent;
    private final java.util.concurrent.locks.ReentrantLock chatLock = new java.util.concurrent.locks.ReentrantLock();

    /** 当前 SSE 输出（每次请求创建一个新的） */
    private volatile SseEmitter currentSseEmitter;

    /** 初始化 Agent（Solon 启动后自动调用） */
    @Init
    public void init() {
        try {
            Agent4jConfig config = Agent4jConfig.load();
            String apiUrl = envOr("OPENAI_BASE_URL", config.chatApiUrl());
            String apiKey = envOr("OPENAI_API_KEY", config.apiKey());
            String model = envOr("MODEL", config.model());

            if (apiKey == null || apiKey.isEmpty()) {
                System.err.println("[web] 未配置 apiKey，Agent 未初始化");
                return;
            }


            agent = Agent4jAgent.builder()
                    .config(config)
                    .apiUrl(apiUrl)
                    .apiKey(apiKey)
                    .model(model)
                    .workspace(config.workspaceDir())
                    .commandRegistry(commandRegistry)
                    .build();

            // 注册 token 用量追踪
            agent.setListener(new AgentLoopListener() {
                @Override
                public void onUsage(int promptTokens, int completionTokens, int totalTokens,
                                     int cacheHit, int cacheMiss) {
                    agent.addUsage(promptTokens, completionTokens, cacheHit, cacheMiss);
                }
            });

            // 默认使用 NOOP 输出（API 调用时由 SseEmitter 接管）
            agent.setOutput(AgentOutput.NOOP);

            System.out.println("[web] Agent 初始化完成 — 模型: " + model);
        } catch (Exception e) {
            System.err.println("[web] Agent 初始化失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** 获取 Agent 实例（可能为 null，如果初始化失败） */
    public Agent4jAgent getAgent() {
        return agent;
    }

    /** Agent 是否已初始化 */
    public boolean isReady() {
        return agent != null;
    }

    // ==================== 聊天 ====================

    /**
     * 同步聊天 —— 串行执行，返回完整回复。
     * <p>
     * 命令字符串（如 "/retry"、"/compact"）会由 Agent4jAgent.chat()
     * 自动路由到 {@link site.sorghum.agent4j.bin.command.ChatCommandRegistry} 处理。
     * </p>
     */
    public String chat(String message) throws IOException, InterruptedException {
        chatLock.lock();
        try {
            agent.setOutput(AgentOutput.NOOP);
            return agent.chat(message);
        } finally {
            agent.flushSession();
            agent.saveUsage();
            chatLock.unlock();
        }
    }

    /**
     * 流式聊天 —— 通过 AgentOutput 桥接到 SseEmitter。
     * <p>
     * 命令字符串同样在此通道处理，命令的输出通过 SSE 事件返回。
     * </p>
     */
    public void chatStream(String message, SseEmitter emitter) throws IOException, InterruptedException {
        chatLock.lock();
        try {
            this.currentSseEmitter = emitter;

            // 设置 AgentOutput：将所有事件桥接到 SSE
            agent.setOutput(new AgentOutput() {
                @Override
                public void onContentDelta(String token) {
                    emitter.sendContent(token);
                }

                @Override
                public void onContentComplete() {}

                @Override
                public void onReasoningDelta(String token) {
                    emitter.sendReasoning(token);
                }

                @Override
                public void onReasoningComplete() {}

                @Override
                public void onReasoning(String reasoning) {
                    if (reasoning != null && !reasoning.isEmpty()) {
                        emitter.sendReasoning(reasoning);
                    }
                }

                @Override
                public void onToolCall(String name, String args) {
                    emitter.sendToolCall(name, args);
                }

                @Override
                public void onToolResult(String name, String result) {
                    emitter.sendToolResult(name, result);
                }

                @Override
                public void onUsage(int promptTokens, int completionTokens, int totalTokens,
                                     int cacheHit, int cacheMiss) {
                    emitter.sendUsage(promptTokens, completionTokens, totalTokens, cacheHit, cacheMiss);
                    agent.addUsage(promptTokens, completionTokens, cacheHit, cacheMiss);
                }

                @Override
                public void onError(String error) {
                    emitter.sendError(error);
                }

                @Override
                public void onLog(LogLevel level, String message) {}

                @Override
                public void onMessage(String message) {}
            });

            String reply = agent.chat(message);

            // 发送最终回复
            if (reply != null && !reply.isEmpty()) {
                ONode replyNode = ONode.ofJson("{}").asObject();
                replyNode.set("content", reply);
                emitter.send("reply", replyNode.toJson());
            }
        } catch (Exception e) {
            try {
                emitter.sendError(e.getMessage());
            } catch (Exception ex) {
                // SSE连接可能已断开，忽略异常
                System.err.println("[web] 发送错误信息失败（可能SSE连接已断开）: " + ex.getMessage());
            }
        } finally {
            agent.setOutput(AgentOutput.NOOP);
            agent.flushSession();
            agent.saveUsage();
            this.currentSseEmitter = null;
            try {
                emitter.complete();
            } catch (Exception ex) {
                // SSE连接可能已断开，忽略异常
                System.err.println("[web] 完成SSE流失败（可能SSE连接已断开）: " + ex.getMessage());
            }
            chatLock.unlock();
        }
    }

    // ==================== 会话管理 ====================

    public void newSession() {
        requireAgent();
        agent.newSession();
    }

    public List<Map<String, Object>> listSessions(String workspaceHash) throws IOException {
        requireAgent();
        SessionStore store;
        
        if (workspaceHash != null && !workspaceHash.isEmpty()) {
            // 根据工作区 hash 获取对应的会话目录
            WorkspaceManager workspaceManager = agent.getWorkspaceManager();
            if (workspaceManager == null) {
                return new ArrayList<>();
            }
            
            // 查找工作区对应的会话目录
            try {
                List<WorkspaceManager.WorkspaceInfo> workspaces = workspaceManager.listWorkspaces();
                WorkspaceManager.WorkspaceInfo targetWorkspace = null;
                for (WorkspaceManager.WorkspaceInfo w : workspaces) {
                    if (w.hash.equals(workspaceHash)) {
                        targetWorkspace = w;
                        break;
                    }
                }
                
                if (targetWorkspace == null) {
                    System.err.println("[workspace] 未找到工作区: " + workspaceHash);
                    return new ArrayList<>();
                }
                
                // 获取该工作区的会话目录
                java.nio.file.Path sessionsDir = workspaceManager.getSessionsDir(targetWorkspace.path);
                if (!java.nio.file.Files.isDirectory(sessionsDir)) {
                    return new ArrayList<>();
                }
                
                // 创建临时的 SessionStore 来列出该工作区的会话
                store = new site.sorghum.agent4j.bin.session.JsonlSessionStore(sessionsDir);
            } catch (Exception e) {
                System.err.println("[workspace] 获取工作区会话失败: " + e.getMessage());
                return new ArrayList<>();
            }
        } else {
            // 使用当前会话的 store
            store = agent.getSessionStore();
        }
        
        List<SessionStore.SessionInfo> sessions = store.list();
        List<Map<String, Object>> result = new ArrayList<>();
        for (SessionStore.SessionInfo s : sessions) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", s.name);
            m.put("messageCount", s.messageCount);
            m.put("size", s.size);
            m.put("mtime", s.mtime);
            m.put("title", s.title);
            result.add(m);
        }
        return result;
    }

    public boolean switchSession(String name) {
        requireAgent();
        return agent.switchSession(name);
    }

    public boolean deleteSession(String name) throws IOException {
        requireAgent();
        return agent.getSessionStore().delete(name);
    }

    public Map<String, Object> getCurrentSession() {
        requireAgent();
        Map<String, Object> info = new LinkedHashMap<>();
        String currentName = agent.getSessionStore().currentName();
        info.put("name", currentName);
        info.put("historySize", agent.historySize());
        try {
            String title = agent.getSessionStore().getTitle(currentName);
            info.put("title", title);
        } catch (IOException e) {
            info.put("title", null);
        }
        return info;
    }

    // ==================== Agent 状态 ====================

    public Map<String, Object> getStatus() {
        requireAgent();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("ready", true);
        status.put("planMode", agent.isPlanMode());
        status.put("hitlMode", agent.isHitlMode());
        status.put("hasPendingHitl", agent.hasPendingHITL());
        status.put("historySize", agent.historySize());
        status.put("currentSession", agent.getSessionStore().currentName());
        status.put("workspace", agent.getWorkspace() != null ? agent.getWorkspace().toString() : null);

        long[] usage = agent.getSessionUsage();
        Map<String, Object> usageMap = new LinkedHashMap<>();
        usageMap.put("promptTokens", usage[0]);
        usageMap.put("completionTokens", usage[1]);
        usageMap.put("cacheHit", usage[2]);
        usageMap.put("cacheMiss", usage[3]);
        status.put("usage", usageMap);

        return status;
    }

    // ==================== 工作目录 ====================

    /**
     * 获取当前工作目录。
     */
    public String getWorkspace() {
        requireAgent();
        return agent.getWorkspace() != null ? agent.getWorkspace().toString() : null;
    }

    /**
     * 切换工作目录。
     *
     * @param path 新的工作目录路径
     * @return 切换成功返回 true，路径无效返回 false
     */
    public boolean switchWorkspace(String path) {
        requireAgent();
        if (path == null || path.isEmpty()) {
            return false;
        }
        java.nio.file.Path newPath = java.nio.file.Paths.get(path).toAbsolutePath();
        return agent.switchWorkspace(newPath);
    }

    /**
     * 获取所有工作区列表。
     */
    public List<Map<String, Object>> listWorkspaces() {
        requireAgent();
        WorkspaceManager workspaceManager = agent.getWorkspaceManager();
        if (workspaceManager == null) return new ArrayList<>();

        try {
            List<WorkspaceManager.WorkspaceInfo> workspaces = workspaceManager.listWorkspaces();
            List<Map<String, Object>> result = new ArrayList<>();
            for (WorkspaceManager.WorkspaceInfo w : workspaces) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("hash", w.hash);
                m.put("name", w.name);
                m.put("path", w.path);
                m.put("createdAt", w.createdAt);
                m.put("lastAccessedAt", w.lastAccessedAt);
                m.put("sessionCount", w.sessionCount);
                m.put("isActive", w.isActive);
                result.add(m);
            }
            return result;
        } catch (IOException e) {
            System.err.println("[workspace] 列出工作区失败: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 切换到指定工作区。
     *
     * @param workspacePath 工作区路径
     * @return 切换成功返回 true
     */
    public boolean switchToWorkspace(String workspacePath) {
        requireAgent();
        if (workspacePath == null || workspacePath.isEmpty()) {
            return false;
        }
        
        try {
            // 切换工作区
            boolean switched = switchWorkspace(workspacePath);
            if (!switched) return false;
            
            // 重新加载会话（使用新工作区的会话目录）
            agent.newSession();
            return true;
        } catch (Exception e) {
            System.err.println("[workspace] 切换工作区失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 删除工作区。
     *
     * @param hash 工作区 hash
     * @return 删除成功返回 true
     */
    public boolean deleteWorkspace(String hash) {
        requireAgent();
        WorkspaceManager workspaceManager = agent.getWorkspaceManager();
        if (workspaceManager == null) return false;

        try {
            return workspaceManager.deleteWorkspace(hash);
        } catch (IOException e) {
            System.err.println("[workspace] 删除工作区失败: " + e.getMessage());
            return false;
        }
    }

    public List<Map<String, Object>> getHistory() {
        requireAgent();
        try {
            return agent.getSessionStore().load();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // ==================== 工具 ====================

    public List<Map<String, Object>> listTools() {
        requireAgent();
        site.sorghum.agent4j.bin.tool.ToolRegistry registry = agent.getToolRegistry();
        if (registry == null) return new ArrayList<>();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, ToolDef> entry : registry.all().entrySet()) {
            ToolDef def = entry.getValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", def.name);
            m.put("description", def.description);
            m.put("readOnly", def.readOnly);
            m.put("stormExempt", def.stormExempt);
            m.put("parameters", def.toParametersSchema());
            result.add(m);
        }
        return result;
    }

    public Map<String, Object> getTool(String name) {
        requireAgent();
        site.sorghum.agent4j.bin.tool.ToolRegistry registry = agent.getToolRegistry();
        if (registry == null) return null;
        ToolDef def = registry.get(name);
        if (def == null) return null;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", def.name);
        m.put("description", def.description);
        m.put("readOnly", def.readOnly);
        m.put("stormExempt", def.stormExempt);
        m.put("parameters", def.toParametersSchema());
        return m;
    }

    public String executeTool(String name, Map<String, Object> arguments) {
        requireAgent();
        site.sorghum.agent4j.bin.tool.ToolRegistry registry = agent.getToolRegistry();
        if (registry == null) throw new IllegalStateException("ToolRegistry 未初始化");
        ToolDef def = registry.get(name);
        if (def == null) throw new IllegalArgumentException("未知工具: " + name);
        return def.fn.call(arguments != null ? arguments : new LinkedHashMap<String, Object>());
    }

    // ==================== 用量 ====================

    /**
     * 获取当前会话的 Token 用量统计。
     */
    public Map<String, Object> getUsage() {
        return getUsage(null, null);
    }

    /**
     * 获取指定工作区和会话的 Token 用量统计。
     * @param workspaceHash 工作区 hash（可选，null 时使用当前工作区）
     * @param sessionName   会话名称（可选，null 时使用当前会话）
     */
    public Map<String, Object> getUsage(String workspaceHash, String sessionName) {
        requireAgent();
        
        long[] u;
        
        // 如果指定了会话名，尝试从对应的 usage 文件加载
        if (sessionName != null && !sessionName.isEmpty()) {
            SessionStore store = null;
            
            // 如果指定了工作区 hash，获取该工作区的会话目录
            if (workspaceHash != null && !workspaceHash.isEmpty()) {
                WorkspaceManager workspaceManager = agent.getWorkspaceManager();
                if (workspaceManager != null) {
                    try {
                        List<WorkspaceManager.WorkspaceInfo> workspaces = workspaceManager.listWorkspaces();
                        for (WorkspaceManager.WorkspaceInfo w : workspaces) {
                            if (w.hash.equals(workspaceHash)) {
                                java.nio.file.Path sessionsDir = workspaceManager.getSessionsDir(w.path);
                                if (java.nio.file.Files.isDirectory(sessionsDir)) {
                                    store = new site.sorghum.agent4j.bin.session.JsonlSessionStore(sessionsDir);
                                }
                                break;
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("[usage] 获取工作区会话目录失败: " + e.getMessage());
                    }
                }
            }
            
            // 从指定会话加载 usage
            if (store != null) {
                u = store.loadUsage(sessionName);
            } else {
                // 回退到当前会话
                u = agent.getSessionUsage();
            }
        } else {
            // 使用当前会话的 usage
            u = agent.getSessionUsage();
        }
        
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("promptTokens", u[0]);
        m.put("completionTokens", u[1]);
        m.put("cacheHit", u[2]);
        m.put("cacheMiss", u[3]);
        // 优先使用持久化的 lastPromptTokens（0 时回退到 AgentLoop 的实时值）
        long sessionLast = u.length > 4 ? u[4] : 0;
        m.put("lastPromptTokens", sessionLast > 0 ? sessionLast : agent.getLastPromptTokens());
        m.put("maxContextTokens", agent.getMaxContextTokens());
        return m;
    }

    // ==================== 模型 ====================

    /**
     * 更新当前模型。
     * @param model 新的模型名称
     */
    public void updateModel(String model) {
        requireAgent();
        agent.updateModel(model);
    }

    // ==================== 辅助 ====================

    private void requireAgent() {
        if (agent == null) {
            throw new IllegalStateException("Agent 未初始化，请检查 ~/.agent4j/config.json 配置");
        }
    }

    private static String envOr(String key, String fallback) {
        String v = System.getenv(key);
        return (v != null && !v.isEmpty()) ? v : fallback;
    }
}
