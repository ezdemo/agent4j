package site.sorghum.agent4j.bin.agent;

import lombok.SneakyThrows;
import org.noear.solon.Solon;
import site.sorghum.agent4j.bin.command.ChatCommand;
import site.sorghum.agent4j.bin.command.ChatCommandContext;
import site.sorghum.agent4j.bin.command.ChatCommandRegistry;
import site.sorghum.agent4j.bin.model.ModelClient;
import site.sorghum.agent4j.bin.model.HttpModelClient;
import site.sorghum.agent4j.bin.config.Agent4jConfig;
import site.sorghum.agent4j.bin.tool.ToolDef;
import site.sorghum.agent4j.bin.tool.ToolDefHelper;
import site.sorghum.agent4j.bin.tool.ToolRegistry;
import site.sorghum.agent4j.bin.session.SessionService;
import site.sorghum.agent4j.bin.session.SessionStore;
import site.sorghum.agent4j.bin.session.JsonlSessionStore;
import site.sorghum.agent4j.bin.workspace.WorkspaceManager;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolResult;
import site.sorghum.agent4j.tool.ToolParameter;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import site.sorghum.agent4j.bin.skill.SkillStoreV2;
import site.sorghum.agent4j.bin.skill.SkillV2;

/**
 * Agent4j 工厂——组装 ModelClient + ToolRegistry → AgentLoop。
 *
 * @author Sorghum
 */
public class Agent4jAgent {

    private AgentLoop loop;
    private ConversationContext ctx;
    private SessionService sessionService;
    private volatile Path workspace;
    private String apiUrl;
    private String apiKey;
    private WorkspaceManager workspaceManager;

    /** 命令注册表（用于 chat() 中自动路由 "/" 开头的命令） */
    private final ChatCommandRegistry commandRegistry;

    /** 技能存储（V2 - Claude Code 风格） */
    private SkillStoreV2 skillStore;

    /** 退出信号（命令返回 EXIT 时设置，主循环据此终止） */
    private volatile boolean terminated = false;

