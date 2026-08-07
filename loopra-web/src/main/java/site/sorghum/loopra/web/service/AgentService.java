package site.sorghum.loopra.web.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Init;
import org.noear.solon.annotation.Inject;
import site.sorghum.loopra.bin.agent.context.ContextTokenEstimate;
import site.sorghum.loopra.bin.agent.context.ConversationContext;
import site.sorghum.loopra.bin.agent.core.LoopraAgent;
import site.sorghum.loopra.bin.agent.model.ChatMessage;
import site.sorghum.loopra.bin.agent.model.UserMessage;
import site.sorghum.loopra.bin.command.ChatCommandRegistry;
import site.sorghum.loopra.bin.config.ConfigService;
import site.sorghum.loopra.bin.config.LoopraConfig;
import site.sorghum.loopra.bin.model.HttpModelClient;
import site.sorghum.loopra.bin.model.ModelPriceProvider;
import site.sorghum.loopra.bin.session.JsonlSessionStore;
import site.sorghum.loopra.bin.session.SessionStore;
import site.sorghum.loopra.bin.tool.ToolRegistry;
import site.sorghum.loopra.bin.tool.ToolSystemInitializer;
import site.sorghum.loopra.bin.workspace.WorkspaceManager;
import site.sorghum.loopra.tool.AgentOutput;
import site.sorghum.loopra.bin.session.SessionFileChangeTracker;
import site.sorghum.loopra.web.common.ServiceException;
import site.sorghum.loopra.web.common.UsageCostCalculator;
import site.sorghum.loopra.web.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Agent 会话级服务 —— 管理 LoopraAgent 的生命周期和并发访问。
 * <p>
 * 实现"一个会话一个 Agent"架构：
 * - 每个会话（workspacePath::sessionName）拥有独立的 LoopraAgent 实例
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
        private final ConcurrentHashMap<String, LoopraAgent> agents = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, String> currentNames = new ConcurrentHashMap<>();
        /** LRU 访问顺序跟踪（synchronized 保证 put/get 与 evict 的原子性） */
        private final List<String> accessOrder = Collections.synchronizedList(new LinkedList<>());

        LoopraAgent get(String key) {
            // 先查 agents（ConcurrentHashMap 无锁读），再更新 LRU 顺序
            LoopraAgent agent = agents.get(key);
            if (agent != null) {
                synchronized (accessOrder) {
                    accessOrder.remove(key);
                    accessOrder.add(0, key);
                }
            }
            return agent;
        }

        void put(String key, LoopraAgent agent) {
            agents.put(key, agent);
            synchronized (accessOrder) {
                accessOrder.remove(key);
                accessOrder.add(0, key);
            }
        }

        void evictIfNeeded() {
            while (agents.size() >= MAX_CACHE_SIZE) {
                String oldest;
                synchronized (accessOrder) {
                    if (accessOrder.isEmpty()) break;
                    oldest = accessOrder.remove(accessOrder.size() - 1);
                }
                if (oldest != null) {
                    LoopraAgent removed = agents.remove(oldest);
                    if (removed != null) {
                        try {
                            removed.dispose();
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
            accessOrder.clear();
            locks.clear();
            currentNames.clear();
        }

        LoopraAgent remove(String key) {
            accessOrder.remove(key);
            locks.remove(key);
            return agents.remove(key);
        }

        Set<String> keySet() {
            return agents.keySet();
        }

        Set<Map.Entry<String, LoopraAgent>> entrySet() {
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

        Collection<LoopraAgent> values() {
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

    /** 会话当前使用的模型和渠道，用于避免不同会话间互相覆盖。 */
    private final ConcurrentHashMap<String, ModelTarget> sessionModelTargets = new ConcurrentHashMap<>();

    /**
     * 默认工作区的共享 ToolRegistry（供 /api/tools 等接口与就绪检查使用）
     */
    @Getter
    private volatile ToolRegistry sharedToolRegistry;

    /**
     * 按工作区缓存的工具系统初始化结果（绝对路径 -> Result）。
     * <p>同一工作区的所有会话 Agent 复用其 ToolRegistry 与 PromptPrefix，
     * 避免每个 Agent 重复扫描工具/构建提示词；配置重建（{@link #reinitialize()}）时清空。</p>
     */
    private final ConcurrentHashMap<String, ToolSystemInitializer.Result> sharedToolSystems = new ConcurrentHashMap<>();

    /**
     * 加载默认系统提示词。
     * <p>
     * 基底固定为硬编码 {@code DEFAULT_SYSTEM_PROMPT}（含核心身份规则与记忆引导）；
     * 若用户级 {@code ~/.loopra/loopra.md} 存在且非空，则拼接在其后，保证两者都有。
     * 顺序：稳定的硬编码部分在前（利于 KV 前缀缓存），用户自定义部分在后。
     * </p>
     */
    private static String loadDefaultSystemPrompt() {
        String base = LoopraAgent.Builder.DEFAULT_SYSTEM_PROMPT;
        Path homePrompt = Paths.get(System.getProperty("user.home"), ".loopra", "loopra.md");
        if (!Files.exists(homePrompt)) {
            return base;
        }
        try {
            String content = Files.readString(homePrompt);
            if (content.trim().isEmpty()) {
                return base;
            }
            log.info("[prompt] 拼接用户级 ~/.loopra/loopra.md（{} 字符）", content.length());
            return base + "\n\n" + content.trim();
        } catch (IOException e) {
            log.error("[prompt] 读取 ~/.loopra/loopra.md 失败: {}", e.getMessage());
            return base;
        }
    }

    // ==================== 配置 getter（统一从 ConfigService 读取） ====================

    public String getSharedApiUrl() {
        LoopraConfig cfg = ConfigService.getConfig();
        return cfg != null ? cfg.apiUrl() : null;
    }

    public String getSharedApiKey() {
        LoopraConfig cfg = ConfigService.getConfig();
        return cfg != null ? cfg.apiKey() : null;
    }

    public String getSharedModel() {
        LoopraConfig cfg = ConfigService.getConfig();
        return cfg != null ? cfg.model() : null;
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
            ConfigService.reload();
            if (!buildSharedComponents(ConfigService.getConfig())) {
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

        // 1. 先 dispose 所有缓存的 Agent（注销监听 + 刷入数据）
        for (LoopraAgent agent : sessionCache.values()) {
            try {
                agent.dispose();
            } catch (Exception e) {
                log.warn("[config] dispose Agent 时异常: {}", e.getMessage());
            }
        }

        // 2. 清空所有缓存
        sessionCache.clear();
        sessionModelTargets.clear();
        sharedToolSystems.clear();

        // 3. 重新加载配置并构建共享组件
        try {
            ConfigService.reload();
            if (!buildSharedComponents(ConfigService.getConfig())) {
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
     * @param config 已加载的 Loopra 配置
     * @return true 表示初始化成功，false 表示缺少必要的 API Key
     */
    private boolean buildSharedComponents(LoopraConfig config) {
        String apiUrl = config.apiUrl();
        String apiKey = config.apiKey();

        if (apiKey == null || apiKey.isEmpty()) {
            return false;
        }

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

        // 预构建默认工作区的共享工具系统（其余工作区在创建 Agent 时按需构建）
        ToolSystemInitializer.Result initResult = buildSharedToolSystem(config.workspaceDir(), config);
        sharedToolSystems.put(workspaceKey(config.workspaceDir()), initResult);
        this.sharedToolRegistry = initResult.toolRegistry;

        return true;
    }

    /**
     * 构建指定工作区的共享工具系统（ToolRegistry + PromptPrefix + 系统提示词）。
     * <p>纯构建，不操作缓存 Map —— 供 {@code computeIfAbsent} 的映射函数安全调用
     * （ConcurrentHashMap 禁止在 computeIfAbsent 内递归更新同一 Map）。</p>
     */
    private ToolSystemInitializer.Result buildSharedToolSystem(Path workspace, LoopraConfig config) {
        ToolSystemInitializer.Result result = ToolSystemInitializer.initialize(
                workspace, config.apiUrl(), config.apiKey(),
                config.disabledTools(), config.blockedPaths(),
                loadDefaultSystemPrompt());
        log.info("[web] 已构建共享工具系统 — 工作区: {}", workspace);
        return result;
    }

    /**
     * 获取（或首次构建）指定工作区的共享工具系统。
     */
    private ToolSystemInitializer.Result getOrCreateSharedToolSystem(Path workspace) {
        return sharedToolSystems.computeIfAbsent(workspaceKey(workspace),
                k -> buildSharedToolSystem(workspace, ConfigService.getConfig()));
    }

    private static String workspaceKey(Path workspace) {
        // 未配置工作区时返回空键（initialize 本身容忍 null 工作区）
        return workspace == null ? "" : workspace.toAbsolutePath().normalize().toString();
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
            LoopraConfig cfg = ConfigService.getConfig();
            if (cfg != null && cfg.workspaceDir() != null) {
                workspacePath = cfg.workspaceDir().toAbsolutePath().toString();
            } else {
                workspacePath = Paths.get(System.getProperty("user.home"), ".loopra").toString();
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
    private LoopraAgent getOrCreateAgent(String sessionKey) {
        LoopraAgent existing = sessionCache.get(sessionKey);
        if (existing != null) return existing;
        return getOrCreateAgent(sessionKey, defaultModelTarget());
    }

    /**
     * 获取会话 Agent，并确保它连接到本次请求指定的模型渠道。
     * 同一会话的调用方已持有会话锁，因此渠道切换不会与流式响应并发发生。
     */
    private LoopraAgent getOrCreateAgent(String sessionKey, ModelTarget target) {
        // 更新访问顺序并检查缓存
        LoopraAgent agent = sessionCache.get(sessionKey);
        if (agent != null) {
            ModelTarget currentTarget = sessionModelTargets.get(sessionKey);
            if (target.equals(currentTarget)) return agent;
            if (currentTarget != null && currentTarget.channelId().equals(target.channelId())) {
                agent.setModel(target.model());
                sessionModelTargets.put(sessionKey, target);
                return agent;
            }

            // 渠道切换需要替换 HttpModelClient；先落盘，再从同一会话历史恢复新 Agent。
            agent.flushSession();
            agent.dispose();
            agent = createAgent(sessionKey, target);
            sessionCache.put(sessionKey, agent);
            sessionModelTargets.put(sessionKey, target);
            log.info("[web] 会话模型渠道已切换: {}, {}/{}", sessionKey, target.channelId(), target.model());
            return agent;
        }

        // 创建新 Agent
        synchronized (this) {
            // 双重检查
            agent = sessionCache.get(sessionKey);
            if (agent != null) {
                return getOrCreateAgent(sessionKey, target);
            }

            // LRU 淘汰
            sessionCache.evictIfNeeded();
            agent = createAgent(sessionKey, target);

            // 缓存 Agent
            sessionCache.put(sessionKey, agent);
            sessionModelTargets.put(sessionKey, target);

            log.info("[web] 创建新 Agent: {}", sessionKey);
            return agent;
        }
    }

    private LoopraAgent createAgent(String sessionKey, ModelTarget target) {
        return createAgent(sessionKey, target, null);
    }

    private LoopraAgent createAgent(String sessionKey, ModelTarget target, String systemPrompt) {
        String[] parts = sessionKey.split("::", 2);
        String workspacePath = parts[0];
        String sessionName = parts.length > 1 ? parts[1] : "default";
        LoopraConfig cfg = ConfigService.getConfig();
        LoopraConfig.ModelChannel channel = cfg.modelChannel(target.channelId());
        if (channel == null) throw new ServiceException("模型渠道不存在: " + target.channelId());

        String apiUrl = channel.apiUrl();
        String apiKey = channel.apiKey();
        String reasoningEffort = cfg.reasoningEffort();
        String hitl = cfg.hitl();
        LoopraAgent.Builder builder = LoopraAgent.builder()
                .config(cfg)
                .apiUrl(apiUrl)
                .apiKey(apiKey)
                .model(target.model())
                .workspace(Paths.get(workspacePath))
                .commandRegistry(commandRegistry)
                .hitl(hitl)
                .loopraConfig(cfg)
                // 复用该工作区的共享工具系统，跳过 Agent 内部的重复初始化
                .toolSystem(getOrCreateSharedToolSystem(Paths.get(workspacePath)))
                .modelClient(new HttpModelClient(apiUrl, apiKey, target.model(), reasoningEffort,
                        target.channelId(), channel.apiProtocol()));
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            builder.systemPrompt(systemPrompt);
        }
        LoopraAgent agent = builder.buildLightweight();
        agent.bindSession(sessionName);
        agent.setListener(new WebUsageListener(agent));
        agent.setOutput(AgentOutput.NOOP);
        return agent;
    }

    /**
     * 获取或创建会话 Agent（创建时注入自定义系统提示词，仅首次创建生效）。
     */
    private LoopraAgent getOrCreateAgentWithPrompt(String sessionKey, ModelTarget target, String systemPrompt) {
        LoopraAgent agent = sessionCache.get(sessionKey);
        if (agent != null) return agent;
        synchronized (this) {
            agent = sessionCache.get(sessionKey);
            if (agent != null) return agent;
            sessionCache.evictIfNeeded();
            agent = createAgent(sessionKey, target, systemPrompt);
            sessionCache.put(sessionKey, agent);
            sessionModelTargets.put(sessionKey, target);
            log.info("[web] 创建新 Agent（自定义提示词）: {}", sessionKey);
            return agent;
        }
    }

    private ModelTarget defaultModelTarget() {
        LoopraConfig cfg = ConfigService.getConfig();
        LoopraConfig.ModelChannel channel = cfg.activeModelChannel();
        return new ModelTarget(cfg.model(), channel != null ? channel.id() : "default");
    }

    private ModelTarget resolveModelTarget(String requestedModel, String requestedChannelId) {
        LoopraConfig cfg = ConfigService.getConfig();
        ModelTarget fallback = defaultModelTarget();
        String channelId = requestedChannelId == null || requestedChannelId.isBlank()
                ? fallback.channelId() : requestedChannelId.trim();
        if (cfg.modelChannel(channelId) == null) {
            throw new ServiceException("模型渠道不存在: " + channelId);
        }
        String model = requestedModel == null || requestedModel.isBlank() ? fallback.model() : requestedModel.trim();
        return new ModelTarget(model, channelId);
    }

    private record ModelTarget(String model, String channelId) {}

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
        return ConfigService.getConfig() != null
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
        String hitlMode = "free";
        String sessionName = null;
        long promptTokens = 0;
        long completionTokens = 0;

        // 追加默认会话的详细信息
        String defaultKey = generateSessionKey(null, null);
        LoopraAgent agent = sessionCache.get(defaultKey);
        if (agent != null) {
            historySize = agent.historySize();
            hitlMode = agent.getHitlMode();
            SessionStore store = agent.getSessionStore();
            if (store != null) {
                sessionName = store.currentName();
            }
            long[] usage = agent.getSessionUsage();
            promptTokens = usage[0];
            completionTokens = usage[1];
        }
        return new AgentStatusDTO(ready, model, workspace, cacheSize,
                historySize, hitlMode, sessionName,
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
        LoopraAgent agent = getOrCreateAgent(sessionKey);
        SessionStore store = agent.getSessionStore();
        if (store != null) {
            try {
                store.flush();
                return store.load();
            } catch (IOException e) {
                log.warn("[web] 加载会话历史失败: {}", e.getMessage());
            }
        }
        return new ArrayList<>();
    }

    /**
     * 向指定会话注入一条用户消息（不触发 AI 回复）。
     * <p>
     * 用于需求池评论等场景：消息进入会话历史并持久化，
     * 对 Agent 上下文可见；webHidden 为 true 时 Web 历史不展示（但仍可通过专属接口读取）。
     * </p>
     *
     * @param workspacePath 工作区路径
     * @param sessionName   目标会话名称
     * @param text          消息文本
     * @param webHidden     是否对 Web 历史隐藏
     */
    public void appendUserMessage(String workspacePath, String sessionName, String text, boolean webHidden) {
        if (sessionName == null || sessionName.isBlank() || text == null || text.isBlank()) {
            return;
        }
        String sessionKey = generateSessionKey(workspacePath, sessionName);
        LoopraAgent agent = getOrCreateAgent(sessionKey);
        SessionStore store = agent.getSessionStore();
        if (store == null) {
            return;
        }
        ChatMessage message = ChatMessage.ofUser(text);
        message.setWebHidden(webHidden);
        try {
            store.append(message);
            store.flush();
        } catch (IOException e) {
            throw new ServiceException("写入会话消息失败: " + e.getMessage());
        }
    }

    /** 返回会话计划模式及待审查计划。 */
    public Map<String, Object> getPlanState(String workspacePath, String sessionName) {
        String effectiveSessionName = sessionName;
        if (effectiveSessionName == null || effectiveSessionName.isEmpty()) {
            effectiveSessionName = getCurrentSessionName(workspacePath);
        }
        boolean enabled = false;
        String pendingPlan = null;
        if (effectiveSessionName != null) {
            String sessionKey = generateSessionKey(workspacePath, effectiveSessionName);
            LoopraAgent agent = sessionCache.get(sessionKey);
            if (agent != null) {
                enabled = agent.isPlanMode();
                pendingPlan = agent.getPendingPlan();
            } else {
                try {
                    String resolvedPath = workspacePath != null ? workspacePath : getWorkspace();
                    if (resolvedPath != null) {
                        Path sessionsDir = new WorkspaceManager().getSessionsDir(resolvedPath);
                        if (sessionsDir != null && Files.exists(sessionsDir)) {
                            SessionStore store = new JsonlSessionStore(sessionsDir);
                            try {
                                enabled = store.isPlanMode(effectiveSessionName);
                                pendingPlan = store.getPendingPlan(effectiveSessionName);
                            } finally {
                                store.shutdown();
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("[web] 查询计划模式失败: {}", e.getMessage());
                }
            }
        }
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("mode", enabled ? "plan" : "execute");
        state.put("pendingPlan", pendingPlan);
        return state;
    }

    public boolean getPlanMode(String workspacePath, String sessionName) {
        return "plan".equals(getPlanState(workspacePath, sessionName).get("mode"));
    }

    /** Web UI 切换会话计划模式；关闭时同时丢弃待审查计划。 */
    public Map<String, Object> setPlanMode(String workspacePath, String sessionName, boolean enabled) {
        if (sessionName == null || sessionName.isBlank()) {
            throw new ServiceException("请先选择会话");
        }
        String sessionKey = generateSessionKey(workspacePath, sessionName);
        ReentrantLock sessionLock = getSessionLock(sessionKey);
        sessionLock.lock();
        try {
            LoopraAgent agent = getOrCreateAgent(sessionKey);
            if (!enabled) agent.clearPendingPlan();
            agent.setPlanMode(enabled);
            return getPlanState(workspacePath, sessionName);
        } finally {
            sessionLock.unlock();
        }
    }

    /**
     * 中断当前聊天 —— 中断所有活跃的 Agent。
     */
    public void abortCurrentChat() {
        // 中断所有缓存的 Agent
        for (LoopraAgent agent : sessionCache.values()) {
            if (agent != null) {
                agent.abort();
            }
        }
    }

    /** 中断指定工作区和会话的当前生成，不影响其他并行会话。 */
    public void abortChat(String workspacePath, String sessionName) {
        String sessionKey = generateSessionKey(workspacePath, sessionName);
        LoopraAgent agent = sessionCache.get(sessionKey);
        if (agent != null) agent.abort();
    }

    /**
     * 截断会话历史：删除包含指定撤回定位 ID 的用户消息及之后的所有消息，
     * 同时重写 JSONL 文件使持久化数据同步。
     *
     * @param workspacePath 工作区路径
     * @param sessionName   会话名称
     * @param rollbackId    要截断的消息撤回定位 ID
     * @return 截断后被删除的用户消息文本（用于回填输入框），null 表示未找到
     */
    public String truncateHistoryBySnapshotId(String workspacePath, String sessionName, String rollbackId, Long rollbackTimestamp) {
        if (sessionName == null || sessionName.isEmpty()) {
            sessionName = getCurrentSessionName(workspacePath);
        }
        if (sessionName == null || (rollbackId == null && rollbackTimestamp == null)) return null;
        String sessionKey = generateSessionKey(workspacePath, sessionName);
        ReentrantLock lock = getSessionLock(sessionKey);
        lock.lock();
        try {
            LoopraAgent agent = getOrCreateAgent(sessionKey);
            ConversationContext ctx = agent.getCtx();
            if (ctx == null) return null;
            List<ChatMessage> history = ctx.getHistory();
            int targetIdx = -1;
            String rollbackText = null;
            for (int i = 0; i < history.size(); i++) {
                ChatMessage msg = history.get(i);
                boolean matchesRollbackId = rollbackId != null
                        && (rollbackId.equals(msg.getRollbackId()) || rollbackId.equals(msg.getSnapshotId()));
                boolean matchesTimestamp = rollbackTimestamp != null && rollbackTimestamp.equals(msg.getTimestamp());
                if ("user".equals(msg.getRole()) && (matchesRollbackId || matchesTimestamp)) {
                    targetIdx = i;
                    rollbackText = msg.getContent();
                    break;
                }
            }
            if (targetIdx < 0) return null;
            // 任何历史截断都会使待审查计划失效，避免执行界面中已撤销的旧计划。
            agent.clearPendingPlan();
            ctx.truncate(targetIdx);
            return rollbackText;
        } finally {
            lock.unlock();
        }
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
        SessionFileChangeTracker.beginTurn(Paths.get(workspacePath), effectiveSessionName);
        CURRENT_SESSION_NAME.set(effectiveSessionName);
        HttpModelClient.CURRENT_LOG_SESSION.set(effectiveSessionName);

        try {
            LoopraAgent agent = getOrCreateAgent(sessionKey);

            agent.setOutput(AgentOutput.NOOP);
            // 设置会话ID到 AgentLoop
            String sessionId = sessionName != null ? sessionName : "default";
            agent.setSessionId(sessionId);
            return agent.chat(userMessage);
        } finally {
            // 清理 ThreadLocal
            CURRENT_SESSION_NAME.remove();
            HttpModelClient.CURRENT_LOG_SESSION.remove();
            // 刷入会话数据
            LoopraAgent agent = sessionCache.get(sessionKey);
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
    public void chatStream(UserMessage userMessage, String workspacePath, String sessionName, SseEmitter emitter,
                           String requestedModel, String requestedChannelId, String requestedReasoningEffort,
                           String action) {
        String sessionKey = generateSessionKey(workspacePath, sessionName);
        ReentrantLock lock = getSessionLock(sessionKey);
        lock.lock();

        // 设置当前会话名称到 ThreadLocal，供工具执行时获取
        String effectiveSessionName = sessionName != null ? sessionName : "default";
        SessionFileChangeTracker.beginTurn(Paths.get(workspacePath), effectiveSessionName);
        CURRENT_SESSION_NAME.set(effectiveSessionName);
        HttpModelClient.CURRENT_LOG_SESSION.set(effectiveSessionName);

        try {
            LoopraAgent agent = getOrCreateAgent(sessionKey, resolveModelTarget(requestedModel, requestedChannelId));
            if (requestedReasoningEffort != null && !requestedReasoningEffort.isBlank()) {
                agent.setReasoningEffort(requestedReasoningEffort.trim());
            }

            // 设置 AgentOutput：将所有事件桥接到 SSE
            agent.setOutput(new SseAgentOutput(emitter));

            UserMessage effectiveMessage = userMessage;
            boolean planExecutionPrepared = false;
            int historySizeBeforeExecution = agent.historySize();
            if (action != null && !action.isBlank()) {
                if (!"execute_plan".equals(action)) {
                    throw new ServiceException("不支持的聊天操作: " + action);
                }
                String executionMessage = agent.preparePendingPlanExecution();
                if (executionMessage == null) {
                    throw new ServiceException("当前会话没有待审查计划");
                }
                planExecutionPrepared = true;
                effectiveMessage = UserMessage.of(executionMessage);
                effectiveMessage.setWebHidden(true);
                if (userMessage != null) {
                    effectiveMessage.setRollbackId(userMessage.getRollbackId());
                    effectiveMessage.setSnapshotId(userMessage.getSnapshotId());
                }
            }

            String reply;
            try {
                reply = agent.chat(effectiveMessage);
            } catch (Exception executionError) {
                if (planExecutionPrepared) {
                    if (!hasPlanExecutionStarted(agent)) {
                        agent.getCtx().truncate(historySizeBeforeExecution);
                        agent.restorePendingPlanExecution();
                    } else {
                        agent.completePendingPlanExecution();
                    }
                }
                throw executionError;
            }
            if (planExecutionPrepared) {
                if (agent.isAbortRequested()) {
                    if (!hasPlanExecutionStarted(agent)) {
                        agent.getCtx().truncate(historySizeBeforeExecution);
                        agent.restorePendingPlanExecution();
                    } else {
                        agent.completePendingPlanExecution();
                    }
                    throw new ServiceException("计划执行已停止");
                }
                if (!hasPlanExecutionStarted(agent)) {
                    agent.getCtx().truncate(historySizeBeforeExecution);
                    agent.restorePendingPlanExecution();
                    throw new ServiceException("计划执行未能启动，请重试");
                }
                agent.completePendingPlanExecution();
            }

            // 发送最终完整回复（使用 complete 事件，与增量 content 事件区分）
            // HITL 待审批时跳过：interceptForHITL/interceptForSandboxHITL 已通过
            // output.onContentDelta() 发送过 HITL 消息，此处不应重复发送
            if (reply != null && !reply.isEmpty() && agent.noPendingHITL()) {
                agent.getCurrentTurnFileChanges().forEach(emitter::sendFileChanges);
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
            HttpModelClient.CURRENT_LOG_SESSION.remove();
            // 恢复 Agent 输出
            LoopraAgent agent = sessionCache.get(sessionKey);
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

    private boolean hasPlanExecutionStarted(LoopraAgent agent) {
        List<ChatMessage> history = agent.getCtx().getHistory();
        int hiddenInstruction = -1;
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).isWebHidden()) {
                hiddenInstruction = i;
                break;
            }
        }
        if (hiddenInstruction < 0) return false;
        for (int i = hiddenInstruction + 1; i < history.size(); i++) {
            ChatMessage message = history.get(i);
            if (message.isTool()) return true;
            if (!message.isAssistant()) continue;
            if (message.getContent() != null && !message.getContent().isBlank()) return true;
            if (message.getReasoningContent() != null && !message.getReasoningContent().isBlank()) return true;
            if (message.hasToolCalls()) return true;
        }
        return false;
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
            sessionName = "loopra-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(java.time.LocalDateTime.now());
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
        LoopraAgent agent = getOrCreateAgent(sessionKey);

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
            String title = store.getTitle(sessionInfo.name());
            if (title == null) {
                int replicaSuffix = sessionInfo.name().lastIndexOf("[复刻]");
                if (replicaSuffix > 0) {
                    String sourceTitle = store.getTitle(sessionInfo.name().substring(0, replicaSuffix));
                    if (sourceTitle != null && !sourceTitle.isBlank()) {
                        title = sourceTitle + sessionInfo.name().substring(replicaSuffix);
                        store.updateTitle(sessionInfo.name(), title);
                    }
                }
            }
            sessions.add(new SessionInfoDTO(
                    sessionInfo.name(),
                    title,
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
     * 从指定会话分支：复制原始历史的完整前缀到新会话，并切换过去。
     *
     * @param workspacePath 工作区路径
     * @param sourceSession  源会话名称
     * @param messageCount   原始历史的排他结束位置
     * @return 新会话名称
     */
    public String branchSession(String workspacePath, String sourceSession, int messageCount) throws Exception {
        if (!isReady()) throw new ServiceException("Agent 未初始化");
        String sessionKey = generateSessionKey(workspacePath, sourceSession);
        LoopraAgent agent = getOrCreateAgent(sessionKey);
        SessionStore store = agent.getSessionStore();
        if (store == null) throw new ServiceException("会话存储不可用");

        // 加载源会话消息
        store.flush();
        List<ChatMessage> sourceMessages = store.load(sourceSession);
        List<ChatMessage> branchMessages = copyBranchMessages(sourceMessages, messageCount);
        String sourceTitle = store.getTitle(sourceSession);

        // Use an independent store so the source agent remains bound to its session.
        WorkspaceManager workspaceManager = new WorkspaceManager();
        Path sessionsDir = workspaceManager.getSessionsDir(workspacePath);
        JsonlSessionStore branchStore = new JsonlSessionStore(sessionsDir);
        try {
            String newName = "loopra-" + System.currentTimeMillis();
            if (!branchStore.bindTo(newName)) throw new ServiceException("无法创建新会话");
            branchStore.rewrite(branchMessages);
            branchStore.updateTitle(newName, branchTitle(sourceTitle, sourceSession));
            branchStore.flush();

            // 切换到新会话
            switchSession(workspacePath, newName);
            return newName;
        } finally {
            branchStore.shutdown();
        }
    }

    static List<ChatMessage> copyBranchMessages(List<ChatMessage> sourceMessages, int messageCount) {
        if (sourceMessages == null || sourceMessages.isEmpty()) {
            throw new ServiceException("源会话没有消息");
        }
        if (messageCount < 1 || messageCount > sourceMessages.size()) {
            throw new ServiceException("messageCount 超出源会话消息范围");
        }
        return new ArrayList<>(sourceMessages.subList(0, messageCount));
    }

    static String branchTitle(String sourceTitle, String sourceSession) {
        String title = sourceTitle == null || sourceTitle.isBlank() ? sourceSession : sourceTitle;
        return title + "[复刻]";
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
        LoopraAgent agent = sessionCache.get(sessionKey);
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
        LoopraAgent agent = sessionCache.get(sessionKey);

        // 历史会话可能尚未进入 Agent 缓存。按会话名加载 JSONL 后，仍可离线重算上下文构成。
        if (agent == null && sessionName != null && !sessionName.isBlank()) {
            try {
                agent = getOrCreateAgent(sessionKey);
            } catch (RuntimeException e) {
                log.warn("[usage] 加载历史会话 '{}' 失败: {}", sessionName, e.getMessage());
            }
        }

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
            LoopraConfig config = ConfigService.getConfig();
            currentModel = config.model();
            Map<String, Map<String, Double>> prices = config.price();

            if (agent == null) {
                log.warn("[usage] Agent 为 null，无法获取模型用量");
                return new UsageDTO(0, 0, 0, 0, 0, maxContextTokens, 0, null, false, 0, 0, 0, 0, null, null);
            }
            Map<String, long[]> mu = agent.getModelUsage();

            if (mu != null && !mu.isEmpty()) {
                // 全局价格提供者（与 UsageCostCalculator.calc() 行为对齐：config 优先，provider 兜底）
                ModelPriceProvider priceProvider = ModelMetaPriceProvider.getInstance();

                // 按模型分别计算费用
                for (Map.Entry<String, long[]> entry : mu.entrySet()) {
                    String modelName = entry.getKey();
                    long[] usage = entry.getValue();

                    // 1) 优先从用户配置中取价
                    Map<String, Double> modelPrice = prices.get(modelName);

                    // 2) 配置中没有时回退到 ModelPriceProvider（与 DashboardService / UsageCostCalculator 保持一致）
                    if ((modelPrice == null || modelPrice.isEmpty()) && priceProvider != null) {
                        modelPrice = priceProvider.getModelPrice(modelName);
                        if (modelPrice == null || modelPrice.isEmpty()) {
                            log.debug("[usage] 模型 '{}' 既无 config.price() 配置，也无 ModelPriceProvider 元数据，跳过计价", modelName);
                        }
                    }

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

        ContextTokenEstimate contextEstimate = agent != null ? agent.getLastContextEstimate() : null;
        if (contextEstimate == null && agent != null) {
            contextEstimate = agent.estimateCurrentContext();
        }
        return new UsageDTO(
                promptTokens, completionTokens, cacheHit, cacheMiss,
                lastPromptTokens, maxContextTokens,
                promptTokens + completionTokens,
                currentModel, hasPrice,
                inputCost, cacheCost, outputCost, totalCost,
                currency, contextEstimate
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
        LoopraAgent removed = sessionCache.remove(sessionKey);
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
            if (sessionsDir == null || !Files.exists(sessionsDir)) return;
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
        if (sessionsDir == null || !Files.exists(sessionsDir)) return;
        SessionStore store = new JsonlSessionStore(sessionsDir);
        store.clearAll();
        log.info("[web] 已清空所有会话");
    }

    // ==================== 兼容旧接口 ====================

    /**
     * 清除所有 Agent 缓存。
     */
    public void evictAllAgents() {
        for (Map.Entry<String, LoopraAgent> entry : sessionCache.entrySet()) {
            LoopraAgent agent = entry.getValue();
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
        LoopraConfig cfg = ConfigService.getConfig();
        if (cfg != null && cfg.workspaceDir() != null) {
            return cfg.workspaceDir().toAbsolutePath().toString();
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
            ConfigService.updateConfig(Collections.singletonMap("workspaceDir", normalized));
            log.info("[web] 工作区已持久化到 config.json: {}", normalized);
        } catch (Exception e) {
            log.warn("[web] 持久化工作区到 config.json 失败: {}", e.getMessage());
        }
        // 确保工作区目录结构存在（~/.loopra/workspace/{hash}/）
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
            // 注意：此处不应调用 switchWorkspace，因为它有自动创建（initWorkspace）的副作用，
            // 会导致刚刚被删除的工作区在 list 时被重建。
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

            LoopraConfig cfg = ConfigService.getConfig();
            if (cfg != null && cfg.workspaceDir() != null) {
                workspacePaths.add(cfg.workspaceDir().toAbsolutePath().toString());
            }

            for (String path : workspacePaths) {
                result.add(new WorkspaceInfoDTO(
                        WorkspaceManager.computeHash(path), null, path,
                        0, 0, 0
                ));
            }
        }

        return applyWorkspaceOrder(result, ConfigService.getWorkspaceOrder());
    }

    /**
     * 按用户保存的顺序合并工作区列表：
     * order 中的 hash 按保存顺序排列在前，未保存过排序的工作区按原顺序追加到末尾，
     * order 中已不存在（被删除）的 hash 自动忽略。
     */
    public static List<WorkspaceInfoDTO> applyWorkspaceOrder(List<WorkspaceInfoDTO> workspaces, List<String> order) {
        if (workspaces == null || workspaces.isEmpty() || order == null || order.isEmpty()) {
            return workspaces;
        }
        Map<String, WorkspaceInfoDTO> byHash = new LinkedHashMap<>();
        for (WorkspaceInfoDTO workspace : workspaces) {
            byHash.put(workspace.hash(), workspace);
        }
        List<WorkspaceInfoDTO> result = new ArrayList<>(workspaces.size());
        Set<String> placed = new HashSet<>();
        for (String hash : order) {
            WorkspaceInfoDTO workspace = byHash.get(hash);
            if (workspace != null && placed.add(hash)) {
                result.add(workspace);
            }
        }
        for (WorkspaceInfoDTO workspace : workspaces) {
            if (placed.add(workspace.hash())) {
                result.add(workspace);
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

        // 2. 删除工作区数据目录（~/.loopra/workspace/{hash}/）
        boolean directoryDeleted = false;
        try {
            WorkspaceManager wm = new WorkspaceManager();
            directoryDeleted = wm.deleteWorkspace(hash);
            if (directoryDeleted) {
                log.info("[web] 已删除工作区数据目录: {}", hash);
            }
        } catch (Exception e) {
            log.warn("[web] 删除工作区数据目录失败: {}", e.getMessage());
        }

        // 3. 如果删除的是当前工作区，清除 config.json 中的 workspaceDir，
        //    防止后续其他代码路径触发 switchWorkspace → initWorkspace 重建已删除的工作区
        if (directoryDeleted) {
            try {
                String currentPath = getWorkspace();
                if (currentPath != null && hash.equals(WorkspaceManager.computeHash(currentPath))) {
                    ConfigService.removeConfigKey("workspaceDir");
                    log.info("[web] 已清除 config.json 中的当前工作区引用: {}", currentPath);
                }
            } catch (Exception e) {
                log.warn("[web] 清除当前工作区引用失败: {}", e.getMessage());
            }
        }

        log.info("[web] 已删除工作区: {}，清除了 {} 个 Agent", hash, keysToRemove.size());
        return directoryDeleted;
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
        HttpModelClient.CURRENT_LOG_SESSION.set(effectiveSessionName);

        try {
            LoopraAgent agent = getOrCreateAgent(sessionKey);

            agent.setOutput(AgentOutput.NOOP);
            String sessionId = sessionName != null ? sessionName : "default";
            agent.setSessionId(sessionId);
            return agent.chat(UserMessage.of(message));
        } catch (Exception e) {
            log.warn("[schedule] 定时任务执行异常: {}", e.getMessage());
            return "错误：" + e.getMessage();
        } finally {
            CURRENT_SESSION_NAME.remove();
            LoopraAgent agent = sessionCache.get(sessionKey);
            if (agent != null) {
                agent.setOutput(AgentOutput.NOOP);
                agent.flushSession();
                agent.saveUsage();
            }
            lock.unlock();
        }
    }

    /**
     * 向指定会话注入一条 assistant 消息（不触发 AI 回复）。
     * <p>用于需求执行中 AI 回复用户评论（reply_requirement_comment 工具）。</p>
     */
    public void appendAssistantMessage(String workspacePath, String sessionName, String content) {
        if (sessionName == null || sessionName.isBlank() || content == null || content.isBlank()) {
            return;
        }
        String sessionKey = generateSessionKey(workspacePath, sessionName);
        LoopraAgent agent = getOrCreateAgent(sessionKey);
        SessionStore store = agent.getSessionStore();
        if (store == null) {
            return;
        }
        ChatMessage message = new ChatMessage("assistant");
        message.setContent(content);
        message.setTimestamp(System.currentTimeMillis());
        try {
            store.append(message);
            store.flush();
        } catch (IOException e) {
            throw new ServiceException("写入会话消息失败: " + e.getMessage());
        }
    }

    /**
     * 执行需求：以指定系统提示词驱动需求专属会话（req_&lt;id&gt;）执行一次任务。
     * <p>
     * 与 {@link #executeScheduledTask} 同构：会话锁 + ThreadLocal 上下文 + 无头执行；
     * 差异在于首次创建 Agent 时注入需求 SystemPrompt，且异常不吞掉（由执行器兜底流转 failed）。
     * </p>
     *
     * @param workspacePath 工作区路径
     * @param sessionName   需求专属会话名
     * @param systemPrompt  需求 SystemPrompt（首次创建会话 Agent 时生效）
     * @param message       触发执行的消息
     * @return Agent 回复内容
     */
    public String executeRequirement(String workspacePath, String sessionName, String systemPrompt, String message) {
        String sessionKey = generateSessionKey(workspacePath, sessionName);
        ReentrantLock lock = getSessionLock(sessionKey);
        lock.lock();

        String effectiveSessionName = sessionName != null ? sessionName : "default";
        CURRENT_SESSION_NAME.set(effectiveSessionName);
        HttpModelClient.CURRENT_LOG_SESSION.set(effectiveSessionName);

        try {
            LoopraAgent agent = getOrCreateAgentWithPrompt(sessionKey, defaultModelTarget(), systemPrompt);
            agent.setOutput(AgentOutput.NOOP);
            agent.setSessionId(effectiveSessionName);
            return agent.chat(UserMessage.of(message));
        } finally {
            CURRENT_SESSION_NAME.remove();
            HttpModelClient.CURRENT_LOG_SESSION.remove();
            LoopraAgent agent = sessionCache.get(sessionKey);
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
