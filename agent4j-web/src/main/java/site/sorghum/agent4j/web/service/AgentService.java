package site.sorghum.agent4j.web.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Init;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.bin.agent.Agent4jAgent;
import site.sorghum.agent4j.bin.agent.AgentOutput;
import site.sorghum.agent4j.bin.agent.ChatMessage;
import site.sorghum.agent4j.bin.agent.PromptPrefix;
import site.sorghum.agent4j.bin.command.ChatCommandRegistry;
import site.sorghum.agent4j.bin.config.Agent4jConfig;
import site.sorghum.agent4j.bin.model.HttpModelClient;
import site.sorghum.agent4j.bin.model.ModelClient;
import site.sorghum.agent4j.bin.session.JsonlSessionStore;
import site.sorghum.agent4j.bin.session.SessionStore;
import site.sorghum.agent4j.bin.tool.ToolRegistry;
import site.sorghum.agent4j.bin.tool.ToolScanUtil;
import site.sorghum.agent4j.bin.tool.ToolSystemInitializer;
import site.sorghum.agent4j.bin.workspace.WorkspaceManager;
import site.sorghum.agent4j.web.model.*;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Agent 会话级服务 —— 管理 Agent4jAgent 的生命周期和并发访问。
 * <p>
 * 实现"一个会话一个 Agent"架构：
 * - 每个会话（workspacePath::sessionName）拥有独立的 Agent4jAgent 实例
 * - 共享 ModelClient、ToolRegistry 和 PromptPrefix 减少资源消耗
 * - 每个 Agent 有自己的 ReentrantLock，支持并发聊天
 * - 使用 LRU 缓存策略管理 Agent 实例数量
 * </p>
 *
 * <p>
 * <strong>关于命令操作：</strong>retry/rewind/compact/plan/hitl/agree/deny 等命令
 * 已由 {@link site.sorghum.agent4j.bin.command.ChatCommandRegistry} 在
 * {@link Agent4jAgent#chat(String)} 中统一处理。前端直接发送命令字符串
 * （如 {@code "/retry"}、{@code "/compact"}）到聊天接口即可，
 * 无需额外 REST API。</p>
 *
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
    private static final ThreadLocal<String> currentSessionName = new ThreadLocal<>();
    /**
     * 会话级 Agent 缓存：key = workspacePath::sessionName
     */
    private final ConcurrentHashMap<String, Agent4jAgent> agentCache = new ConcurrentHashMap<>();
    /**
     * 会话级锁：key = workspacePath::sessionName
     */
    private final ConcurrentHashMap<String, ReentrantLock> sessionLocks = new ConcurrentHashMap<>();
    /**
     * 每个工作区当前活跃的会话名：key = workspacePath
     */
    private final ConcurrentHashMap<String, String> currentSessionNames = new ConcurrentHashMap<>();
    /**
     * Agent 访问顺序（用于 LRU 淘汰）
     */
    private final java.util.concurrent.ConcurrentLinkedDeque<String> accessOrder = new java.util.concurrent.ConcurrentLinkedDeque<>();
    @Inject
    ChatCommandRegistry commandRegistry;
    /**
     * 共享的 ModelClient（所有会话复用）
     */
    private volatile ModelClient sharedModelClient;
    /**
     * 共享的 ToolRegistry（所有会话复用）
     */
    @Getter
    private volatile ToolRegistry sharedToolRegistry;
    /**
     * 按工作区缓存的 PromptPrefix：key = 工作区绝对路径
     */
    private final ConcurrentHashMap<String, PromptPrefix> workspacePrefixes = new ConcurrentHashMap<>();
    /**
     * 共享的 Agent4jConfig
     */
    private volatile Agent4jConfig sharedConfig;
    /**
     * 共享的 API 配置
     */
    private volatile String sharedApiUrl;
    private volatile String sharedApiKey;
    private volatile String sharedModel;
    /**
     * 当前 HITL 模式（true=手动需审批，false=自由直接执行）
     */
    private volatile boolean hitlMode = false;
    /**
     * 当前活跃的工作区路径（动态切换）
     */
    private volatile String currentActiveWorkspace;

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
                    System.err.println("[prompt] 从 ~/.agent4j/agent4j.md 加载默认系统提示词（" + content.length() + " 字符）");
                    return content.trim();
                }
            } catch (IOException e) {
                System.err.println("[prompt] 读取 ~/.agent4j/agent4j.md 失败: " + e.getMessage());
            }
        }
        return "你是一个智能体助手，名为Agent4J\n";
    }

    /**
     * 从环境变量读取配置，如果环境变量不存在则使用默认值。
     */
    private static String envOr(String envName, String defaultValue) {
        String value = System.getenv(envName);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
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
            String apiUrl = envOr("OPENAI_BASE_URL", config.chatApiUrl());
            String apiKey = envOr("OPENAI_API_KEY", config.apiKey());
            String model = envOr("MODEL", config.model());

            if (apiKey == null || apiKey.isEmpty()) {
                System.err.println("[web] 未配置 apiKey，Agent 未初始化");
                return;
            }

            // 保存共享配置
            this.sharedConfig = config;
            this.sharedApiUrl = apiUrl;
            this.sharedApiKey = apiKey;
            this.sharedModel = model;

            // 创建共享的 ModelClient
            this.sharedModelClient = new HttpModelClient(apiUrl, apiKey, model);

            // 创建共享的 ToolRegistry
            this.sharedToolRegistry = new ToolRegistry();

            // 设置禁用工具
            Set<String> disabledTools = config.disabledTools();
            if (disabledTools != null && !disabledTools.isEmpty()) {
                sharedToolRegistry.setDisabledTools(disabledTools);
                System.err.println("[config] 已禁用工具: " + String.join(", ", disabledTools));
            }

            // 屏蔽目录列表
            final List<String> blockedPaths = config.blockedPaths();
            if (blockedPaths != null && !blockedPaths.isEmpty()) {
                System.err.println("[config] 已屏蔽目录: " + String.join(", ", blockedPaths));
            }

            // 使用 ToolSystemInitializer 统一初始化（消除重复代码）
            ToolSystemInitializer.Result initResult = ToolSystemInitializer.initialize(
                    config.workspaceDir(), apiUrl, apiKey,
                    disabledTools, blockedPaths,
                    loadDefaultSystemPrompt());
            this.sharedToolRegistry = initResult.toolRegistry;
            // 保存按工作区缓存的 PromptPrefix
            String initWs = config.workspaceDir() != null
                    ? config.workspaceDir().toAbsolutePath().toString()
                    : Paths.get(System.getProperty("user.home"), ".agent4j").toString();
            workspacePrefixes.put(initWs, initResult.promptPrefix);

            System.out.println("[web] Agent 共享组件初始化完成 — 模型: " + model);
        } catch (Exception e) {
            System.err.println("[web] Agent 共享组件初始化失败: " + e.getMessage());
            log.error(
                    "Agent 共享组件初始化失败: ", e
            );
        }
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
        // 更新访问顺序
        accessOrder.remove(sessionKey);
        accessOrder.addFirst(sessionKey);

        // 检查缓存
        Agent4jAgent agent = agentCache.get(sessionKey);
        if (agent != null) {
            return agent;
        }

        // 创建新 Agent
        synchronized (this) {
            // 双重检查
            agent = agentCache.get(sessionKey);
            if (agent != null) {
                return agent;
            }

            // 解析会话标识
            String[] parts = sessionKey.split("::", 2);
            String workspacePath = parts[0];
            String sessionName = parts.length > 1 ? parts[1] : "default";

            // 检查缓存大小，淘汰最久未使用的
            while (agentCache.size() >= MAX_CACHE_SIZE) {
                String oldestKey = accessOrder.pollLast();
                if (oldestKey != null) {
                    Agent4jAgent removed = agentCache.remove(oldestKey);
                    if (removed != null) {
                        // 保存并关闭被淘汰的 Agent
                        try {
                            removed.flushSession();
                            removed.saveUsage();
                        } catch (Exception e) {
                            System.err.println("[web] 淘汰 Agent 失败: " + e.getMessage());
                        }
                        System.out.println("[web] LRU 淘汰 Agent: " + oldestKey);
                    }
                }
            }

            // 构建轻量级 Agent
            try {
                Agent4jAgent.Builder builder = Agent4jAgent.builder()
                        .config(sharedConfig)               // 先加载 config 默认值（含 disabledTools/blockedPaths/hitl）
                        .apiUrl(sharedApiUrl)               // 覆盖为 env 感知的值
                        .apiKey(sharedApiKey)
                        .model(sharedModel)
                        .workspace(Paths.get(workspacePath)) // 覆盖为当前工作区路径
                        .commandRegistry(commandRegistry)
                        .hitl(hitlMode)  // 使用热更新后的 HITL 模式
                        .sharedModelClient(sharedModelClient)
                        .sharedPrefix(getOrCreateWorkspacePrefix(workspacePath, sessionName))
                        .sharedSystemPrompt(loadDefaultSystemPrompt());

                agent = builder.buildLightweight();

                // 切换到指定会话（含 default，以恢复 usage / title）
                agent.switchSession(sessionName);

                // 注册 token 用量追踪
                agent.setListener(new WebUsageListener(agent));

                // 默认使用 NOOP 输出（API 调用时由 SseEmitter 接管）
                agent.setOutput(AgentOutput.NOOP);

                // 缓存 Agent
                agentCache.put(sessionKey, agent);

                System.out.println("[web] 创建新 Agent: " + sessionKey);
                return agent;
            } catch (Exception e) {
                System.err.println("[web] 创建 Agent 失败: " + e.getMessage());
                log.error(
                        "Agent 共享组件初始化失败: ", e
                );
                return null;
            }
        }
    }

    /**
     * 获取会话级锁。
     *
     * @param sessionKey 会话唯一标识
     * @return 锁
     */
    private ReentrantLock getSessionLock(String sessionKey) {
        return sessionLocks.computeIfAbsent(sessionKey, k -> new ReentrantLock());
    }

    /**
     * 共享组件是否已初始化
     */
    public boolean isReady() {
        return sharedModelClient != null && sharedToolRegistry != null && !workspacePrefixes.isEmpty();
    }

    /**
     * 获取或创建指定会话的 PromptPrefix。
     * <p>
     * 以 "workspacePath::sessionName" 为缓存 key，每个会话有独立的 PromptPrefix
     * （因会话可能在不同工作区，项目文档 agent4j.md/CLAUDE.md 不同）。
     * 同一工作区+同一会话名的请求复用缓存，避免重复初始化。
     * </p>
     *
     * @param workspacePath 工作区路径
     * @param sessionName   会话名称
     * @return PromptPrefix，缓存未命中时自动初始化
     */
    private PromptPrefix getOrCreateWorkspacePrefix(String workspacePath, String sessionName) {
        // 标准化工作区路径
        String ws = workspacePath != null ? workspacePath : getWorkspace();
        if (ws == null && sharedConfig != null && sharedConfig.workspaceDir() != null) {
            ws = sharedConfig.workspaceDir().toAbsolutePath().toString();
        }
        if (ws == null) {
            ws = Paths.get(System.getProperty("user.home"), ".agent4j").toString();
        }
        String session = sessionName != null ? sessionName : "default";
        String cacheKey = ws + "::" + session;

        // 缓存命中直接返回
        PromptPrefix existing = workspacePrefixes.get(cacheKey);
        if (existing != null) {
            return existing;
        }
        // 缓存未命中，为该工作区初始化
        synchronized (this) {
            existing = workspacePrefixes.get(cacheKey);
            if (existing != null) return existing;
            try {
                Path workspaceDir = Paths.get(ws);
                Set<String> disabledTools = sharedConfig != null ? sharedConfig.disabledTools() : Collections.emptySet();
                List<String> blockedPaths = sharedConfig != null ? sharedConfig.blockedPaths() : Collections.emptyList();
                ToolSystemInitializer.Result result = ToolSystemInitializer.initialize(
                        workspaceDir, sharedApiUrl, sharedApiKey,
                        disabledTools, blockedPaths,
                        loadDefaultSystemPrompt());
                workspacePrefixes.put(cacheKey, result.promptPrefix);
                log.info("[prompt] 已初始化会话提示词: {} ({} 字符)", cacheKey, result.promptPrefix.system.length());
                return result.promptPrefix;
            } catch (Exception e) {
                log.error("[prompt] 初始化会话提示词失败: {}", e.getMessage());
                return null;
            }
        }
    }

    // ==================== 聊天 ====================

    /**
     * 获取当前会话的系统提示词。
     * <p>
     * 直接返回 {@link PromptPrefix#system}，即 Agent 运行时实际使用的完整提示词
     *（含项目文档 + 基础提示词 + 工具定义 + Plan Mode 说明 + Skill 索引）。
     * </p>
     *
     * @param workspacePath 工作区路径（预留，目前所有会话共享同一份 PromptPrefix）
     * @param sessionName   会话名称（预留）
     * @return PromptDTO，包含提示词内容和字符数
     */
    public PromptDTO getSystemPrompt(String workspacePath, String sessionName) {
        // 获取该会话的 PromptPrefix（优先用缓存，未命中则自动初始化）
        PromptPrefix prefix = getOrCreateWorkspacePrefix(workspacePath, sessionName);
        if (prefix == null || prefix.system == null) {
            return new PromptDTO("", 0);
        }
        return new PromptDTO(prefix.system, prefix.system.length());
    }

    /**
     * 获取 Agent 整体状态（供前端状态面板使用）。
     *
     * @return 状态信息
     */
    public AgentStatusDTO getStatus() {
        boolean ready = isReady();
        String model = sharedModel;
        String workspace = getWorkspace();
        int cacheSize = agentCache.size();

        int historySize = 0;
        boolean planMode = false;
        boolean hitlMode = false;
        String sessionName = null;
        long promptTokens = 0;
        long completionTokens = 0;

        // 追加默认会话的详细信息
        String defaultKey = generateSessionKey(null, null);
        Agent4jAgent agent = agentCache.get(defaultKey);
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
        if (agent != null) {
            SessionStore store = agent.getSessionStore();
            if (store != null) {
                try {
                    return store.load();
                } catch (IOException e) {
                    System.err.println("[web] 加载会话历史失败: " + e.getMessage());
                }
            }
        }
        return new ArrayList<>();
    }

    /**
     * 中断当前聊天 —— 中断所有活跃的 Agent。
     */
    public void abortCurrentChat() {
        // 中断所有缓存的 Agent
        for (Agent4jAgent agent : agentCache.values()) {
            if (agent != null) {
                agent.abort();
            }
        }
    }

    // ==================== 会话管理 ====================

    /**
     * 同步聊天 —— 支持工作区和会话隔离。
     * <p>
     * 每个会话拥有独立的 Agent4jAgent 实例，无需切换和恢复状态。
     * </p>
     *
     * @param message       用户消息
     * @param workspacePath 工作区路径（可选）
     * @param sessionName   会话名称（可选）
     * @return 聊天回复
     */
    public String chat(String message, String workspacePath, String sessionName) throws IOException {
        String sessionKey = generateSessionKey(workspacePath, sessionName);
        ReentrantLock lock = getSessionLock(sessionKey);
        lock.lock();

        // 设置当前会话名称到 ThreadLocal，供工具执行时获取
        String effectiveSessionName = sessionName != null ? sessionName : "default";
        currentSessionName.set(effectiveSessionName);

        try {
            Agent4jAgent agent = getOrCreateAgent(sessionKey);
            if (agent == null) {
                return "错误：无法创建 Agent 实例";
            }

            agent.setOutput(AgentOutput.NOOP);
            // 设置会话ID到 AgentLoop
            String sessionId = sessionName != null ? sessionName : "default";
            agent.setSessionId(sessionId);
            return agent.chat(message);
        } finally {
            // 清理 ThreadLocal
            currentSessionName.remove();
            // 刷入会话数据
            Agent4jAgent agent = agentCache.get(sessionKey);
            if (agent != null) {
                agent.flushSession();
                agent.saveUsage();
            }
            lock.unlock();
        }
    }

    /**
     * 流式聊天 —— 支持工作区和会话隔离。
     * <p>
     * 每个会话拥有独立的 Agent4jAgent 实例，无需切换和恢复状态。
     * </p>
     *
     * @param message       用户消息
     * @param workspacePath 工作区路径（可选）
     * @param sessionName   会话名称（可选）
     * @param emitter       SSE 发射器
     */
    public void chatStream(String message, String workspacePath, String sessionName, SseEmitter emitter) {
        String sessionKey = generateSessionKey(workspacePath, sessionName);
        ReentrantLock lock = getSessionLock(sessionKey);
        lock.lock();

        // 设置当前会话名称到 ThreadLocal，供工具执行时获取
        String effectiveSessionName = sessionName != null ? sessionName : "default";
        currentSessionName.set(effectiveSessionName);

        try {
            Agent4jAgent agent = getOrCreateAgent(sessionKey);
            if (agent == null) {
                emitter.sendError("无法创建 Agent 实例");
                return;
            }

            // 设置 AgentOutput：将所有事件桥接到 SSE
            agent.setOutput(new SseAgentOutput(emitter, agent));

            // 设置会话ID到 AgentLoop
            String sessionId = sessionName != null ? sessionName : "default";
            agent.setSessionId(sessionId);
            String reply = agent.chat(message);

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
                System.err.println("[web] 发送错误信息失败（可能SSE连接已断开）: " + ex.getMessage());
            }
        } finally {
            // 清理 ThreadLocal
            currentSessionName.remove();
            // 恢复 Agent 输出
            Agent4jAgent agent = agentCache.get(sessionKey);
            if (agent != null) {
                agent.setOutput(AgentOutput.NOOP);
                agent.flushSession();
                agent.saveUsage();
            }

            try {
                emitter.complete();
            } catch (Exception ex) {
                // SSE连接可能已断开，忽略异常
                System.err.println("[web] 完成SSE流失败（可能SSE连接已断开）: " + ex.getMessage());
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
            sessionName = "agent4j-" + new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date());
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
        if (agent == null) {
            return Collections.emptyList();
        }

        SessionStore store = agent.getSessionStore();
        if (store == null) {
            return Collections.emptyList();
        }

        // 确定当前会话名（优先用追踪记录，其次用 store.currentName）
        String resolvedPath = workspacePath != null ? workspacePath : getWorkspace();
        if (resolvedPath == null) {
            return Collections.emptyList();
        }
        String activeSession = currentSessionNames.get(resolvedPath);
        if (activeSession == null) {
            activeSession = store.currentName();
        }
        List<SessionInfoDTO> sessions = new ArrayList<>();
        for (SessionStore.SessionInfo sessionInfo : store.list()) {
            sessions.add(new SessionInfoDTO(
                    sessionInfo.name(),
                    store.getTitle(sessionInfo.name()),
                    sessionInfo.messageCount(),
                    sessionInfo.name().equals(activeSession)
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

        // 获取或创建目标会话的 Agent
        String sessionKey = generateSessionKey(workspacePath, sessionName);
        Agent4jAgent agent = getOrCreateAgent(sessionKey);
        if (agent != null) {
            // 记录当前活跃会话
            String resolvedPath = workspacePath != null ? workspacePath : getWorkspace();
            currentSessionNames.put(resolvedPath, sessionName);
            return true;
        }
        return false;
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
        String tracked = currentSessionNames.get(resolvedPath);
        if (tracked != null) {
            return tracked;
        }
        // 回退到 store 查询
        String sessionKey = generateSessionKey(workspacePath, null);
        Agent4jAgent agent = agentCache.get(sessionKey);
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
        Agent4jAgent agent = agentCache.get(sessionKey);

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

            // 获取按模型分别累计的用量
            assert agent != null;
            Map<String, long[]> mu = agent.getModelUsage();

            if (mu != null && !mu.isEmpty()) {
                // 按模型分别计算费用
                for (Map.Entry<String, long[]> entry : mu.entrySet()) {
                    String modelName = entry.getKey();
                    long[] usage = entry.getValue();
                    long mPrompt = usage[0];
                    long mCompletion = usage[1];
                    long mCacheHit = usage[2];

                    Map<String, Double> modelPrice = prices.get(modelName);
                    if (modelPrice != null && !modelPrice.isEmpty()) {
                        hasPrice = true;
                        double inputRate = modelPrice.getOrDefault("input", 0.0);
                        double cacheRate = modelPrice.getOrDefault("cache", 0.0);
                        double outputRate = modelPrice.getOrDefault("output", 0.0);

                        long nonCacheInput = Math.max(0, mPrompt - mCacheHit);
                        inputCost += nonCacheInput / 1_000_000.0 * inputRate;
                        cacheCost += mCacheHit / 1_000_000.0 * cacheRate;
                        outputCost += mCompletion / 1_000_000.0 * outputRate;
                    }
                    // 无价格配置的模型不计入费用
                }
                totalCost = inputCost + cacheCost + outputCost;
                currency = hasPrice ? "CNY" : null;

                inputCost = Math.round(inputCost * 10000.0) / 10000.0;
                cacheCost = Math.round(cacheCost * 10000.0) / 10000.0;
                outputCost = Math.round(outputCost * 10000.0) / 10000.0;
                totalCost = Math.round(totalCost * 10000.0) / 10000.0;
            } else {
                // 无 per-model 数据（旧格式 .usage 文件），回退到当前模型计费
                Map<String, Double> modelPrice = prices.get(currentModel);
                hasPrice = modelPrice != null && !modelPrice.isEmpty();

                if (hasPrice) {
                    double inputRate = modelPrice.getOrDefault("input", 0.0);
                    double cacheRate = modelPrice.getOrDefault("cache", 0.0);
                    double outputRate = modelPrice.getOrDefault("output", 0.0);

                    long nonCacheInput = Math.max(0, promptTokens - cacheHit);
                    inputCost = nonCacheInput / 1_000_000.0 * inputRate;
                    cacheCost = cacheHit / 1_000_000.0 * cacheRate;
                    outputCost = completionTokens / 1_000_000.0 * outputRate;
                    totalCost = inputCost + cacheCost + outputCost;
                    currency = "CNY";

                    inputCost = Math.round(inputCost * 10000.0) / 10000.0;
                    cacheCost = Math.round(cacheCost * 10000.0) / 10000.0;
                    outputCost = Math.round(outputCost * 10000.0) / 10000.0;
                    totalCost = Math.round(totalCost * 10000.0) / 10000.0;
                }
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
        return agentCache.size();
    }

    /**
     * 清除指定会话的 Agent 缓存。
     *
     * @param workspacePath 工作区路径（可选）
     * @param sessionName   会话名称（可选）
     */
    public void evictAgent(String workspacePath, String sessionName) {
        String sessionKey = generateSessionKey(workspacePath, sessionName);
        Agent4jAgent removed = agentCache.remove(sessionKey);
        if (removed != null) {
            try {
                removed.flushSession();
                removed.saveUsage();
            } catch (Exception e) {
                System.err.println("[web] 清除 Agent 失败: " + e.getMessage());
            }
            accessOrder.remove(sessionKey);
            sessionLocks.remove(sessionKey);
            // 如果清除的是当前活跃会话，清理追踪记录
            String resolvedPath = workspacePath != null ? workspacePath : getWorkspace();
            currentSessionNames.remove(resolvedPath, sessionName);
            System.out.println("[web] 已清除 Agent: " + sessionKey);
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
                System.out.println("[web] 已删除会话文件: " + sessionName);
            }
        } catch (Exception e) {
            System.err.println("[web] 删除会话文件失败: " + e.getMessage());
        }
    }

    // ==================== 兼容旧接口 ====================

    /**
     * 清除所有 Agent 缓存。
     */
    public void evictAllAgents() {
        for (Map.Entry<String, Agent4jAgent> entry : agentCache.entrySet()) {
            Agent4jAgent agent = entry.getValue();
            if (agent != null) {
                try {
                    agent.flushSession();
                    agent.saveUsage();
                } catch (Exception e) {
                    System.err.println("[web] 清除 Agent 失败: " + e.getMessage());
                }
            }
        }
        agentCache.clear();
        accessOrder.clear();
        sessionLocks.clear();
        currentSessionNames.clear();
        System.out.println("[web] 已清除所有 Agent 缓存");
    }

    /**
     * 通过 workspaceHash 反查工作区路径。
     * <p>
     * 遍历缓存的 Agent key + 默认工作区，返回匹配的路径。
     * 未匹配时返回 hash 本身（兼容直接传路径的旧调用方式）。
     * </p>
     *
     * @param hash 工作区 hash（或路径本身）
     * @return 解析后的工作区路径
     */
    public String resolveWorkspacePath(String hash) {
        if (hash == null || hash.isEmpty()) {
            return null;
        }
        // 1. 检查默认工作区
        String defaultPath = getWorkspace();
        if (hash.equals(WorkspaceManager.computeHash(defaultPath))) {
            return defaultPath;
        }
        // 2. 遍历缓存的 Agent key
        for (String key : agentCache.keySet()) {
            String workspacePath = key.split("::", 2)[0];
            if (hash.equals(WorkspaceManager.computeHash(workspacePath))) {
                return workspacePath;
            }
        }
        // 3. 兼容：hash 可能就是路径本身（旧前端直接传路径）
        if (hash.contains("/") || hash.contains("\\")) {
            return hash;
        }
        // 4. 无法解析，返回 null 让调用方使用默认值
        return null;
    }

    /**
     * 获取默认工作区路径。
     *
     * @return 工作区路径
     */
    public String getWorkspace() {
        // 优先返回动态切换的工作区
        if (currentActiveWorkspace != null) {
            return currentActiveWorkspace;
        }
        if (sharedConfig != null && sharedConfig.workspaceDir() != null) {
            return sharedConfig.workspaceDir().toAbsolutePath().toString();
        }
        return null;
    }

    /**
     * 更新模型（更新共享 ModelClient）。
     *
     * @param model 新模型名称
     */
    public void updateModel(String model) {
        if (sharedModelClient != null) {
            sharedModelClient.setModel(model);
            this.sharedModel = model;
            System.out.println("[web] 模型已更新: " + model);
        }
    }

    /**
     * 热更新 HITL 模式 — 同步到所有已缓存的 Agent 实例。
     *
     * @param hitl true=手动(需审批)，false=自由(直接执行)
     */
    public void updateHitlMode(boolean hitl) {
        for (Agent4jAgent agent : agentCache.values()) {
            agent.setHitlMode(hitl);
        }
        // 更新共享配置引用，确保后续新建的 Agent 也使用新值
        this.hitlMode = hitl;
        System.out.println("[web] HITL 模式已更新: " + (hitl ? "手动(需审批)" : "自由(直接执行)"));
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
        // 更新当前活跃工作区路径
        this.currentActiveWorkspace = Paths.get(path).toAbsolutePath().normalize().toString();
        // 持久化到 config.json，下次启动默认加载此工作区
        try {
            Agent4jConfig config = Agent4jConfig.load();
            config.updateAndSave(Collections.singletonMap("workspaceDir", currentActiveWorkspace));
            log.info("[web] 工作区已持久化到 config.json: {}", currentActiveWorkspace);
        } catch (Exception e) {
            log.warn("[web] 持久化工作区到 config.json 失败: {}", e.getMessage());
        }
        // 清除默认会话的缓存，让下次访问时使用新路径
        evictAgent(null, null);
        log.info("[web] 工作区已切换: {}", currentActiveWorkspace);
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
                        info.sessionCount(), info.isActive()
                ));
            }
        } catch (IOException e) {
            System.err.println("[web] 获取工作区列表失败: " + e.getMessage());
            // 回退到旧逻辑：从缓存中收集
            Set<String> workspacePaths = new HashSet<>();
            for (String key : agentCache.keySet()) {
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
                        0, 0, 0, false
                ));
            }
        }

        return result;
    }

    /**
     * 通过 hash 切换到指定工作区。
     *
     * @param hash 工作区 hash
     * @return 切换成功返回 true
     */
    public boolean switchToWorkspaceByHash(String hash) {
        if (hash == null || hash.isEmpty()) {
            return false;
        }
        // 从已注册的工作区中查找匹配的路径
        try {
            WorkspaceManager wm = new WorkspaceManager();
            List<WorkspaceManager.WorkspaceInfo> list = wm.listWorkspaces();
            for (WorkspaceManager.WorkspaceInfo info : list) {
                if (hash.equals(info.hash())) {
                    return switchWorkspace(info.path());
                }
            }
        } catch (IOException e) {
            System.err.println("[web] 查询工作区失败: " + e.getMessage());
        }
        // 兼容：从缓存中查找
        for (String key : agentCache.keySet()) {
            String workspacePath = key.split("::", 2)[0];
            if (hash.equals(WorkspaceManager.computeHash(workspacePath))) {
                return switchWorkspace(workspacePath);
            }
        }
        return false;
    }

    /**
     * 获取当前会话信息（兼容旧接口）。
     *
     * @return 当前会话信息
     */
    public SessionCurrentDTO getCurrentSession() {
        return new SessionCurrentDTO(getWorkspace(), getCurrentSessionName(null));
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
        for (String key : agentCache.keySet()) {
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
                System.out.println("[web] 已删除工作区数据目录: " + hash);
            }
        } catch (Exception e) {
            System.err.println("[web] 删除工作区数据目录失败: " + e.getMessage());
        }

        System.out.println("[web] 已删除工作区: " + hash + "，清除了 " + keysToRemove.size() + " 个 Agent");
        return !keysToRemove.isEmpty();
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
