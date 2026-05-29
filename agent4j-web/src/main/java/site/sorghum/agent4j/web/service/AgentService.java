package site.sorghum.agent4j.web.service;

import lombok.Getter;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Get;
import org.noear.solon.annotation.Init;

import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.bin.agent.Agent4jAgent;
import site.sorghum.agent4j.bin.agent.AgentLoopListener;
import site.sorghum.agent4j.bin.agent.AgentOutput;
import site.sorghum.agent4j.bin.agent.PromptPrefix;
import site.sorghum.agent4j.bin.command.ChatCommandRegistry;
import site.sorghum.agent4j.bin.config.Agent4jConfig;
import site.sorghum.agent4j.bin.model.ModelClient;
import site.sorghum.agent4j.bin.model.HttpModelClient;
import site.sorghum.agent4j.bin.session.SessionService;
import site.sorghum.agent4j.bin.session.SessionStore;
import site.sorghum.agent4j.bin.tool.ToolDef;
import site.sorghum.agent4j.bin.tool.ToolDefHelper;
import site.sorghum.agent4j.bin.tool.ToolRegistry;
import site.sorghum.agent4j.bin.workspace.WorkspaceManager;
import site.sorghum.agent4j.bin.skill.SkillStoreV2;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolResult;
import site.sorghum.agent4j.tool.ToolParameter;

import org.noear.snack4.ONode;
import org.noear.solon.Solon;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.HashSet;
import java.util.LinkedHashMap;

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
@Component
public class AgentService {

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
     * 共享的 PromptPrefix（所有会话复用）
     */
    private volatile PromptPrefix sharedPrefix;

    /**
     * 共享的 SkillStoreV2（所有会话复用）
     */
    private volatile SkillStoreV2 sharedSkillStore;

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

    /**
     * 最大缓存 Agent 数量
     */
    private static final int MAX_CACHE_SIZE = 50;

    /**
     * 当前 SSE 输出（每次请求创建一个新的）
     */
    private volatile SseEmitter currentSseEmitter;

    /**
     * 当前线程正在处理的会话名称（用于工具执行时获取 sessionId）
     */
    private static final ThreadLocal<String> currentSessionName = new ThreadLocal<>();

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

            // 存储所有工具的 toToolSpec 结果，用于追加到 system prompt
            StringBuilder toolSpecsBuilder = new StringBuilder();
            toolSpecsBuilder.append("\n\n## 可用工具规范\n\n");

            // 通过 getBeansOfType 同步获取所有 AgentTool 子类 Bean
            List<AgentTool> agentTools = new ArrayList<>(Solon.context().getBeansOfType(AgentTool.class));
            // 排序保证前缀一致
            agentTools.sort(Comparator.comparing(it -> it.getClass().getName()));
            for (AgentTool tool : agentTools) {
                // 获取工具的 toToolSpec() 纯文本规范
                String toolSpec = tool.toToolSpec();
                // 注册到 ToolDef，同时传递 toolSpec
                sharedToolRegistry.register(new ToolDef(
                        tool.getName(),
                        tool.getDescription(),
                        ToolDefHelper.toParamDefs(tool.getParameters()),
                        args -> {
                            // 从 args 中获取 sessionId（由 ToolDispatcher 在执行前注入）
                            String sessionId = args != null ? (String) args.remove("__sessionId__") : null;
                            return ToolDefHelper.formatResult(tool.execute(
                                    new ToolContext(args, config.workspaceDir(), apiUrl, apiKey, sharedToolRegistry, blockedPaths, sessionId)));
                        },
                        tool.isReadOnly(),
                        tool.isStormExempt(),
                        toolSpec));
                // 收集工具规范文本
                if (toolSpec != null && !toolSpec.isEmpty()) {
                    toolSpecsBuilder.append(toolSpec).append("\n\n---\n\n");
                }
            }

            // 加载项目文档（agent4j.md / CLAUDE.md），追加到 system prompt
            String systemPrompt = loadDefaultSystemPrompt();
            String projectMd = loadProjectMd(config.workspaceDir());
            if (!projectMd.isEmpty()) {
                systemPrompt = projectMd + "\n\n---\n\n" + systemPrompt;
            }
            // 将工具规范追加到 system prompt 末尾
            systemPrompt = systemPrompt + "\n\n" + toolSpecsBuilder.toString().trim();
            
