package site.sorghum.agent4j.bin.agent;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import site.sorghum.agent4j.bin.command.ChatCommand;
import site.sorghum.agent4j.bin.command.ChatCommandContext;
import site.sorghum.agent4j.bin.command.ChatCommandRegistry;
import site.sorghum.agent4j.bin.config.Agent4jConfig;
import site.sorghum.agent4j.bin.model.HttpModelClient;
import site.sorghum.agent4j.bin.model.ModelClient;
import site.sorghum.agent4j.bin.session.SessionService;
import site.sorghum.agent4j.bin.session.SessionStore;
import site.sorghum.agent4j.bin.tool.ToolRegistry;
import site.sorghum.agent4j.bin.tool.ToolSystemInitializer;
import site.sorghum.agent4j.bin.workspace.WorkspaceManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Agent4j 工厂——组装 ModelClient + ToolRegistry → AgentLoop。
 *
 * @author Sorghum
 */
@Slf4j
public class Agent4jAgent {

    private final AgentLoop loop;
    private final ConversationContext ctx;
    /**
     * 命令注册表（用于 chat() 中自动路由 "/" 开头的命令）
     */
    private final ChatCommandRegistry commandRegistry;
    /**
     * -- GETTER --
     * 获取当前 SessionService（用于保存/恢复状态）
     */
    @Getter
    private SessionService sessionService;
    /**
     * -- GETTER --
     * 获取当前工作目录
     */
    @Getter
    private volatile Path workspace;
    /**
     * -- GETTER --
     * 获取工作区管理器
     */
    @Getter
    private WorkspaceManager workspaceManager;

    /**
     * 退出信号（命令返回 EXIT 时设置，主循环据此终止）
     * -- GETTER --
     * 检查是否收到退出信号（命令返回 EXIT 后为 true）
     */
    @Getter
    private volatile boolean terminated = false;

    private Agent4jAgent(Builder b) {
        this.commandRegistry = b.commandRegistry;
        this.workspace = b.workspace;

        ModelClient client = new HttpModelClient(b.apiUrl, b.apiKey, b.model);

        // 初始化技能存储（V2 - Claude Code 风格）

        // 使用 ToolSystemInitializer 统一初始化（消除重复代码）
        ToolSystemInitializer.Result initResult = ToolSystemInitializer.initialize(
                b.workspace, b.apiUrl, b.apiKey,
                b.disabledTools, b.blockedPaths, b.systemPrompt);
        ToolRegistry registry = initResult.toolRegistry;
        this.ctx = new ConversationContext(initResult.promptPrefix);

        // 会话持久化 — 委托 SessionService（支持工作区隔离）
        try {
            this.workspaceManager = new WorkspaceManager();
            String workspacePath = b.workspace != null
                    ? b.workspace.toAbsolutePath().toString()
                    : Paths.get(System.getProperty("user.home"), ".agent4j").toString();
            workspaceManager.initWorkspace(workspacePath);
            Path sessionsDir = workspaceManager.getSessionsDir(workspacePath);
            this.sessionService = new SessionService(ctx, sessionsDir);
            sessionService.loadOrCreate(System.getenv("AGENT4J_SESSION"));
        } catch (IOException e) {
            log.error("[session] 初始化失败: {}", e.getMessage());
        }

        this.loop = new AgentLoop(client, registry, ctx, b.hitl);
        // 设置 SessionService 引用，用于同步 lastPromptTokens
        this.loop.setSessionService(this.sessionService);
    }

    /**
     * 轻量级构造函数 —— 共享 ModelClient、ToolRegistry 和 PromptPrefix。
     * 适用于"一个会话一个 Agent"场景，减少资源消耗。
     *
     * @param b           Builder
     * @param lightweight 标记为轻量级构建（未使用，仅用于区分构造函数）
     */
    private Agent4jAgent(Builder b, boolean lightweight) {
        this.commandRegistry = b.commandRegistry;
        this.workspace = b.workspace;

        // 共享 ModelClient（无状态 HTTP 客户端），但每个 Agent 创建独立的 ToolRegistry
        ModelClient client = b.sharedModelClient;

        // 为当前工作区创建独立的 ToolRegistry 和 SkillStore
        String prompt = b.systemPrompt != null ? b.systemPrompt
                : (b.sharedSystemPrompt != null ? b.sharedSystemPrompt : "你是一个智能体助手，名为Agent4J\n");
        ToolSystemInitializer.Result initResult = ToolSystemInitializer.initialize(
                b.workspace, b.apiUrl, b.apiKey,
                b.disabledTools, b.blockedPaths, prompt);
        ToolRegistry registry = initResult.toolRegistry;
        PromptPrefix prefix = b.sharedPrefix != null ? b.sharedPrefix : initResult.promptPrefix;

        // 创建独立的会话上下文
        this.ctx = new ConversationContext(prefix);

        // 会话持久化 — 委托 SessionService（支持工作区隔离）
        try {
            this.workspaceManager = new WorkspaceManager();
            String workspacePath = b.workspace != null
                    ? b.workspace.toAbsolutePath().toString()
                    : Paths.get(System.getProperty("user.home"), ".agent4j").toString();
            workspaceManager.initWorkspace(workspacePath);
            Path sessionsDir = workspaceManager.getSessionsDir(workspacePath);
            this.sessionService = new SessionService(ctx, sessionsDir);
            sessionService.loadOrCreate(System.getenv("AGENT4J_SESSION"));
        } catch (IOException e) {
            log.error("[session] 初始化失败: {}", e.getMessage());
        }

        this.loop = new AgentLoop(client, registry, ctx, b.hitl);
        // 设置 SessionService 引用，用于同步 lastPromptTokens
        this.loop.setSessionService(this.sessionService);
    }

