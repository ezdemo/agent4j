package site.sorghum.agent4j.web.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Init;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.bin.agent.context.ConversationContext;
import site.sorghum.agent4j.bin.agent.core.Agent4jAgent;
import site.sorghum.agent4j.bin.agent.model.ChatMessage;
import site.sorghum.agent4j.bin.agent.model.UserMessage;
import site.sorghum.agent4j.bin.command.ChatCommandRegistry;
import site.sorghum.agent4j.bin.config.Agent4jConfig;
import site.sorghum.agent4j.bin.config.ConfigService;
import site.sorghum.agent4j.bin.model.HttpModelClient;
import site.sorghum.agent4j.bin.model.ModelClient;
import site.sorghum.agent4j.bin.session.JsonlSessionStore;
import site.sorghum.agent4j.bin.session.SessionStore;
import site.sorghum.agent4j.bin.tool.ToolRegistry;
import site.sorghum.agent4j.bin.tool.ToolSystemInitializer;
import site.sorghum.agent4j.bin.workspace.WorkspaceManager;
import site.sorghum.agent4j.tool.AgentOutput;
import site.sorghum.agent4j.web.common.ServiceException;
import site.sorghum.agent4j.web.common.UsageCostCalculator;
import site.sorghum.agent4j.web.model.*;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Agent 会话级服务 —— 管理 Agent4jAgent 的生命周期和并发访问。
 * <p>
 * 实现"一个会话一个 Agent"架构：
 * - 每个会话（workspacePath::sessionName）拥有独立的 Agent4jAgent 实例
 * - 共享 ModelClient、ToolRegistry 减少资源消耗
 * - 每个 Agent 有自己的 ReentrantLock，支持并发聊天
 * - 使用 LRU 缓存策略管理 Agent 实例数量
 * @author Sorghum
 */
@Slf4j
@Component
public class AgentService {

    /**
     * 最大缓存 Agent 数量
     */
    private static final int MAX_CACHE_SIZE = 50;
    /**
     * 当前线程正在处理的会话名称（用于工具执行时获取 sessionId）
     */
    private static final ThreadLocal<String> CURRENT_SESSION_NAME = new ThreadLocal<>();

    /**
     * 会话级 Agent 缓存（LRU 淘汰策略）。
     * <p>
     * 封装 agents、order、locks、currentNames 四个内部结构，
     * 对外提供统一的 get/put/evict/clear/lock 操作。
     * </p>
     */
    private static class SessionAgentCache {
        private final ConcurrentHashMap<String, Agent4jAgent> agents = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, String> currentNames = new ConcurrentHashMap<>();
        private final java.util.concurrent.ConcurrentLinkedDeque<String> order = new java.util.concurrent.ConcurrentLinkedDeque<>();

        Agent4jAgent get(String key) {
            order.remove(key);
            order.addFirst(key);
            return agents.get(key);
        }

        void put(String key, Agent4jAgent agent) {
            agents.put(key, agent);
        }

        void evictIfNeeded() {
            while (agents.size() >= MAX_CACHE_SIZE) {
                String oldest = order.pollLast();
                if (oldest != null) {
                    Agent4jAgent removed = agents.remove(oldest);
                    if (removed != null) {
                        try {
                            removed.flushSession();
                            removed.saveUsage();
                        } catch (Exception e) {
                            log.info("[web] 淘汰 Agent 失败: {}", e.getMessage());
                        }
                        log.info("[web] LRU 淘汰 Agent: {}", oldest);
                    }
                }
            }
        }

        void clear() {
            agents.clear();
            order.clear();
            locks.clear();
            currentNames.clear();
        }

        Agent4jAgent remove(String key) {
            order.remove(key);
            locks.remove(key);
            return agents.remove(key);
        }

        Set<String> keySet() {
            return agents.keySet();
        }

        Set<Map.Entry<String, Agent4jAgent>> entrySet() {
            return agents.entrySet();
        }

        ReentrantLock getLock(String key) {
            return locks.computeIfAbsent(key, k -> new ReentrantLock());
        }

        String getCurrentName(String workspacePath) {
            return currentNames.get(workspacePath);
        }

        void setCurrentName(String workspacePath, String sessionName) {
            currentNames.put(workspacePath, sessionName);
        }

        Collection<Agent4jAgent> values() {
            return agents.values();
        }

        int size() {
            return agents.size();
        }
    }

    @Inject
    ChatCommandRegistry commandRegistry;

    @Inject
    private ConfigService configService;

    /**
     * 会话级 Agent 缓存
     */
    private final SessionAgentCache sessionCache = new SessionAgentCache();

    /**
     * 共享的 ToolRegistry（所有会话复用）
     */
    @Getter
    private volatile ToolRegistry sharedToolRegistry;
    /**
     * 共享的 Agent4jConfig
     */
    private volatile Agent4jConfig sharedConfig;