    private Agent4jAgent(Builder b) {
        this.commandRegistry = b.commandRegistry;
        this.workspace = b.workspace;
        this.apiUrl = b.apiUrl;
        this.apiKey = b.apiKey;

        ModelClient client = new HttpModelClient(b.apiUrl, b.apiKey, b.model);
        
        // 初始化技能存储（V2 - Claude Code 风格）
        this.skillStore = new SkillStoreV2(b.workspace, 
                Paths.get(System.getProperty("user.home")), 
                Collections.<Path>emptyList());
        Solon.context().beanInject(skillStore);
        // 使用普通工具注册表
        ToolRegistry registry = new ToolRegistry();

        // 设置禁用工具（被禁用的工具不会注册到 LLM 工具列表）
        Set<String> disabledTools = b.disabledTools != null ? b.disabledTools : Collections.<String>emptySet();
        registry.setDisabledTools(disabledTools);
        if (!disabledTools.isEmpty()) {
            System.err.println("[config] 已禁用工具: " + String.join(", ", disabledTools));
        }

        // 屏蔽目录列表
        final List<String> blockedPaths = b.blockedPaths != null ? b.blockedPaths : Collections.<String>emptyList();
        if (!blockedPaths.isEmpty()) {
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
            registry.register(new ToolDef(
                    tool.getName(),
                    tool.getDescription(),
                    ToolDefHelper.toParamDefs(tool.getParameters()),
                    args -> {
                        // 从 args 中获取 sessionId（由 ToolDispatcher 在执行前注入）
                        String sessionId = args != null ? (String) args.remove("__sessionId__") : null;
                        return ToolDefHelper.formatResult(tool.execute(
                                new ToolContext(args, getWorkspace(), apiUrl, apiKey, (Object) registry, blockedPaths, sessionId)));
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
        String systemPrompt = b.systemPrompt;
        String projectMd = loadProjectMd(b.workspace);
        if (!projectMd.isEmpty()) {
            systemPrompt = projectMd + "\n\n---\n\n" + systemPrompt;
        }
        // 将工具规范追加到 system prompt 末尾
        systemPrompt = systemPrompt + "\n\n" + toolSpecsBuilder.toString().trim();
        
        // 添加 skill 索引到 system prompt
        String skillsIndex = skillStore.buildSkillsIndex();
        if (!skillsIndex.isEmpty()) {
            systemPrompt = systemPrompt + "\n\n" + skillsIndex;
            System.err.println("[skill] 已加载 skill 索引，共 " + skillStore.list().size() + " 个 skill");
        }

        // 构建缓存优先前缀：system prompt + 工具定义（注册后冻结，跨 turn 稳定）
        PromptPrefix prefix = new PromptPrefix(systemPrompt, registry.toOpenAiTools());
        this.ctx = new ConversationContext(prefix);

        // 会话持久化 — 委托 SessionService（支持工作区隔离）
        try {
            this.workspaceManager = new WorkspaceManager();
            String workspacePath = b.workspace.toAbsolutePath().toString();
            workspaceManager.initWorkspace(workspacePath);
            java.nio.file.Path sessionsDir = workspaceManager.getSessionsDir(workspacePath);
            this.sessionService = new SessionService(ctx, sessionsDir);
            sessionService.loadOrCreate(System.getenv("AGENT4J_SESSION"));
        } catch (IOException e) {
            System.err.println("[session] 初始化失败: " + e.getMessage());
        }

        this.loop = new AgentLoop(client, registry, ctx, b.hitl);
        // 设置 SessionService 引用，用于同步 lastPromptTokens
        this.loop.setSessionService(this.sessionService);
    }
    
    /**
     * 轻量级构造函数 —— 共享 ModelClient、ToolRegistry 和 PromptPrefix。
     * 适用于"一个会话一个 Agent"场景，减少资源消耗。
     *
     * @param b Builder
     * @param lightweight 标记为轻量级构建（未使用，仅用于区分构造函数）
     */
    private Agent4jAgent(Builder b, boolean lightweight) {
        this.commandRegistry = b.commandRegistry;
        this.workspace = b.workspace;
        this.apiUrl = b.apiUrl;
        this.apiKey = b.apiKey;

        // 使用共享组件
        ModelClient client = b.sharedModelClient;
        ToolRegistry registry = b.sharedToolRegistry;
        PromptPrefix prefix = b.sharedPrefix;

        // 创建独立的会话上下文
        this.ctx = new ConversationContext(prefix);

        // 会话持久化 — 委托 SessionService（支持工作区隔离）
        try {
            this.workspaceManager = new WorkspaceManager();
            String workspacePath = b.workspace.toAbsolutePath().toString();
            workspaceManager.initWorkspace(workspacePath);
            java.nio.file.Path sessionsDir = workspaceManager.getSessionsDir(workspacePath);
            this.sessionService = new SessionService(ctx, sessionsDir);
            sessionService.loadOrCreate(System.getenv("AGENT4J_SESSION"));
        } catch (IOException e) {
            System.err.println("[session] 初始化失败: " + e.getMessage());
        }

        this.loop = new AgentLoop(client, registry, ctx, b.hitl);
        // 设置 SessionService 引用，用于同步 lastPromptTokens
        this.loop.setSessionService(this.sessionService);
    }

    // 使用 ToolDefHelper 提供的公共方法

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
            System.out.println("[session] 自动生成会话标题: " + title);
        }
    }

    // ========== 公共 API ==========

    /** 检查是否收到退出信号（命令返回 EXIT 后为 true） */
    public boolean isTerminated() { return terminated; }

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
        try { sessionService.newSession(); } catch (IOException ignored) {}
    }

    /**
     * 切换到指定会话。
     * 切换后加载历史消息到上下文并恢复 token 用量。
     */
    public boolean switchSession(String name) {
        boolean ok = getSessionStore().switchTo(name);
        if (ok) {
            // 加载会话历史消息到上下文，将 JSONL 中的 OpenAI 格式 tool_calls
            // 转回内存格式 {id, name, arguments}，与新创建的消息保持一致
            try {
                List<Map<String, Object>> loaded = getSessionStore().load();
                for (Map<String, Object> m : loaded) {
                    normalizeToolCalls(m);
                    sessionService.injectHistory(m);
                }
            } catch (IOException e) {
                System.err.println("[session] 加载会话历史失败: " + e.getMessage());
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
        return ok;
    }

    /**
     * 将 JSONL 中的 OpenAI 格式 tool_calls 转回内存格式 {id, name, arguments}。
     * 加载历史时调用，保证注入上下文的消息与新创建的格式一致。
     */
    @SuppressWarnings("unchecked")
    private static void normalizeToolCalls(Map<String, Object> msg) {
        Object tcs = msg.get("tool_calls");
        if (!(tcs instanceof List)) return;
        List<Map<String, Object>> list = (List<Map<String, Object>>) tcs;
        for (Map<String, Object> tc : list) {
            // 已经是内存格式 {id, name, arguments} → 跳过
            if (tc.containsKey("name") && !tc.containsKey("type")) continue;
            // OpenAI 格式 {id, type, function: {name, arguments}} → 转换
            Object func = tc.get("function");
            if (func instanceof Map) {
                Map<String, Object> fm = (Map<String, Object>) func;
                Object fnName = fm.get("name");
                if (fnName != null) tc.put("name", fnName.toString());
                Object fnArgs = fm.get("arguments");
                if (fnArgs != null) tc.put("arguments", fnArgs.toString());
            }
            tc.remove("type");
            tc.remove("function");
        }
    }

    /** 累计 token 用量 */
    public void addUsage(int prompt, int completion, int cacheHit, int cacheMiss) {
        sessionService.addUsage(prompt, completion, cacheHit, cacheMiss);
    }

    /** 获取会话累计 token 用量 */
    public long[] getSessionUsage() {
        return sessionService.getUsage();
    }

    /** 获取最近一次 API 返回的 prompt_tokens */
    public int getLastPromptTokens() {
        return loop != null ? loop.getLastPromptTokens() : 0;
    }

    /** 获取模型最大上下文窗口 token 数 */
    public int getMaxContextTokens() {
        return loop != null ? loop.getMaxContextTokens() : 128000;
    }

    /** 更新当前模型 */
    public void updateModel(String model) {
        if (loop != null) {
            loop.getclient().setModel(model);
        }
    }

    /** /retry 撤回最后一条消息并重试 */
    public String retryLast() throws IOException {
        String msg = ctx.retryLastUser();
        return msg != null ? chat(msg) : null;
    }

    /** /rewind N 回退到第 N 轮 */
    public String rewind(int n) throws IOException {
        String msg = ctx.rewindToUser(n);
        return msg != null ? chat(msg) : null;
    }

    /** 获取当前工作目录 */
    public Path getWorkspace() {
        return workspace;
    }

    /**
     * 切换工作目录。
     * 切换后，所有工具将使用新的工作目录执行。
     * 同时会切换到对应工作区的会话目录。
     *
     * @param newWorkspace 新的工作目录路径
     * @return 切换成功返回 true，路径无效返回 false
     */
    public boolean switchWorkspace(Path newWorkspace) {
        if (newWorkspace == null || !java.nio.file.Files.isDirectory(newWorkspace)) {
            return false;
        }
        this.workspace = newWorkspace.toAbsolutePath();
        
        // 切换工作区会话目录
        if (workspaceManager != null) {
            try {
                String workspacePath = newWorkspace.toAbsolutePath().toString();
                workspaceManager.switchWorkspace(workspacePath);
                
                // 关闭旧的 SessionService 的 store
                if (sessionService != null) {
                    sessionService.saveUsage();
                    SessionStore oldStore = sessionService.getStore();
                    if (oldStore instanceof JsonlSessionStore) {
                        ((JsonlSessionStore) oldStore).shutdown();
                    }
                }
                
                // 重新创建 SessionService 使用新工作区的会话目录
                java.nio.file.Path sessionsDir = workspaceManager.getSessionsDir(workspacePath);
                this.sessionService = new SessionService(ctx, sessionsDir);
                // ★ 同步更新 AgentLoop 的 SessionService 引用（修复 lastPromptTokens 跟踪）
                this.loop.setSessionService(this.sessionService);
                
                System.out.println("[workspace] 已切换到工作区: " + workspacePath);
                System.out.println("[workspace] 会话目录: " + sessionsDir);
            } catch (IOException e) {
                System.err.println("[workspace] 切换工作区失败: " + e.getMessage());
                return false;
            }
        }
        
        return true;
    }

    /** 获取工作区管理器 */
    public WorkspaceManager getWorkspaceManager() {
        return workspaceManager;
    }

    public void setListener(AgentLoopListener listener) {
        loop.setListener(listener);
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

    /** 获取当前输出接口 */
    public AgentOutput getOutput() {
        return loop.getOutput();
    }

    /** 设置当前会话ID（用于工具执行上下文） */
    public void setSessionId(String sessionId) {
        if (loop != null) {
            loop.setSessionId(sessionId);
        }
    }

    /** 获取 SessionStore（用于列表/切换） */
    public SessionStore getSessionStore() {
        return sessionService.getStore();
    }

    /** 设置 SessionStore */
    public void setSessionStore(SessionStore store) {
        ctx.setSessionStore(store);
        // 重建 SessionService 以保持一致性
        this.sessionService = new SessionService(ctx, store);
        // ★ 同步更新 AgentLoop 的 SessionService 引用
        this.loop.setSessionService(this.sessionService);
    }

    /** 获取当前 SessionService（用于保存/恢复状态） */
    public SessionService getSessionService() {
        return sessionService;
    }

    /**
     * 轻量级恢复工作区状态 —— 直接恢复 SessionService 引用，不销毁/重建 store。
     * <p>
     * 用于 chatStream 等临时切换场景：先保存原始 SessionService，
     * 聊天结束后用此方法恢复，避免 switchWorkspace() 销毁再重建导致
     * 用量数据丢失、titleGenerated 重置等问题。
     * </p>
     *
     * @param workspace       原始工作区路径
     * @param originalService 原始 SessionService 实例
     */
    public void restoreWorkspaceState(Path workspace, SessionService originalService) {
        this.workspace = workspace.toAbsolutePath();
        this.sessionService = originalService;
        ctx.setSessionStore(originalService.getStore());
        this.loop.setSessionService(originalService);
    }

    /** 注入历史消息（加载会话时） */
    public void injectHistory(Map<String, Object> msg) {
        sessionService.injectHistory(msg);
    }

    /** 进入/退出 Plan Mode（提示词始终包含规则，仅切换 dispatch 门控） */
    public void setPlanMode(boolean on) {
        loop.setPlanMode(on);
    }

    public boolean isPlanMode() {
        return loop.isPlanMode();
    }

    // ========== HITL (Human-In-The-Loop) ==========

    /** 获取 HITL 模式状态 */
    public boolean isHitlMode() { return loop.isHitlMode(); }

    /** 切换 HITL 模式 */
    public void toggleHitl() { loop.toggleHitl(); }

    /** 批准待执行的工具调用 */
    public void approveHITL() { loop.approveHITL(); }

    /** 拒绝待执行的工具调用 */
    public void denyHITL() { loop.denyHITL(); }

    /** 获取待审批的工具调用列表 */
    public java.util.List<java.util.Map<String, Object>> getPendingHITTcList() {
        return loop.getPendingHITTcList();
    }

    /** 是否有待审批的工具调用 */
    public boolean hasPendingHITL() { return loop.hasPendingHITL(); }

    /** 保存会话用量（退出前调用） */
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

    /** 获取工具注册表（供 Web API 列出工具使用） */
    public site.sorghum.agent4j.bin.tool.ToolRegistry getToolRegistry() {
        return loop.getToolRegistry();
    }
    
    /** 获取技能存储（V2） */
    public SkillStoreV2 getSkillStore() {
        return skillStore;
    }

    public int historySize() {
        return ctx.size();
    }

    public static Builder builder() {
        return new Builder();
    }

    // ---- 项目文档加载 ----

    /**
     * 加载工作区根目录下的 agent4j.md 和 CLAUDE.md，
     * 将项目文档作为系统提示的补充上下文。
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
                } catch (IOException ignored) {}
            }
        }
        return sb.toString();
    }

    public static class Builder {
        String apiUrl;
        String apiKey;
        String model = "deepseek-v4-flash";
        /** 默认系统提示词。如果 ~/.agent4j/agent4j.md 存在则从中读取，否则用此硬编码默认值。 */
        String systemPrompt = DEFAULT_SYSTEM_PROMPT;
        Path workspace = Paths.get(".").toAbsolutePath();
        Set<String> disabledTools;
        List<String> blockedPaths;
        boolean hitl;
        /** 命令注册表（Solon 自动收集的 ChatCommand Bean） */
        ChatCommandRegistry commandRegistry;
        /** 用户是否显式设置过 systemPrompt */
        private boolean systemPromptExplicitlySet = false;
        
        /** 共享的 ModelClient（用于轻量级构建，避免重复创建 HTTP 客户端） */
        ModelClient sharedModelClient;
        /** 共享的 ToolRegistry（用于轻量级构建，避免重复注册工具） */
        ToolRegistry sharedToolRegistry;
        /** 共享的 system prompt（用于轻量级构建） */
        String sharedSystemPrompt;
        /** 共享的 PromptPrefix（用于轻量级构建） */
        PromptPrefix sharedPrefix;

        /** 硬编码的默认系统提示词（在 ~/.agent4j/agent4j.md 不存在时使用） */
        private static final String DEFAULT_SYSTEM_PROMPT = "你是一个智能体助手，名为Agent4J\n";

        public Builder apiUrl(String v) { this.apiUrl = v; return this; }
        public Builder apiKey(String v) { this.apiKey = v; return this; }
        public Builder model(String v) { this.model = v; return this; }
        public Builder systemPrompt(String v) { this.systemPrompt = v; this.systemPromptExplicitlySet = true; return this; }
        public Builder workspace(Path v) { this.workspace = v; return this; }
        public Builder commandRegistry(ChatCommandRegistry v) { this.commandRegistry = v; return this; }

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

        /**
         * 加载用户级默认系统提示词。
         * 优先级：builder.systemPrompt(v) 显式设置 > ~/.agent4j/agent4j.md > 硬编码默认值
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
            return DEFAULT_SYSTEM_PROMPT;
        }

        public Agent4jAgent build() {
            Objects.requireNonNull(apiUrl, "apiUrl is required");
            Objects.requireNonNull(apiKey, "apiKey is required");
            // 如果用户没有显式设置 systemPrompt，则尝试从 ~/.agent4j/agent4j.md 读取默认值
            if (!systemPromptExplicitlySet) {
                systemPrompt = loadDefaultSystemPrompt();
            }
            return new Agent4jAgent(this);
        }
        
        /**
         * 设置共享组件，用于轻量级构建。
         * 避免每个会话都重新创建 ModelClient 和 ToolRegistry。
         *
         * @param client 共享的 ModelClient
         * @param registry 共享的 ToolRegistry
         * @param prefix 共享的 PromptPrefix
         * @return this
         */
        public Builder sharedComponents(ModelClient client, ToolRegistry registry, PromptPrefix prefix) {
            this.sharedModelClient = client;
            this.sharedToolRegistry = registry;
            this.sharedPrefix = prefix;
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
            Objects.requireNonNull(sharedToolRegistry, "sharedToolRegistry is required");
            Objects.requireNonNull(sharedPrefix, "sharedPrefix is required");
            return new Agent4jAgent(this, true);
        }
    }
}