    // 使用 ToolDefHelper 提供的公共方法


    // ========== 公共 API ==========

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 如果当前会话尚未生成标题，则根据用户消息生成标题。
     *
     * @param userMessage 用户消息内容
     */
    private void generateSessionTitleIfNeeded(String userMessage) {
        if (sessionService != null && !sessionService.isTitleGenerated()) {
            // 确保会话名已分配（新会话的 currentName 初始为 null，延迟到首次 append 才分配）
            sessionService.ensureSessionName();
            String title = sessionService.generateSessionTitle(userMessage);
            sessionService.updateCurrentSessionTitle(title);
            sessionService.setTitleGenerated(true);
            log.info("[session] 自动生成会话标题: {}", title);
        }
    }

    /**
     * 处理用户输入，自动路由 "/" 命令或转发到 LLM 推理循环。
     * <p>
     * 命令处理通过 {@link ChatCommandRegistry} 自动分发 ——
     * 新增命令只需实现 {@link ChatCommand} 接口并标注 {@code @Component}，
     * 即可被 IoC 容器收集并在此处自动匹配执行。
     * </p>
     */
    @SneakyThrows
    public String chat(String message) throws IOException {
        // HITL 恢复（approve/deny 后传入 null 触发恢复）
        if (message == null) {
            return loop.run(null);
        }

        // === 命令处理（通过 IoC 注册的命令接口自动分发）===
        if (message.startsWith("/") && commandRegistry != null) {
            ChatCommand cmd = commandRegistry.match(message);
            if (cmd != null) {
                // 构造命令上下文：命令通过此上下文访问 agent 与退出机制
                ChatCommandContext cmdContext = new ChatCommandContext(
                        this, null, () -> this.terminated = true);
                ChatCommand.CommandResult result = cmd.execute(message, cmdContext);
                // 命令执行后自动刷入会话与保存用量
                flushSession();
                saveUsage();
                if (result == ChatCommand.CommandResult.EXIT) {
                    this.terminated = true;
                    return "/exit";
                }
                // 静默命令（如 /agree、/deny）不返回确认消息，
                // 它们的实际输出已在命令内部通过 AgentOutput/SSE 发送
                if (cmd.isSilent()) {
                    return null;
                }
                // 返回执行确认（Web 模式下前端需要看到回复，CLI 模式也便于追踪）
                return "✅ 已执行 " + message + " 命令";
            }
            // 未匹配到命令："/" 开头但不是命令，降级为普通聊天消息
        }

        // === 普通聊天逻辑 ===
        // 如果是第一条用户消息，自动生成会话标题
        generateSessionTitleIfNeeded(message);
        // 设置当前会话ID到 AgentLoop（用于工具执行上下文）
        String currentSessionId = sessionService != null ? sessionService.getStore().currentName() : null;
        loop.setSessionId(currentSessionId);
        return loop.run(message);
    }

    public void newSession() {
        try {
            sessionService.newSession();
        } catch (IOException ignored) {
        }
    }