            // 初始化技能存储并加载 skill 索引
            this.sharedSkillStore = new SkillStoreV2(config.workspaceDir(), 
                    Paths.get(System.getProperty("user.home")), 
                    Collections.emptyList());
            String skillsIndex = sharedSkillStore.buildSkillsIndex();
            if (!skillsIndex.isEmpty()) {
                systemPrompt = systemPrompt + "\n\n" + skillsIndex;
                System.out.println("[web] 已加载 skill 索引，共 " + sharedSkillStore.list().size() + " 个 skill");
            }

            // 注册 SkillStoreV2 到容器，供 RunSkillTool 和 InstallSkillTool 使用
            Solon.context().wrapAndPut(SkillStoreV2.class, sharedSkillStore);
            
            // 构建缓存优先前缀：system prompt + 工具定义（注册后冻结，跨 turn 稳定）
            this.sharedPrefix = new PromptPrefix(systemPrompt, sharedToolRegistry.toOpenAiTools());

            System.out.println("[web] Agent 共享组件初始化完成 — 模型: " + model);
            System.out.println("[web] 工具数量: " + agentTools.size());
        } catch (Exception e) {
            System.err.println("[web] Agent 共享组件初始化失败: " + e.getMessage());
            e.printStackTrace();
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
            workspacePath = sharedConfig != null ? sharedConfig.workspaceDir().toAbsolutePath().toString() : ".";
        }
        // 使用默认会话名称（如果未指定）
        if (sessionName == null || sessionName.isEmpty()) {
            sessionName = "default";
        }
        return workspacePath + "::" + sessionName;
    }

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
                        .apiUrl(sharedApiUrl)
                        .apiKey(sharedApiKey)
                        .model(sharedModel)
                        .workspace(Paths.get(workspacePath))
                        .commandRegistry(commandRegistry)
                        .sharedComponents(sharedModelClient, sharedToolRegistry, sharedPrefix);

                agent = builder.buildLightweight();

                // 切换到指定会话（含 default，以恢复 usage / title）
                agent.switchSession(sessionName);

                // 注册 token 用量追踪
                Agent4jAgent finalAgent = agent;
                agent.setListener(new AgentLoopListener() {
                    @Override
                    public void onUsage(int promptTokens, int completionTokens, int totalTokens,
                                        int cacheHit, int cacheMiss) {
                        finalAgent.addUsage(promptTokens, completionTokens, cacheHit, cacheMiss);
                    }
                });

                // 默认使用 NOOP 输出（API 调用时由 SseEmitter 接管）
                agent.setOutput(AgentOutput.NOOP);

                // 缓存 Agent
                agentCache.put(sessionKey, agent);

                System.out.println("[web] 创建新 Agent: " + sessionKey);
                return agent;
            } catch (Exception e) {
                System.err.println("[web] 创建 Agent 失败: " + e.getMessage());
                e.printStackTrace();
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
        return sharedModelClient != null && sharedToolRegistry != null && sharedPrefix != null;
    }

    /**
     * 获取默认 Agent 实例（兼容旧代码）
     */
    public Agent4jAgent getAgent() {
        String defaultKey = generateSessionKey(null, null);
        return getOrCreateAgent(defaultKey);
    }

    // ==================== 状态查询 ====================

    /**
     * 获取 Agent 整体状态（供前端状态面板使用）。
     *
     * @return 状态信息 Map
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("ready", isReady());
        status.put("model", sharedModel);
        status.put("workspace", getWorkspace());
        status.put("cacheSize", agentCache.size());

        // 追加默认会话的详细信息
        String defaultKey = generateSessionKey(null, null);
        Agent4jAgent agent = agentCache.get(defaultKey);
        if (agent != null) {
            status.put("historySize", agent.historySize());
            status.put("planMode", agent.isPlanMode());
            status.put("hitlMode", agent.isHitlMode());
            SessionStore store = agent.getSessionStore();
            if (store != null) {
                status.put("sessionName", store.currentName());
            }
            long[] usage = agent.getSessionUsage();
            status.put("promptTokens", usage[0]);
            status.put("completionTokens", usage[1]);
        }
        return status;
    }

    /**
     * 获取会话的对话历史。
     *
     * @param workspacePath 工作区路径（可选）
     * @param sessionName   会话名称（可选，不传则使用当前活跃会话）
     * @return 历史消息列表
     */
    public List<Map<String, Object>> getHistory(String workspacePath, String sessionName) {
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
     * 获取当前活跃会话的对话历史（兼容旧接口）。
     *
     * @return 历史消息列表
     */
    public List<Map<String, Object>> getHistory() {
        return getHistory(null, null);
    }

    // ==================== 聊天 ====================

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

    /**
     * 同步聊天 —— 串行执行，返回完整回复。
     * <p>
     * 命令字符串（如 "/retry"、"/compact"）会由 Agent4jAgent.chat()
     * 自动路由到 {@link site.sorghum.agent4j.bin.command.ChatCommandRegistry} 处理。
     * </p>
     */
    public String chat(String message) throws IOException, InterruptedException {
        return chat(message, null, null);
    }

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
    public String chat(String message, String workspacePath, String sessionName) throws IOException, InterruptedException {
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
     * 流式聊天 —— 通过 AgentOutput 桥接到 SseEmitter。
     * <p>
     * 命令字符串同样在此通道处理，命令的输出通过 SSE 事件返回。
     * </p>
     */
    public void chatStream(String message, SseEmitter emitter) throws IOException, InterruptedException {
        chatStream(message, null, null, emitter);
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
    public void chatStream(String message, String workspacePath, String sessionName, SseEmitter emitter) throws IOException, InterruptedException {
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

            this.currentSseEmitter = emitter;

            // 设置 AgentOutput：将所有事件桥接到 SSE
            agent.setOutput(new AgentOutput() {
                @Override
                public void onContentDelta(String token) {
                    emitter.sendContent(token);
                }

                @Override
                public void onContentComplete() {
                }

                @Override
                public void onReasoningDelta(String token) {
                    emitter.sendReasoning(token);
                }

                @Override
                public void onReasoningComplete() {
                }

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
                public void onLog(LogLevel level, String message) {
                }

                @Override
                public void onMessage(String message) {
                }
            });

            // 设置会话ID到 AgentLoop
            String sessionId = sessionName != null ? sessionName : "default";
            agent.setSessionId(sessionId);
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
            // 清理 ThreadLocal
            currentSessionName.remove();
            // 恢复 Agent 输出
            Agent4jAgent agent = agentCache.get(sessionKey);
            if (agent != null) {
                agent.setOutput(AgentOutput.NOOP);
                agent.flushSession();
                agent.saveUsage();
            }

            this.currentSseEmitter = null;
            try {
                emitter.complete();
            } catch (Exception ex) {
                // SSE连接可能已断开，忽略异常
                System.err.println("[web] 完成SSE流失败（可能SSE连接已断开）: " + ex.getMessage());
            }

            lock.unlock();
        }
    }

    // ==================== 会话管理 ====================

    /**
     * 创建新会话（前端指定 sessionId，延迟持久化：文件在首次发消息时才创建）。
     *
     * @param workspacePath 工作区路径（可选）
     * @param sessionName   会话名称（前端指定，为空则自动生成）
     * @return 实际使用的会话名
     */
    public String newSession(String workspacePath, String sessionName) {
        String resolvedPath = workspacePath != null ? workspacePath : getWorkspace();

        // 未指定会话名时自动生成
        if (sessionName == null || sessionName.isEmpty()) {
            sessionName = "agent4j-" + new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date());
        }

        // 直接以目标会话名创建/获取 Agent（switchTo 是惰性的，不创建文件）
        try {
            switchSession(workspacePath, sessionName);
        } catch (IOException e) {
            System.err.println("[web] 创建会话失败: " + e.getMessage());
        }

        return sessionName;
    }

    /**
     * 创建新会话（兼容旧接口，自动生成名称）。
     */
    public void newSession(String workspacePath) {
        newSession(workspacePath, null);
    }

    /**
     * 创建新会话（兼容旧接口）。
     */
    public void newSession() {
        newSession(null, null);
    }

    /**
     * 列出会话。
     *
     * @param workspacePath 工作区路径（可选）
     * @return 会话列表
     */
    public List<Map<String, Object>> listSessions(String workspacePath) throws IOException {
        if (!isReady()) {
            return new ArrayList<>();
        }

        // 获取一个 Agent 实例来访问 SessionStore
        String sessionKey = generateSessionKey(workspacePath, null);
        Agent4jAgent agent = getOrCreateAgent(sessionKey);
        if (agent == null) {
            return new ArrayList<>();
        }

        SessionStore store = agent.getSessionStore();
        // 确定当前会话名（优先用追踪记录，其次用 store.currentName）
        String resolvedPath = workspacePath != null ? workspacePath : getWorkspace();
        String activeSession = currentSessionNames.get(resolvedPath);
        if (activeSession == null) {
            activeSession = store.currentName();
        }
        List<Map<String, Object>> sessions = new ArrayList<>();
        for (SessionStore.SessionInfo sessionInfo : store.list()) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", sessionInfo.name);
            info.put("title", store.getTitle(sessionInfo.name));
            info.put("messageCount", sessionInfo.messageCount);
            info.put("current", sessionInfo.name.equals(activeSession));
            sessions.add(info);
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
    public boolean switchSession(String workspacePath, String sessionName) throws IOException {
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
     * 获取当前会话的 token 用量。
     *
     * @return [promptTokens, completionTokens, cacheHitTokens, cacheMissTokens]
     */
    public long[] getUsage() {
        // 汇总所有会话的用量
        long totalPrompt = 0, totalCompletion = 0, totalCacheHit = 0, totalCacheMiss = 0;
        for (Agent4jAgent agent : agentCache.values()) {
            if (agent != null) {
                long[] usage = agent.getSessionUsage();
                totalPrompt += usage[0];
                totalCompletion += usage[1];
                totalCacheHit += usage[2];
                totalCacheMiss += usage[3];
            }
        }
        return new long[]{totalPrompt, totalCompletion, totalCacheHit, totalCacheMiss};
    }

    /**
     * 获取指定会话的 token 用量。
     *
     * @param workspacePath 工作区路径（可选）
     * @param sessionName   会话名称（可选）
     * @return [promptTokens, completionTokens, cacheHitTokens, cacheMissTokens]
     */
    public long[] getSessionUsage(String workspacePath, String sessionName) {
        String sessionKey = generateSessionKey(workspacePath, sessionName);
        Agent4jAgent agent = getOrCreateAgent(sessionKey);
        if (agent != null) {
            return agent.getSessionUsage();
        }
        return new long[]{0, 0, 0, 0};
    }

    /**
     * 获取指定会话的 token 用量（返回 Map 格式，前端友好）。
     *
     * @param workspacePath 工作区路径（可选）
     * @param sessionName   会话名称（可选）
     * @return usage 数据 Map
     */
    public Map<String, Object> getSessionUsageMap(String workspacePath, String sessionName) {
        String sessionKey = generateSessionKey(workspacePath, sessionName);
        Agent4jAgent agent = agentCache.get(sessionKey);
        Map<String, Object> result = new LinkedHashMap<>();
        
        if (agent != null) {
            long[] usage = agent.getSessionUsage();
            long promptTokens = usage[0];
            long completionTokens = usage[1];
            long cacheHit = usage[2];
            long cacheMiss = usage[3];
            long lastPromptTokens = usage.length > 4 ? usage[4] : 0;
            int maxContextTokens = agent.getMaxContextTokens();
            
            result.put("promptTokens", promptTokens);
            result.put("completionTokens", completionTokens);
            result.put("cacheHit", cacheHit);
            result.put("cacheMiss", cacheMiss);
            result.put("lastPromptTokens", lastPromptTokens);
            result.put("maxContextTokens", maxContextTokens);
            result.put("totalTokens", promptTokens + completionTokens);
        } else {
            result.put("promptTokens", 0);
            result.put("completionTokens", 0);
            result.put("cacheHit", 0);
            result.put("cacheMiss", 0);
            result.put("lastPromptTokens", 0);
            result.put("maxContextTokens", 128000);
            result.put("totalTokens", 0);
        }
        return result;
    }

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

    // ==================== 工具方法 ====================

    /**
     * 加载用户级默认系统提示词。
     * 优先级：~/.agent4j/agent4j.md > 硬编码默认值
     */
    private static String loadDefaultSystemPrompt() {
        // 如果 ~/.agent4j/agent4j.md 存在，以其内容作为默认系统提示词
        Path homePrompt = Paths.get(System.getProperty("user.home"), ".agent4j", "agent4j.md");
        if (java.nio.file.Files.exists(homePrompt)) {
            try {
                String content = new String(java.nio.file.Files.readAllBytes(homePrompt),
                        java.nio.charset.StandardCharsets.UTF_8);
                if (content != null && !content.trim().isEmpty()) {
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
     * 如果当前工作区存在 agent4j.md / CLAUDE.md，则读取并返回其内容。
     * 文件不存在时返回空字符串。
     */
    private static String loadProjectMd(Path workspace) {
        StringBuilder sb = new StringBuilder();
        for (String name : new String[]{"agent4j.md", "CLAUDE.md"}) {
            Path file = workspace.resolve(name);
            if (java.nio.file.Files.exists(file)) {
                try {
                    String content = new String(java.nio.file.Files.readAllBytes(file),
                            java.nio.charset.StandardCharsets.UTF_8);
                    if (sb.length() > 0) sb.append("\n\n");
                    sb.append("[来自 ").append(name).append(" 的项目上下文]\n");
                    sb.append(content.trim());
                } catch (IOException ignored) {
                }
            }
        }
        return sb.toString();
    }

    /**
     * 从环境变量读取配置，如果环境变量不存在则使用默认值。
     */
    private static String envOr(String envName, String defaultValue) {
        String value = System.getenv(envName);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    // ==================== 兼容旧接口 ====================

    /**
     * 计算工作区路径的 hash 值（与 WorkspaceManager 一致，MD5 前 12 位）。
     */
    public static String computeWorkspaceHash(String workspacePath) {
        return WorkspaceManager.computeHash(workspacePath);
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
        if (sharedConfig != null && sharedConfig.workspaceDir() != null) {
            return sharedConfig.workspaceDir().toAbsolutePath().toString();
        }
        return ".";
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
     * 获取指定会话的 token 用量（兼容旧接口）。
     *
     * @param workspaceHash 工作区 hash（现在用作 workspacePath）
     * @param sessionName   会话名称（可选）
     * @return [promptTokens, completionTokens, cacheHitTokens, cacheMissTokens]
     */
    public long[] getUsage(String workspaceHash, String sessionName) {
        return getSessionUsage(workspaceHash, sessionName);
    }

    /**
     * 切换工作区（兼容旧接口）。
     * 在新架构中，工作区路径由会话标识决定，此方法仅用于更新默认配置。
     *
     * @param path 新的工作区路径
     * @return 切换成功返回 true
     */
    public boolean switchWorkspace(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        // 在新架构中，工作区路径在创建 Agent 时确定
        // 这里仅清除默认会话的缓存，让下次访问时使用新路径
        String defaultKey = generateSessionKey(null, null);
        evictAgent(null, null);
        System.out.println("[web] 工作区已切换: " + path);
        return true;
    }

    /**
     * 列出工作区（兼容旧接口）。
     * 需要 WorkspaceManager 支持。
     *
     * @return 工作区列表
     */
    public List<Map<String, Object>> listWorkspaces() {
        List<Map<String, Object>> result = new ArrayList<>();
        // 从缓存中收集所有工作区路径
        Set<String> workspacePaths = new HashSet<>();
        for (String key : agentCache.keySet()) {
            String[] parts = key.split("::", 2);
            if (parts.length > 0) {
                workspacePaths.add(parts[0]);
            }
        }

        // 添加默认工作区
        if (sharedConfig != null && sharedConfig.workspaceDir() != null) {
            workspacePaths.add(sharedConfig.workspaceDir().toAbsolutePath().toString());
        }

        for (String path : workspacePaths) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("path", path);
            info.put("hash", WorkspaceManager.computeHash(path));
            result.add(info);
        }
        return result;
    }

    /**
     * 切换到指定工作区（兼容旧接口）。
     *
     * @param path 工作区路径
     * @return 切换成功返回 true
     */
    public boolean switchToWorkspace(String path) {
        return switchWorkspace(path);
    }

    /**
     * 获取当前会话信息（兼容旧接口）。
     *
     * @return 当前会话信息
     */
    public Map<String, Object> getCurrentSession() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("workspacePath", getWorkspace());
        info.put("sessionName", getCurrentSessionName(null));
        return info;
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

        // 查找并清除该工作区的所有 Agent
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

        System.out.println("[web] 已删除工作区: " + hash + "，清除了 " + keysToRemove.size() + " 个 Agent");
        return !keysToRemove.isEmpty();
    }
}