    /**
     * 加载用户级默认系统提示词。
     * 优先级：~/.agent4j/agent4j.md > 硬编码默认值
     */
    private static String loadDefaultSystemPrompt() {
        // 先确保默认提示词文件已安装（首运行自动从 classpath 复制）
        Agent4jAgent.Builder.installDefaultPromptIfNeeded();
        // 如果 ~/.agent4j/agent4j.md 存在，以其内容作为默认系统提示词
        Path homePrompt = Paths.get(System.getProperty("user.home"), ".agent4j", "agent4j.md");
        if (java.nio.file.Files.exists(homePrompt)) {
            try {
                String content = java.nio.file.Files.readString(homePrompt);
                if (!content.trim().isEmpty()) {
                    log.info("[prompt] 从 ~/.agent4j/agent4j.md 加载默认系统提示词（" + content.length() + " 字符）");
                    return content.trim();
                }
            } catch (IOException e) {
                log.error("[prompt] 读取 ~/.agent4j/agent4j.md 失败: {}", e.getMessage());
            }
        }
        return Agent4jAgent.Builder.DEFAULT_SYSTEM_PROMPT;
    }

    /**
     * 从环境变量读取配置，如果环境变量不存在则使用默认值。
     */
    private static String envOr(String envName, String defaultValue) {
        String value = System.getenv(envName);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    // ==================== 派生配置 getter（从 sharedConfig 实时计算，无需独立字段） ====================

    public String getSharedApiUrl() {
        return sharedConfig != null ? envOr("OPENAI_BASE_URL", sharedConfig.chatApiUrl()) : null;
    }

    public String getSharedApiKey() {
        return sharedConfig != null ? envOr("OPENAI_API_KEY", sharedConfig.apiKey()) : null;
    }

    public String getSharedModel() {
        return sharedConfig != null ? envOr("MODEL", sharedConfig.model()) : null;
    }

    /**
     * 计算工作区路径的 hash 值（与 WorkspaceManager 一致，MD5 前 12 位）。
     */
    public static String computeWorkspaceHash(String workspacePath) {
        return WorkspaceManager.computeHash(workspacePath);
    }

    /**
     * 初始化共享组件（Solon 启动后自动调用）
     */
    @Init
    public void init() {
        try {
            Agent4jConfig config = Agent4jConfig.load();
            if (!buildSharedComponents(config)) {
                log.error("[web] 未配置 apiKey，Agent 未初始化");
            } else {
                log.info("[web] Agent 共享组件初始化完成 — 模型: {}", getSharedModel());
            }
        } catch (Exception e) {
            log.error("Agent 共享组件初始化失败: ", e);
        }
    }

    /**
     * 销毁所有缓存的 Agent 和共享组件，然后重新从 config.json 初始化。
     * <p>
     * 适用于 baseUrl / apiKey 等不可热更新的配置变更后调用，
     * 下次聊天请求时 {@link #getOrCreateAgent} 会按全新配置重建 Agent。
     * </p>
     */
    public synchronized void reinitialize() {
        log.info("[config] 开始重新初始化 AgentService...");

        // 1. 先 flush 所有缓存的 Agent，避免数据丢失
        for (Agent4jAgent agent : sessionCache.values()) {
            try {
                agent.flushSession();
                agent.saveUsage();
            } catch (Exception e) {
                log.warn("[config] flush 淘汰 Agent 时异常: {}", e.getMessage());
            }
        }

        // 2. 清空所有缓存
        sessionCache.clear();

        // 3. 重新加载配置并构建共享组件
        try {
            Agent4jConfig config = Agent4jConfig.load();
            if (!buildSharedComponents(config)) {
                log.error("[config] 未配置 apiKey，重新初始化失败");
            } else {
                log.info("[config] AgentService 重新初始化完成 — 模型: {}, API: {}", getSharedModel(), getSharedApiUrl());
            }
        } catch (Exception e) {
            log.error("[config] 重新初始化 AgentService 失败", e);
        }
    }

    /**
     * 根据配置构建所有共享组件（ModelClient、ToolRegistry 等）。
     * <p>
     * 抽取自 {@link #init()} 和 {@link #reinitialize()} 中的重复逻辑，
     * 单一职责：加载配置并初始化共享字段，不含缓存清理等上下文操作。
     * </p>
     *
     * @param config 已加载的 Agent4j 配置
     * @return true 表示初始化成功，false 表示缺少必要的 API Key
     */
    private boolean buildSharedComponents(Agent4jConfig config) {
        String apiUrl = envOr("OPENAI_BASE_URL", config.chatApiUrl());
        String apiKey = envOr("OPENAI_API_KEY", config.apiKey());

        if (apiKey == null || apiKey.isEmpty()) {
            return false;
        }

        // 保存共享配置（仅保留 sharedConfig，其余由 getter 派生）
        this.sharedConfig = config;

        // 设置禁用工具
        Set<String> disabledTools = config.disabledTools();
        if (disabledTools != null && !disabledTools.isEmpty()) {
            log.info("[config] 已禁用工具: {}", String.join(", ", disabledTools));
        }

        // 屏蔽目录列表
        final List<String> blockedPaths = config.blockedPaths();
        if (blockedPaths != null && !blockedPaths.isEmpty()) {
            log.info("[config] 已屏蔽目录: {}", String.join(", ", blockedPaths));
        }

        // 使用 ToolSystemInitializer 统一初始化
        ToolSystemInitializer.Result initResult = ToolSystemInitializer.initialize(
                config.workspaceDir(), apiUrl, apiKey,
                disabledTools, blockedPaths,
                loadDefaultSystemPrompt());
        this.sharedToolRegistry = initResult.toolRegistry;
        this.sharedToolRegistry.setConfigService(configService);

        return true;
    }

    /**
     * 生成会话唯一标识。
     *
     * @param workspacePath 工作区路径
     * @param sessionName   会话名称
     * @return 唯一标识
     */
    private String generateSessionKey(String workspacePath, String sessionName) {
        // 使用默认工作区路径（如果未指定）
        if (workspacePath == null || workspacePath.isEmpty()) {
            if (sharedConfig != null && sharedConfig.workspaceDir() != null) {
                workspacePath = sharedConfig.workspaceDir().toAbsolutePath().toString();
            } else {
                workspacePath = Paths.get(System.getProperty("user.home"), ".agent4j").toString();
            }
        }
        // 使用默认会话名称（如果未指定）
        if (sessionName == null || sessionName.isEmpty()) {
            sessionName = "default";
        }
        return workspacePath + "::" + sessionName;
    }

    // ==================== 状态查询 ====================

    /**
     * 获取或创建会话级 Agent。
     *
     * @param sessionKey 会话唯一标识
     * @return Agent 实例
     */
    private Agent4jAgent getOrCreateAgent(String sessionKey) {
        // 更新访问顺序并检查缓存
        Agent4jAgent agent = sessionCache.get(sessionKey);
        if (agent != null) {
            return agent;
        }

        // 创建新 Agent
        synchronized (this) {
            // 双重检查
            agent = sessionCache.get(sessionKey);
            if (agent != null) {
                return agent;
            }

            // 解析会话标识
            String[] parts = sessionKey.split("::", 2);
            String workspacePath = parts[0];
            String sessionName = parts.length > 1 ? parts[1] : "default";

            // LRU 淘汰
            sessionCache.evictIfNeeded();

            // 构建轻量级 Agent（每个 Agent 拥有独立的 ModelClient）
            String apiUrl = envOr("OPENAI_BASE_URL", sharedConfig.chatApiUrl());
            String apiKey = envOr("OPENAI_API_KEY", sharedConfig.apiKey());
            String model = envOr("MODEL", sharedConfig.model());
            String reasoningEffort = sharedConfig.reasoningEffort();
            boolean hitl = sharedConfig.hitl();

            Agent4jAgent.Builder builder = Agent4jAgent.builder()
                    .config(sharedConfig)               // 加载 config 默认值
                    .apiUrl(apiUrl)                     // 覆盖为 env 感知的值
                    .apiKey(apiKey)
                    .model(model)
                    .workspace(Paths.get(workspacePath))
                    .commandRegistry(commandRegistry)
                    .hitl(hitl)
                    .modelClient(new HttpModelClient(apiUrl, apiKey, model, reasoningEffort));

            agent = builder.buildLightweight();

            // 切换到指定会话（含 default，以恢复 usage / title）
            agent.bindSession(sessionName);

            // 注册 token 用量追踪
            agent.setListener(new WebUsageListener(agent));

            // 默认使用 NOOP 输出（API 调用时由 SseEmitter 接管）
            agent.setOutput(AgentOutput.NOOP);

            // 缓存 Agent
            sessionCache.put(sessionKey, agent);

            log.info("[web] 创建新 Agent: {}", sessionKey);
            return agent;
        }
    }

    /**
     * 获取会话级锁。
     *
     * @param sessionKey 会话唯一标识
     * @return 锁
     */
    private ReentrantLock getSessionLock(String sessionKey) {
        return sessionCache.getLock(sessionKey);
    }

    /**
     * 共享组件是否已初始化
     */
    public boolean isReady() {
        return sharedConfig != null
                && sharedToolRegistry != null;
    }

    /**
     * 获取 Agent 整体状态（供前端状态面板使用）。
     *
     * @return 状态信息
     */
    public AgentStatusDTO getStatus() {
        boolean ready = isReady();
        String model = getSharedModel();
        String workspace = getWorkspace();
        int cacheSize = sessionCache.size();

        int historySize = 0;
        boolean planMode = false;
        boolean hitlMode = false;
        String sessionName = null;
        long promptTokens = 0;
        long completionTokens = 0;

        // 追加默认会话的详细信息
        String defaultKey = generateSessionKey(null, null);
        Agent4jAgent agent = sessionCache.get(defaultKey);
        if (agent != null) {
            historySize = agent.historySize();
            planMode = agent.isPlanMode();
            hitlMode = agent.isHitlMode();
            SessionStore store = agent.getSessionStore();
            if (store != null) {
                sessionName = store.currentName();
            }
            long[] usage = agent.getSessionUsage();
            promptTokens = usage[0];
            completionTokens = usage[1];
        }
        return new AgentStatusDTO(ready, model, workspace, cacheSize,
                historySize, planMode, hitlMode, sessionName,
                promptTokens, completionTokens);
    }

    /**
     * 获取会话的对话历史。
     * <p>
     * 直接返回 {@code List&lt;ChatMessage&gt;}，利用 {@code @ONodeAttr} 注解
     * 控制序列化字段名（snake_case）以及排除 boolean 辅助方法，避免 Map 转换。
     * </p>
     *
     * @param workspacePath 工作区路径（可选）
     * @param sessionName   会话名称（可选，不传则使用当前活跃会话）
     * @return 历史消息列表
     */
    public List<ChatMessage> getHistory(String workspacePath, String sessionName) {
        // 未指定会话名时，使用当前活跃会话
        if (sessionName == null || sessionName.isEmpty()) {
            sessionName = getCurrentSessionName(workspacePath);
        }
        if (sessionName == null) {
            return new ArrayList<>();
        }
        String sessionKey = generateSessionKey(workspacePath, sessionName);
        Agent4jAgent agent = getOrCreateAgent(sessionKey);
        SessionStore store = agent.getSessionStore();
        if (store != null) {
            try {
                return store.load();
            } catch (IOException e) {
                log.warn("[web] 加载会话历史失败: {}", e.getMessage());
            }
        }
        return new ArrayList<>();
    }

    /**
     * 中断当前聊天 —— 中断所有活跃的 Agent。
     */
    public void abortCurrentChat() {
        // 中断所有缓存的 Agent
        for (Agent4jAgent agent : sessionCache.values()) {
            if (agent != null) {
                agent.abort();
            }
        }
    }

    /**
     * 截断会话历史：删除包含指定 snapshotId 的用户消息及之后的所有消息，
     * 同时重写 JSONL 文件使持久化数据同步。
     *
     * @param workspacePath 工作区路径
     * @param sessionName   会话名称
     * @param snapshotId    要截断的快照 ID
     * @return 截断后被删除的用户消息文本（用于回填输入框），null 表示未找到
     */
    public String truncateHistoryBySnapshotId(String workspacePath, String sessionName, String snapshotId) {
        if (sessionName == null || sessionName.isEmpty()) {
            sessionName = getCurrentSessionName(workspacePath);
        }
        if (sessionName == null || snapshotId == null) return null;
        String sessionKey = generateSessionKey(workspacePath, sessionName);
        Agent4jAgent agent = getOrCreateAgent(sessionKey);
        ConversationContext ctx = agent.getCtx();
        if (ctx == null) return null;
        List<ChatMessage> history = ctx.getHistory();
        int targetIdx = -1;
        String rollbackText = null;
        for (int i = 0; i < history.size(); i++) {
            ChatMessage msg = history.get(i);
            if ("user".equals(msg.getRole()) && snapshotId.equals(msg.getSnapshotId())) {
                targetIdx = i;
                rollbackText = msg.getContent();
                break;
            }
        }
        if (targetIdx < 0) return null;
        // 截断历史并持久化
        ctx.truncate(targetIdx);
        return rollbackText;
    }

    // ==================== 会话管理 ====================

    /**
     * 同步聊天（多模态）—— 使用 {@link UserMessage} 统一表示文本+图片。
     */
    public String chat(UserMessage userMessage, String workspacePath, String sessionName) {
        String sessionKey = generateSessionKey(workspacePath, sessionName);
        ReentrantLock lock = getSessionLock(sessionKey);
        lock.lock();

        // 设置当前会话名称到 ThreadLocal，供工具执行时获取
        String effectiveSessionName = sessionName != null ? sessionName : "default";
        CURRENT_SESSION_NAME.set(effectiveSessionName);

        try {
            Agent4jAgent agent = getOrCreateAgent(sessionKey);

            agent.setOutput(AgentOutput.NOOP);
            // 设置会话ID到 AgentLoop
            String sessionId = sessionName != null ? sessionName : "default";
            agent.setSessionId(sessionId);
            return agent.chat(userMessage);
        } finally {
            // 清理 ThreadLocal
            CURRENT_SESSION_NAME.remove();
            // 刷入会话数据
            Agent4jAgent agent = sessionCache.get(sessionKey);
            if (agent != null) {
                agent.flushSession();
                agent.saveUsage();
            }
            lock.unlock();
        }
    }

    /**
     * 流式聊天（多模态）—— 使用 {@link UserMessage} 统一表示文本+图片。
     */
    public void chatStream(UserMessage userMessage, String workspacePath, String sessionName, SseEmitter emitter) {
        String sessionKey = generateSessionKey(workspacePath, sessionName);
        ReentrantLock lock = getSessionLock(sessionKey);
        lock.lock();

        // 设置当前会话名称到 ThreadLocal，供工具执行时获取
        String effectiveSessionName = sessionName != null ? sessionName : "default";
        CURRENT_SESSION_NAME.set(effectiveSessionName);

        try {
            Agent4jAgent agent = getOrCreateAgent(sessionKey);

            // 设置 AgentOutput：将所有事件桥接到 SSE
            agent.setOutput(new SseAgentOutput(emitter));

            String reply = agent.chat(userMessage);

            // 发送最终完整回复（使用 complete 事件，与增量 content 事件区分）
            // HITL 待审批时跳过：interceptForHITL/interceptForSandboxHITL 已通过
            // output.onContentDelta() 发送过 HITL 消息，此处不应重复发送
            if (reply != null && !reply.isEmpty() && agent.noPendingHITL()) {
                emitter.sendComplete(reply);
            }
        } catch (Exception e) {
            try {
                emitter.sendError(e.getMessage());
            } catch (Exception ex) {
                // SSE连接可能已断开，忽略异常
                log.warn("[web] 发送错误信息失败（可能SSE连接已断开）: {}", ex.getMessage());
            }
        } finally {
            // 清理 ThreadLocal
            CURRENT_SESSION_NAME.remove();
            // 恢复 Agent 输出
            Agent4jAgent agent = sessionCache.get(sessionKey);
            if (agent != null) {
                agent.setOutput(AgentOutput.NOOP);
                agent.flushSession();
                agent.saveUsage();
            }

            try {
                emitter.complete();
            } catch (Exception ex) {
                // SSE连接可能已断开，忽略异常
                log.warn("[web] 完成SSE流失败（可能SSE连接已断开）: {}", ex.getMessage());
            }

            lock.unlock();
        }
    }

    /**
     * 创建新会话（前端指定 sessionId，延迟持久化：文件在首次发消息时才创建）。
     *
     * @param workspacePath 工作区路径（可选）
     * @param sessionName   会话名称（前端指定，为空则自动生成）
     * @return 实际使用的会话名
     */
    public String newSession(String workspacePath, String sessionName) {

        // 未指定会话名时自动生成
        if (sessionName == null || sessionName.isEmpty()) {
            sessionName = "agent4j-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(java.time.LocalDateTime.now());
        }

        // 直接以目标会话名创建/获取 Agent（switchTo 是惰性的，不创建文件）
        switchSession(workspacePath, sessionName);

        return sessionName;
    }

    /**
     * 列出会话。
     *
     * @param workspacePath 工作区路径（可选）
     * @return 会话列表
     */
    public List<SessionInfoDTO> listSessions(String workspacePath) throws IOException {
        if (!isReady()) {
            return Collections.emptyList();
        }

        // 获取一个 Agent 实例来访问 SessionStore
        String sessionKey = generateSessionKey(workspacePath, null);
        Agent4jAgent agent = getOrCreateAgent(sessionKey);

        SessionStore store = agent.getSessionStore();
        if (store == null) {
            return Collections.emptyList();
        }

        // 确定当前会话名（优先用追踪记录，其次用 store.currentName）
        String resolvedPath = workspacePath != null ? workspacePath : getWorkspace();
        if (resolvedPath == null) {
            return Collections.emptyList();
        }
        String activeSession = sessionCache.getCurrentName(resolvedPath);
        if (activeSession == null) {
            activeSession = store.currentName();
        }
        List<SessionInfoDTO> sessions = new ArrayList<>();
        for (SessionStore.SessionInfo sessionInfo : store.list()) {
            sessions.add(new SessionInfoDTO(
                    sessionInfo.name(),
                    store.getTitle(sessionInfo.name()),
                    sessionInfo.messageCount(),
                    sessionInfo.name().equals(activeSession),
                    sessionInfo.mtime()
            ));
        }
        return sessions;
    }

    /**
     * 切换会话。
     *
     * @param workspacePath 工作区路径（可选）
     * @param sessionName   会话名称
     * @return 切换是否成功
     */
    public boolean switchSession(String workspacePath, String sessionName) {
        if (!isReady() || sessionName == null) {
            return false;
        }

        // 记录当前活跃会话
        String resolvedPath = workspacePath != null ? workspacePath : getWorkspace();
        sessionCache.setCurrentName(resolvedPath, sessionName);
        return true;
    }

    /**
     * 获取当前会话名称。
     *
     * @param workspacePath 工作区路径（可选）
     * @return 当前会话名称
     */
    public String getCurrentSessionName(String workspacePath) {
        // 优先从追踪记录中获取
        String resolvedPath = workspacePath != null ? workspacePath : getWorkspace();
        String tracked = sessionCache.getCurrentName(resolvedPath);
        if (tracked != null) {
            return tracked;
        }
        // 回退到 store 查询
        String sessionKey = generateSessionKey(workspacePath, null);
        Agent4jAgent agent = sessionCache.get(sessionKey);
        if (agent != null) {
            SessionStore store = agent.getSessionStore();
            return store != null ? store.currentName() : null;
        }
        return null;
    }

    /**
     * 获取指定会话的 token 用量（返回 DTO）。
     *
     * @param workspacePath 工作区路径（可选）
     * @param sessionName   会话名称（可选）
     * @return usage 数据
     */
    public UsageDTO getSessionUsageMap(String workspacePath, String sessionName) {
        String sessionKey = generateSessionKey(workspacePath, sessionName);
        Agent4jAgent agent = sessionCache.get(sessionKey);

        long promptTokens = 0;
        long completionTokens = 0;
        long cacheHit = 0;
        long cacheMiss = 0;
        long lastPromptTokens = 0;
        int maxContextTokens = 128000;

        if (agent != null) {
            long[] usage = agent.getSessionUsage();
            promptTokens = usage[0];
            completionTokens = usage[1];
            cacheHit = usage[2];
            cacheMiss = usage[3];
            lastPromptTokens = usage.length > 4 ? usage[4] : 0;
            maxContextTokens = agent.getMaxContextTokens();
        }

        String currentModel = null;
        boolean hasPrice = false;
        double inputCost = 0;
        double cacheCost = 0;
        double outputCost = 0;
        double totalCost = 0;
        String currency = null;

        // 价格计算：优先按模型分别计费，解决模型中途切换导致计价错乱
        try {
            Agent4jConfig config = Agent4jConfig.load();
            currentModel = config.model();
            Map<String, Map<String, Double>> prices = config.price();

            if (agent == null) {
                log.warn("[usage] Agent 为 null，无法获取模型用量");
                return new UsageDTO(0, 0, 0, 0, 0, maxContextTokens, 0, null, false, 0, 0, 0, 0, null);
            }
            Map<String, long[]> mu = agent.getModelUsage();

            if (mu != null && !mu.isEmpty()) {
                // 按模型分别计算费用
                for (Map.Entry<String, long[]> entry : mu.entrySet()) {
                    String modelName = entry.getKey();
                    long[] usage = entry.getValue();
                    Map<String, Double> modelPrice = prices.get(modelName);
                    if (modelPrice != null && !modelPrice.isEmpty()) {
                        hasPrice = true;
                        double inputRate = modelPrice.getOrDefault("input", 0.0);
                        double cacheRate = modelPrice.getOrDefault("cache", 0.0);
                        double outputRate = modelPrice.getOrDefault("output", 0.0);

                        long nonCacheInput = Math.max(0, usage[0] - usage[2]);
                        inputCost += nonCacheInput / 1_000_000.0 * inputRate;
                        cacheCost += usage[2] / 1_000_000.0 * cacheRate;
                        outputCost += usage[1] / 1_000_000.0 * outputRate;
                    }
                }
                totalCost = inputCost + cacheCost + outputCost;
                currency = hasPrice ? "CNY" : null;

                inputCost = Math.round(inputCost * 10000.0) / 10000.0;
                cacheCost = Math.round(cacheCost * 10000.0) / 10000.0;
                outputCost = Math.round(outputCost * 10000.0) / 10000.0;
                totalCost = Math.round(totalCost * 10000.0) / 10000.0;
            } else {
                // 无 per-model 数据（旧格式 .usage 文件），回退到当前模型计费
                totalCost = UsageCostCalculator.calc(prices, currentModel, promptTokens, completionTokens, cacheHit);
                hasPrice = totalCost > 0;
                currency = hasPrice ? "CNY" : null;
                inputCost = totalCost;
                totalCost = Math.round(totalCost * 10000.0) / 10000.0;
                inputCost = Math.round(inputCost * 10000.0) / 10000.0;
            }
        } catch (Exception e) {
            // 价格计算失败不影响主逻辑
        }

        return new UsageDTO(
                promptTokens, completionTokens, cacheHit, cacheMiss,
                lastPromptTokens, maxContextTokens,
                promptTokens + completionTokens,
                currentModel, hasPrice,
                inputCost, cacheCost, outputCost, totalCost,
                currency
        );
    }

    // ==================== 工具方法 ====================

    /**
     * 获取缓存的 Agent 数量。
     *
     * @return 缓存数量
     */
    public int getCacheSize() {
        return sessionCache.size();
    }

    /**
     * 清除指定会话的 Agent 缓存。
     *
     * @param workspacePath 工作区路径（可选）
     * @param sessionName   会话名称（可选）
     */
    public void evictAgent(String workspacePath, String sessionName) {
        String sessionKey = generateSessionKey(workspacePath, sessionName);
        Agent4jAgent removed = sessionCache.remove(sessionKey);
        if (removed != null) {
            try {
                removed.flushSession();
                removed.saveUsage();
            } catch (Exception e) {
                log.warn("[web] 清除 Agent 失败: {}", e.getMessage());
            }
            log.info("[web] 已清除 Agent: " + sessionKey);
        }
    }

    /**
     * 删除指定会话：清除 Agent 缓存 + 删除磁盘文件（.jsonl / .usage / .meta）。
     *
     * @param workspacePath 工作区路径（可选）
     * @param sessionName   会话名称
     */
    public void deleteSession(String workspacePath, String sessionName) {
        // 1. 清除 Agent 缓存
        evictAgent(workspacePath, sessionName);
        // 2. 删除磁盘文件
        try {
            String resolvedPath = workspacePath != null ? workspacePath : getWorkspace();
            if (resolvedPath == null) return;
            WorkspaceManager wm = new WorkspaceManager();
            Path sessionsDir = wm.getSessionsDir(resolvedPath);
            if (sessionsDir == null || !java.nio.file.Files.exists(sessionsDir)) return;
            SessionStore store = new JsonlSessionStore(sessionsDir);
            boolean ok = store.delete(sessionName);
            if (ok) {
                log.info("[web] 已删除会话文件: {}", sessionName);
            }
        } catch (Exception e) {
            log.warn("[web] 删除会话文件失败: {}", e.getMessage());
        }
    }

    /**
     * 清空所有会话：清除所有 Agent 缓存 + 删除所有会话磁盘文件。
     *
     * @param workspacePath 工作区路径（可选）
     */
    public void clearAllSessions(String workspacePath) {
        // 1. 清除所有 Agent 缓存
        evictAllAgents();
        // 2. 删除所有会话磁盘文件
        String resolvedPath = workspacePath != null ? workspacePath : getWorkspace();
        if (resolvedPath == null) return;
        WorkspaceManager wm = new WorkspaceManager();
        Path sessionsDir = wm.getSessionsDir(resolvedPath);
        if (sessionsDir == null || !java.nio.file.Files.exists(sessionsDir)) return;
        SessionStore store = new JsonlSessionStore(sessionsDir);
        store.clearAll();
        log.info("[web] 已清空所有会话");
    }

    // ==================== 兼容旧接口 ====================

    /**
     * 清除所有 Agent 缓存。
     */
    public void evictAllAgents() {
        for (Map.Entry<String, Agent4jAgent> entry : sessionCache.entrySet()) {
            Agent4jAgent agent = entry.getValue();
            if (agent != null) {
                try {
                    agent.flushSession();
                    agent.saveUsage();
                } catch (Exception e) {
                    log.warn("[web] 清除 Agent 失败: {}", e.getMessage());
                }
            }
        }
        sessionCache.clear();
        log.info("[web] 已清除所有 Agent 缓存");
    }

    /**
     * 通过 workspaceHash 反查工作区路径。
     * <p>
     * 优先使用 {@link WorkspaceManager#listWorkspaces()} 从磁盘读取 hash→path 映射，
     * 不依赖 sessionCache，在缓存为空时也能正确解析。
     * 兜底检查默认工作区和 sessionCache（兼容旧版）。
     * </p>
     *
     * @param hash 工作区 hash（或路径本身）
     * @return 解析后的工作区路径，找不到时返回 null
     */
    public String resolveWorkspacePath(String hash) {
        if (hash == null || hash.isEmpty()) {
            return null;
        }
        // 1. 优先使用 WorkspaceManager 的磁盘索引（可靠，不依赖 sessionCache）
        try {
            WorkspaceManager wm = new WorkspaceManager();
            List<WorkspaceManager.WorkspaceInfo> workspaces = wm.listWorkspaces();
            for (WorkspaceManager.WorkspaceInfo ws : workspaces) {
                if (hash.equals(ws.hash())) {
                    return ws.path();
                }
            }
        } catch (Exception e) {
            log.warn("[web] 读取工作区列表失败: {}", e.getMessage());
        }

        // 2. 检查默认工作区
        String defaultPath = getWorkspace();
        if (defaultPath != null && hash.equals(WorkspaceManager.computeHash(defaultPath))) {
            return defaultPath;
        }

        // 3. 遍历缓存的 Agent key（兼容旧版）
        for (String key : sessionCache.keySet()) {
            String workspacePath = key.split("::", 2)[0];
            if (hash.equals(WorkspaceManager.computeHash(workspacePath))) {
                return workspacePath;
            }
        }

        // 4. 兼容：hash 可能就是路径本身（旧前端直接传路径）
        if (hash.contains("/") || hash.contains("\\")) {
            return hash;
        }

        // 5. 无法解析
        return null;
    }

    /**
     * 校验并解析工作区 hash，解析失败时抛异常（不再静默 fallback）。
     *
     * @param workspaceHash 工作区 hash
     * @return 工作区绝对路径
     * @throws ServiceException hash 无效或找不到对应工作区
     */
    public String resolveWorkspaceHashOrThrow(String workspaceHash) {
        if (workspaceHash == null || workspaceHash.isEmpty()) {
            throw new ServiceException("workspaceHash 不能为空");
        }
        String path = resolveWorkspacePath(workspaceHash);
        if (path == null) {
            throw new ServiceException("工作区不存在: " + workspaceHash);
        }
        return path;
    }

    /**
     * 获取默认工作区路径。
     *
     * @return 工作区路径
     */
    public String getWorkspace() {
        if (sharedConfig != null && sharedConfig.workspaceDir() != null) {
            return sharedConfig.workspaceDir().toAbsolutePath().toString();
        }
        return null;
    }
    /**
     * 切换工作区（兼容旧接口）。
     * 更新当前工作区并持久化到 config.json，下次启动默认打开此工作区。
     *
     * @param path 新的工作区路径
     * @return 切换成功返回 true
     */
    public boolean switchWorkspace(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        // 持久化到 config.json，下次启动默认加载此工作区
        String normalized = Paths.get(path).toAbsolutePath().normalize().toString();
        try {
            Agent4jConfig config = Agent4jConfig.load();
            config.updateAndSave(Collections.singletonMap("workspaceDir", normalized));
            log.info("[web] 工作区已持久化到 config.json: {}", normalized);
        } catch (Exception e) {
            log.warn("[web] 持久化工作区到 config.json 失败: {}", e.getMessage());
        }
        // 确保工作区目录结构存在（~/.agent4j/workspace/{hash}/）
        // 否则新工作区不会出现在 listWorkspaces 中，resolveWorkspacePath 也无法反查
        try {
            WorkspaceManager.getOrCreate(normalized);
            log.info("[web] 工作区目录结构已创建: {}", normalized);
        } catch (Exception e) {
            log.warn("[web] 创建工作区目录结构失败: {}", e.getMessage());
        }
        // 清除默认会话的缓存，让下次访问时使用新路径
        evictAgent(null, null);
        log.info("[web] 工作区已切换: {}", normalized);
        return true;
    }

    /**
     * 列出工作区（兼容旧接口）。
     *
     * @return 工作区列表
     */
    public List<WorkspaceInfoDTO> listWorkspaces() {
        List<WorkspaceInfoDTO> result = new ArrayList<>();

        try {
            WorkspaceManager workspaceManager = new WorkspaceManager();
            String currentPath = getWorkspace();
            if (currentPath != null) {
                workspaceManager.switchWorkspace(currentPath);
            }
            List<WorkspaceManager.WorkspaceInfo> workspaces = workspaceManager.listWorkspaces();

            for (WorkspaceManager.WorkspaceInfo info : workspaces) {
                result.add(new WorkspaceInfoDTO(
                        info.hash(), info.name(), info.path(),
                        info.createdAt(), info.lastAccessedAt(),
                        info.sessionCount()
                ));
            }
        } catch (IOException e) {
            log.warn("[web] 获取工作区列表失败: {}", e.getMessage());
            // 回退到旧逻辑：从缓存中收集
            Set<String> workspacePaths = new HashSet<>();
            for (String key : sessionCache.keySet()) {
                String[] parts = key.split("::", 2);
                if (parts.length > 0) {
                    workspacePaths.add(parts[0]);
                }
            }

            if (sharedConfig != null && sharedConfig.workspaceDir() != null) {
                workspacePaths.add(sharedConfig.workspaceDir().toAbsolutePath().toString());
            }

            for (String path : workspacePaths) {
                result.add(new WorkspaceInfoDTO(
                        WorkspaceManager.computeHash(path), null, path,
                        0, 0, 0
                ));
            }
        }

        return result;
    }

    /**
     * 删除工作区（兼容旧接口）。
     * 在新架构中，清除该工作区的所有 Agent 缓存。
     *
     * @param hash 工作区 hash
     * @return 删除成功返回 true
     */
    public boolean deleteWorkspace(String hash) {
        if (hash == null || hash.isEmpty()) {
            return false;
        }

        // 1. 查找并清除该工作区的所有 Agent 缓存
        List<String> keysToRemove = new ArrayList<>();
        for (String key : sessionCache.keySet()) {
            String workspacePath = key.split("::", 2)[0];
            String keyHash = WorkspaceManager.computeHash(workspacePath);
            if (hash.equals(keyHash)) {
                keysToRemove.add(key);
            }
        }

        for (String key : keysToRemove) {
            String[] parts = key.split("::", 2);
            String workspacePath = parts[0];
            String sessionName = parts.length > 1 ? parts[1] : "default";
            evictAgent(workspacePath, sessionName);
        }

        // 2. 删除工作区数据目录（~/.agent4j/workspace/{hash}/）
        try {
            WorkspaceManager wm = new WorkspaceManager();
            boolean deleted = wm.deleteWorkspace(hash);
            if (deleted) {
                log.info("[web] 已删除工作区数据目录: {}", hash);
            }
        } catch (Exception e) {
            log.warn("[web] 删除工作区数据目录失败: {}", e.getMessage());
        }

        log.info("[web] 已删除工作区: {}，清除了 {} 个 Agent", hash, keysToRemove.size());
        return !keysToRemove.isEmpty();
    }

    // ==================== 命令与 Skill 查询 ====================

    /**
     * 获取所有命令的元数据列表（供前端命令选择弹窗使用）。
     *
     * @return 命令元数据列表
     */
    public String executeScheduledTask(String workspacePath, String sessionName, String message) {
        String sessionKey = generateSessionKey(workspacePath, sessionName);
        ReentrantLock lock = getSessionLock(sessionKey);
        lock.lock();

        String effectiveSessionName = sessionName != null ? sessionName : "default";
        CURRENT_SESSION_NAME.set(effectiveSessionName);

        try {
            Agent4jAgent agent = getOrCreateAgent(sessionKey);

            agent.setOutput(AgentOutput.NOOP);
            String sessionId = sessionName != null ? sessionName : "default";
            agent.setSessionId(sessionId);
            return agent.chat(UserMessage.of(message));
        } catch (Exception e) {
            log.warn("[schedule] 定时任务执行异常: {}", e.getMessage());
            return "错误：" + e.getMessage();
        } finally {
            CURRENT_SESSION_NAME.remove();
            Agent4jAgent agent = sessionCache.get(sessionKey);
            if (agent != null) {
                agent.setOutput(AgentOutput.NOOP);
                agent.flushSession();
                agent.saveUsage();
            }
            lock.unlock();
        }
    }

    // ==================== 命令与 Skill 查询 ====================

    /**
     * 获取所有命令的元数据列表（供前端命令选择弹窗使用）。
     *
     * @return 命令元数据列表
     */
    public List<CommandMetaDTO> getCommandMetaList() {
        if (commandRegistry == null) {
            return Collections.emptyList();
        }
        return commandRegistry.getCommandMetaList().stream()
                .map(m -> new CommandMetaDTO(
                        (String) m.get("cmd"),
                        (String) m.get("desc"),
                        (String) m.get("argHint")
                ))
                .collect(Collectors.toList());
    }

}