    /**
     * 切换到指定会话。
     * 切换后加载历史消息到上下文并恢复 token 用量。
     */
    public void switchSession(String name) {
        boolean ok = getSessionStore().switchTo(name);
        if (ok) {
            // 加载会话历史消息到上下文，将 JSONL 中的 OpenAI 格式 tool_calls
            // 转回内存格式 {id, name, arguments}，与新创建的消息保持一致
            try {
                List<ChatMessage> loaded = getSessionStore().load();
                for (ChatMessage m : loaded) {
                    sessionService.injectHistory(m);
                }
            } catch (IOException e) {
                log.error("[session] 加载会话历史失败: {}", e.getMessage());
            }
            // 恢复该会话的 token 用量
            sessionService.restoreUsage(name);
            try {
                String existingTitle = getSessionStore().getTitle(name);
                sessionService.setTitleGenerated(existingTitle != null && !existingTitle.isEmpty());
            } catch (IOException e) {
                sessionService.setTitleGenerated(false);
            }
        }
    }

    /**
     * 累计 token 用量
     */
    public void addUsage(int prompt, int completion, int cacheHit, int cacheMiss) {
        sessionService.addUsage(prompt, completion, cacheHit, cacheMiss);
    }

    /**
     * 按模型累计 token 用量
     */
    public void addUsage(String model, int prompt, int completion, int cacheHit, int cacheMiss) {
        sessionService.addUsage(model, prompt, completion, cacheHit, cacheMiss);
    }

    /**
     * 获取会话累计 token 用量
     */
    public long[] getSessionUsage() {
        return sessionService.getUsage();
    }

    /**
     * 获取按模型分别累计的 token 用量
     */
    public Map<String, long[]> getModelUsage() {
        return sessionService.getModelUsage();
    }

    /**
     * 获取模型最大上下文窗口 token 数
     */
    public int getMaxContextTokens() {
        return loop != null ? loop.getMaxContextTokens() : 128000;
    }

    /**
     * /retry 撤回最后一条消息并重试
     */
    public String retryLast() throws IOException {
        String msg = ctx.retryLastUser();
        return msg != null ? chat(msg) : null;
    }

    /**
     * /rewind N 回退到第 N 轮
     */
    public String rewind(int n) throws IOException {
        String msg = ctx.rewindToUser(n);
        return msg != null ? chat(msg) : null;
    }

    public void setListener(AgentLoopListener listener) {
        loop.setListener(listener);
    }

    /**
     * 获取当前输出接口
     */
    public AgentOutput getOutput() {
        return loop.getOutput();
    }

    /**
     * 设置输出接口。
     * <p>
     * 所有 Agent 的输出（流式内容、思考、工具调用、日志等）都会通过此接口发送。
     * 默认使用 {@link ConsoleAgentOutput} 打印到控制台。
     * 可传入自定义实现（如 WebSocket SSE、日志文件等）。
     * </p>
     *
     * @param output 输出接口实现，传入 null 则使用 NOOP（关闭输出）
     */
    public void setOutput(AgentOutput output) {
        loop.setOutput(output);
    }

    /**
     * 设置当前会话ID（用于工具执行上下文）
     */
    public void setSessionId(String sessionId) {
        if (loop != null) {
            loop.setSessionId(sessionId);
        }
    }

    /**
     * 获取 SessionStore（用于列表/切换）
     */
    public SessionStore getSessionStore() {
        return sessionService.getStore();
    }

    /**
     * 注入历史消息（加载会话时）
     */
    public void injectHistory(ChatMessage msg) {
        sessionService.injectHistory(msg);
    }

    // ========== HITL (Human-In-The-Loop) ==========

    public boolean isPlanMode() {
        return loop.isPlanMode();
    }

    /**
     * 进入/退出 Plan Mode（提示词始终包含规则，仅切换 dispatch 门控）
     */
    public void setPlanMode(boolean on) {
        loop.setPlanMode(on);
    }

    /**
     * 获取 HITL 模式状态
     */
    public boolean isHitlMode() {
        return loop.isHitlMode();
    }

    /**
     * 直接设置 HITL 模式（用于配置热更新）
     */
    public void setHitlMode(boolean on) {
        loop.setHitlMode(on);
    }

    /**
     * 切换 HITL 模式
     */
    public void toggleHitl() {
        loop.toggleHitl();
    }

    /**
     * 批准待执行的工具调用
     */
    public void approveHITL() {
        loop.approveHITL();
    }

    /**
     * 拒绝待执行的工具调用
     */
    public void denyHITL() {
        loop.denyHITL();
    }

    /**
     * 是否有待审批的工具调用
     */
    public boolean noPendingHITL() {
        return !loop.hasPendingHITL();
    }

    /**
     * 保存会话用量（退出前调用）
     */
    public void saveUsage() {
        sessionService.saveUsage();
    }

    /**
     * 中断当前聊天 —— 调用底层 ModelClient 的中断方法。
     * 用于前端主动停止生成。
     */
    public void abort() {
        if (loop != null) {
            loop.requestUserAbort();
        }
    }

    /**
     * 刷入会话数据到磁盘。
     * 每轮对话结束后调用，确保消息已持久化。
     */
    public void flushSession() {
        sessionService.flush();
    }

    public void compact() throws IOException {
        sessionService.saveUsage();
        loop.compactNow();
    }

    // ---- 项目文档加载 ----

    public int historySize() {
        return ctx.size();
    }

    public static class Builder {
        /**
         * 硬编码的默认系统提示词（在 ~/.agent4j/agent4j.md 不存在时使用）
         */
        private static final String DEFAULT_SYSTEM_PROMPT = "你是一个智能体助手，名为Agent4J\n";
        String apiUrl;
        String apiKey;
        String model = "deepseek-v4-flash";
        /**
         * 默认系统提示词。如果 ~/.agent4j/agent4j.md 存在则从中读取，否则用此硬编码默认值。
         */
        String systemPrompt = DEFAULT_SYSTEM_PROMPT;
        Path workspace = null;
        Set<String> disabledTools;
        List<String> blockedPaths;
        boolean hitl;
        /**
         * 命令注册表（Solon 自动收集的 ChatCommand Bean）
         */
        ChatCommandRegistry commandRegistry;
        /**
         * 共享的 ModelClient（用于轻量级构建，避免重复创建 HTTP 客户端）
         */
        ModelClient sharedModelClient;
        /**
         * 共享的 system prompt（用于轻量级构建）
         */
        String sharedSystemPrompt;
        /**
         * 共享的 PromptPrefix（用于轻量级构建）
         */
        PromptPrefix sharedPrefix;

        /**
         * 加载用户级默认系统提示词。
         */
        private static String loadDefaultSystemPrompt() {
            // 如果 ~/.agent4j/agent4j.md 存在，以其内容作为默认系统提示词
            Path homePrompt = Paths.get(System.getProperty("user.home"), ".agent4j", "agent4j.md");
            if (Files.exists(homePrompt)) {
                try {
                    String content = Files.readString(homePrompt);
                    if (!content.trim().isEmpty()) {
                        log.info("[prompt] 从 ~/.agent4j/agent4j.md 加载默认系统提示词（{} 字符）", content.length());
                        return content.trim();
                    }
                } catch (IOException e) {
                    log.error("[prompt] 读取 ~/.agent4j/agent4j.md 失败: {}", e.getMessage());
                }
            }
            return DEFAULT_SYSTEM_PROMPT;
        }

        public Builder apiUrl(String v) {
            this.apiUrl = v;
            return this;
        }

        public Builder apiKey(String v) {
            this.apiKey = v;
            return this;
        }

        public Builder model(String v) {
            this.model = v;
            return this;
        }

        public Builder workspace(Path v) {
            this.workspace = v;
            return this;
        }

        public Builder commandRegistry(ChatCommandRegistry v) {
            this.commandRegistry = v;
            return this;
        }

        public Builder hitl(boolean v) {
            this.hitl = v;
            return this;
        }

        public Builder config(Agent4jConfig c) {
            if (c.chatApiUrl() != null) this.apiUrl = c.chatApiUrl();
            if (c.apiKey() != null) this.apiKey = c.apiKey();
            this.model = c.model();
            this.workspace = c.workspaceDir();
            this.disabledTools = c.disabledTools();
            this.blockedPaths = c.blockedPaths();
            this.hitl = c.hitl();
            return this;
        }

        public Agent4jAgent build() {
            Objects.requireNonNull(apiUrl, "apiUrl is required");
            Objects.requireNonNull(apiKey, "apiKey is required");
            systemPrompt = loadDefaultSystemPrompt();
            return new Agent4jAgent(this);
        }

        /**
         * 单独设置共享的 ModelClient
         */
        public Builder sharedModelClient(ModelClient v) {
            this.sharedModelClient = v;
            return this;
        }

        /**
         * 单独设置共享的 PromptPrefix
         */
        public Builder sharedPrefix(PromptPrefix v) {
            this.sharedPrefix = v;
            return this;
        }

        /**
         * 单独设置共享的 system prompt
         */
        public Builder sharedSystemPrompt(String v) {
            this.sharedSystemPrompt = v;
            return this;
        }

        /**
         * 构建轻量级 Agent 实例。
         * 共享 ModelClient、ToolRegistry 和 PromptPrefix，仅创建独立的会话上下文。
         * 适用于"一个会话一个 Agent"场景，减少资源消耗。
         *
         * @return 轻量级 Agent 实例
         */
        public Agent4jAgent buildLightweight() {
            Objects.requireNonNull(sharedModelClient, "sharedModelClient is required");
            return new Agent4jAgent(this, true);
        }
    }
}
