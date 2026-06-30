package site.sorghum.agent4j.bin.agent.core;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.noear.snack4.Feature;
import org.noear.snack4.ONode;
import org.noear.snack4.Options;
import org.noear.snack4.json.JsonReader;
import org.noear.snack4.json.util.FormatUtil;
import org.noear.solon.ai.chat.interceptor.ToolRequest;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.ai.chat.tool.ToolCall;
import org.noear.solon.ai.chat.tool.ToolResult;
import site.sorghum.agent4j.bin.agent.context.ContextFolding;
import site.sorghum.agent4j.bin.agent.context.ConversationContext;
import site.sorghum.agent4j.bin.agent.context.MessageHealer;
import site.sorghum.agent4j.bin.agent.hitl.HitlManager;
import site.sorghum.agent4j.bin.agent.listener.AgentLoopListener;
import site.sorghum.agent4j.bin.agent.listener.NoOpAgentLoopListener;
import site.sorghum.agent4j.bin.agent.model.*;
import site.sorghum.agent4j.bin.agent.output.ConsoleAgentOutput;

import site.sorghum.agent4j.bin.agent.resilient.ReasonBreaker;
import site.sorghum.agent4j.bin.agent.resilient.Scavenger;
import site.sorghum.agent4j.bin.builtin.TaskTool;
import site.sorghum.agent4j.bin.config.Agent4jConfig;
import site.sorghum.agent4j.bin.model.ModelClient;
import site.sorghum.agent4j.bin.model.UserMessageSanitizer;
import site.sorghum.agent4j.bin.session.SessionService;
import site.sorghum.agent4j.bin.tool.ToolRegistry;
import site.sorghum.agent4j.tool.*;
import site.sorghum.agent4j.tool.interact.FinishTool;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Agent 循环 —— 编排 prompt → LLM → 工具调用 → 反馈结果 → LLM 的循环。
 * <p>
 * 消息历史通过 {@link ConversationContext} 在内存中累积跨回合持久化。
 * HITL（人工审批）逻辑委托给 {@link HitlManager} 管理。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
public class AgentLoop implements AgentLoopController {

    // ==================== 配置读取（带 null-safe 默认值） ====================

    /** 默认最大上下文字符数（200k 约 256k tokens 的保守估计，覆盖主流模型上下文窗口） */
    private static final int DEFAULT_MAX_CONTEXT_CHARS = 200_000;
    /** 折叠后保留尾部字符数（80k 确保折叠后仍有足够上下文供后续推理） */
    private static final int DEFAULT_KEEP_TAIL_CHARS = 80_000;
    /** 工具执行超时秒数（1080s=18min，覆盖长时间工具调用如大型构建/测试） */
    private static final int DEFAULT_TOOL_TIMEOUT_SEC = 1080;
    /** Storm 断路器自愈最大尝试次数 */
    private static final int DEFAULT_MAX_SELF_CORRECTION = 5;
    /** 流式响应等待超时秒数（防止 HTTP 流永不结束导致线程挂起） */
    private static final int DEFAULT_STREAM_LATCH_TIMEOUT_SEC = 300;

    private int maxTotalChars() {
        return config != null ? config.maxContextChars() : DEFAULT_MAX_CONTEXT_CHARS;
    }

    private int keepTailChars() {
        return config != null ? config.keepTailChars() : DEFAULT_KEEP_TAIL_CHARS;
    }

    private int toolTimeoutSec() {
        return config != null ? config.toolTimeoutSec() : DEFAULT_TOOL_TIMEOUT_SEC;
    }

    private int maxSelfCorrectionAttempts() {
        return config != null ? config.maxSelfCorrectionAttempts() : DEFAULT_MAX_SELF_CORRECTION;
    }

    // ==================== 核心字段 ====================

    private final ModelClient client;
    private final ToolRegistry registry;
    private final Agent4jConfig config;
    @Getter
    private final ConversationContext ctx;
    private final ReasonBreaker reasonBreaker = new ReasonBreaker();
    @Getter
    private final HitlManager hitlManager;

    @Setter
    private SessionService sessionService;

    private AgentLoopListener listener = NoOpAgentLoopListener.INSTANCE;

    @Getter
    private AgentOutput output = new ConsoleAgentOutput();

    /** 最近一次 API 返回的 prompt_tokens（0 = 尚无数据，回退到字符估算） */
    @Getter
    private int lastPromptTokens = 0;

    /** 用户主动中断标志（前端点击停止按钮时设置） */
    private volatile boolean userAbortRequested = false;

    /** 外部中断源（Runnable）—— 由父级 AgentLoopController 设置，子代理主循环会同步检查 */
    private volatile Runnable externalAbortSource = null;

    /** 当前正在执行的工具 Future 数组（用于 abort 时取消） */
    private volatile CompletableFuture<ChatMessage>[] activeToolFutures = null;

    /** 任务完成标志 —— finish 工具设置，非空时主循环将退出并返回该内容 */
    private volatile String finishContent = null;

    @Setter
    @Getter
    private volatile String sessionId;

    /** 主循环是否正在执行中（防止巡检线程与主循环冲突） */
    private final AtomicBoolean running = new AtomicBoolean(false);

    // ==================== 构造器 ====================

    public AgentLoop(ModelClient client, ToolRegistry registry, ConversationContext ctx) {
        this(client, registry, ctx, false, null);
    }

    public AgentLoop(ModelClient client, ToolRegistry registry, ConversationContext ctx, boolean hitlDefault) {
        this(client, registry, ctx, hitlDefault, null);
    }

    public AgentLoop(ModelClient client, ToolRegistry registry, ConversationContext ctx,
                     boolean hitlDefault, Agent4jConfig config) {
        this.client = client;
        this.registry = registry;
        this.ctx = ctx;
        this.config = config;
        this.hitlManager = new HitlManager(hitlDefault);
    }

    // ==================== 公共控制 API ====================

    /** 切换 HITL 模式 */
    public void toggleHitl() {
        hitlManager.toggleHitl();
    }

    /** 获取 HITL 模式状态 */
    public boolean isHitlMode() {
        return hitlManager.isHitlMode();
    }

    /** 设置 HITL 模式（用于配置热更新） */
    public void setHitlMode(boolean on) {
        hitlManager.setHitlMode(on);
    }

    /** 批准待执行的工具调用 */
    public void approveHITL() {
        hitlManager.approveHITL();
    }

    /** 拒绝待执行的工具调用 */
    public void denyHITL() {
        hitlManager.denyHITL();
    }

    /** 是否有待审批的工具调用 */
    public boolean hasPendingHITL() {
        return hitlManager.hasPendingHITL();
    }

    /** 获取模型最大上下文窗口 token 数 */
    public int getMaxContextTokens() {
        return client.getMaxContextTokens();
    }

    /** 运行时切换模型（热更新） */
    public void setModel(String model) {
        client.setModel(model);
    }

    /** 运行时切换推理强度（热更新） */
    public void setReasoningEffort(String reasoningEffort) {
        client.setReasoningEffort(reasoningEffort);
    }

    /** 手动触发上下文折叠（/compact 命令） */
    public void compactNow() throws IOException {
        List<ChatMessage> messages = ctx.buildMessages();
        List<ChatMessage> folded = ContextFolding.foldKeepLast(messages, 20, client);
        if (folded.size() < ctx.size()) {
            ctx.compact(folded);
            output.onLog(LogLevel.INFO, "[compact] " + ctx.size() + " 条消息（保留近20条，较早消息已摘要）");
        } else {
            output.onLog(LogLevel.INFO, "[compact] 无需折叠（总消息数 ≤ 20）");
        }
    }

    /**
     * 用户主动中断：设置中断标志、中止当前 HTTP 流式请求、取消正在执行的工具。
     */
    public void requestUserAbort() {
        doAbort();
    }

    /**
     * 检查用户是否已请求中断（供 AgentLoopController 接口实现）。
     */
    @Override
    public boolean isAbortRequested() {
        return userAbortRequested;
    }

    /** 重置用户中断标志（每回合开始时调用） */
    public void resetUserAbort() {
        userAbortRequested = false;
    }

    public void setListener(AgentLoopListener listener) {
        this.listener = listener != null ? listener : NoOpAgentLoopListener.INSTANCE;
    }

    public void setOutput(AgentOutput output) {
        this.output = output != null ? output : AgentOutput.NOOP;
    }

    /**
     * 设置外部中断源 —— 子代理通过此方法绑定父级的 AgentLoopController。
     * <p>
     * 子代理的主循环和流式调用中会同步检查父级的 isAbortRequested()，
     * 一旦父级请求中断，子代理会立即设置自身的 userAbortRequested 并中止 HTTP 流。
     * </p>
     *
     * @param parentController 父级的 AgentLoopController（可 null 表示无父级）
     */
    public void setExternalAbortSource(AgentLoopController parentController) {
        if (parentController == null) {
            this.externalAbortSource = null;
            return;
        }
        // 捕获父级引用，创建轻量 Runnable：检查父级中断状态，若已中断则同步到本循环
        this.externalAbortSource = () -> {
            if (parentController.isAbortRequested() && !userAbortRequested) {
                doAbort();
                log.info("[loop] 检测到父级中断信号，子代理同步中止");
            }
        };
    }

    // ==================== AgentLoopController 实现 ====================

    @Override
    public void requestStop() {
        doAbort();
        log.info("[loop] 工具请求停止推理循环");
    }

    /**
     * 统一的中断实现：设置标志、中止流式请求、取消工具 Future。
     */
    private void doAbort() {
        userAbortRequested = true;
        client.abortStream();
        // 取消正在执行的工具 Future
        CompletableFuture<ChatMessage>[] futures = activeToolFutures;
        if (futures != null) {
            for (CompletableFuture<ChatMessage> f : futures) {
                if (f != null && !f.isDone()) {
                    f.cancel(true);
                }
            }
        }
    }

    @Override
    public void finish(String content) {
        if (content == null || content.isBlank()) {
            // 尝试从上下文获取最后一条 assistant 回复作为回退
            String lastAssistant = ctx.getLastAssistantContent();
            if (lastAssistant != null && !lastAssistant.isBlank()) {
                content = lastAssistant;
                log.info("[loop] finish content 为空，使用最后一条 assistant 回复作为回退");
            } else {
                content = "(completed)";
                log.info("[loop] finish content 为空且无 assistant 回复可回退，使用默认值");
            }
        }
        this.finishContent = content;
        client.abortStream();
        log.info("[loop] 工具请求完成任务，即将退出循环");
    }

    @Override
    public void injectUserMessage(String message) {
        if (message == null || message.isEmpty()) return;
        ctx.addUser("[系统注入] " + message);
        log.info("[loop] 工具注入用户消息: {}...",
                message.length() > 80 ? message.substring(0, 80) + "..." : message);
    }

    @Override
    public <T>T getToolRegistry() {
        return (T) registry;
    }

    // ==================== 内部辅助方法 ====================

    /** 安全调用 output 方法，异常记录 warn 日志（用于 onLog/onError/onToolCall/onToolResult 等） */
    private void safeOutput(String tag, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("[{}] output异常: {}", tag, e.getMessage());
        }
    }

    /** 安全调用 output 方法，异常记录 debug 日志（用于 SSE delta 回调，断开是预期行为） */
    private void safeOutputDebug(String tag, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.debug("[{}] output异常(SSE可能已断开): {}", tag, e.getMessage());
        }
    }

    /** 安全调用 listener 方法，异常记录 warn 日志 */
    private void safeListener(String tag, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("[{}] listener异常: {}", tag, e.getMessage());
        }
    }

    private static ChatMessage toolResult(String id, String result) {
        return ChatMessage.tool(id, result);
    }

    public ONode refreshTools() {
        registry.refresh();
        return registry.toOpenAiTools();
    }

    /**
     * 获取工具注册表实例。
     */
    public ToolRegistry getToolRegistryInstance() {
        return registry;
    }

    private String buildToolInstructions() {
        return """
                # Agent4j AI 工具速查
                ## 调用规约
                - 编辑文件用 `edit`（SEARCH/REPLACE，search 必须唯一，先 `read` 确认内容）
                - 批量编辑用 `edit`（单次调用多 edits）
                - 不确定文件位置时用 `glob`/`grep`
                - 需要构建/测试时用 `bash`
                - 结束对话**必须**调用 `finish`，纯文本回复不会退出循环
                
                ---
                
                ## 工具清单
                
                ### finish — 结束对话
                | 参数 | 必填 | 说明 |
                |------|------|------|
                | content | 否 | 最终回答内容（为空则从上下文回填）|
                
                ---
                
                ### task — 创建子代理
                子代理有独立上下文，继承父工具集（排除 task/ask_choice/finish），不可递归创建。
                | 参数 | 必填 | 说明 |
                |------|------|------|
                | name | 是 | 任务名称 |
                | arguments | 否 | 任务详情/初始指令 |
                | systemPrompt | 否 | 系统提示词覆盖，为空自动生成 |
                
                ---
                
                ### ask_choice — 用户选择菜单（2-6 选项）
                | 参数 | 必填 | 说明 |
                |------|------|------|
                | question | 是 | 问题 |
                | options | 是 | 选项列表，支持字符串或 `{title, summary}` |
                | allowCustom | 否 | 是否允许自定义输入，默认 false |
                
                
                ### workflow_create_dag — 创建工作流 DAG
                创建复杂工作流（有向无环图），支持条件分支和并行执行。
                | 参数 | 必填 | 说明 |
                |------|------|------|
                | title | 是 | 工作流标题 |
                | description | 是 | 工作流详细描述 |
                | nodesJson | 是 | 节点数组 JSON：`[{id, description, type?, condition?}]` |
                | edgesJson | 是 | 边数组 JSON：`[{from, to, type?}]` |
                
                ---
                
                ### workflow_visualize — 查看工作流
                可视化查看当前会话的工作流结构（节点列表、依赖关系和执行状态）。
                | 参数 | 必填 | 说明 |
                |------|------|------|
                | sessionId | 否 | 会话 ID，留空自动获取 |
                
                ---
                
                ### workflow_mark_node — 标记节点完成
                标记当前会话工作流中的某个节点为"已完成"。
                每完成一个节点后调用此工具，如果所有节点都已完成，工作流自动标记为已完成。
                | 参数 | 必填 | 说明 |
                |------|------|------|
                | nodeId | 是 | 已完成的节点ID（如 'n1', 'n2'）|
                | result | 否 | 该节点的执行结果摘要 |
                | sessionId | 否 | 会话 ID，留空自动获取 |
                
                ---
                
                ### workspace_list — 列出工作区条目（只读）
                | 参数 | 必填 | 说明 |
                |------|------|------|
                | prefix | 否 | key 前缀过滤，为空列出全部 |
                | scope | 否 | 预留 |
                
                ---
                
                ### workspace_read — 读取工作区条目（只读）
                优先 KV → 文档 → NOT_FOUND（附相似 key 提示）
                | 参数 | 必填 | 说明 |
                |------|------|------|
                | key | 是 | 条目路径 |
                | scope | 否 | 预留 |
                
                ---
                
                ### workspace_write — 写入工作区条目
                KV 模式：传 value；文档模式：传 content（无 value 时）。
                | 参数 | 必填 | 说明 |
                |------|------|------|
                | key | 是 | 条目路径 |
                | value | 否 | KV 值（与 content 二选一）|
                | content | 否 | 文档内容（与 value 二选一）|
                | type | 否 | MIME 类型，默认 text/plain |
                | scope | 否 | 预留 |
                
                ---
                
                ### goal_mark_step — 标记目标步骤完成
                | 参数 | 必填 | 说明 |
                |------|------|------|
                | stepIndex | 是 | 步骤序号（从 1 开始）|
                | output | 否 | 执行结果摘要 |
                | sessionId | 否 | 留空自动获取 |
                
                ---
                
                ### java_source — 查找 Java 源码（I/O 密集，每类只调一次）
                | 参数 | 必填 | 说明 |
                |------|------|------|
                | className | 是 | 全限定类名，如 `com.example.MyClass` |
                | jarKeyword | 是 | jar 关键字，简短精确，如 `spring-core` |
                
                ---
                
                ### vision_recognize — 图片识别
                需在 `~/.agent4j/config.json` 配置 vision（baseUrl, apiKey, model）。
                | 参数 | 必填 | 说明 |
                |------|------|------|
                | image | 是 | 图片路径、URL 或 Base64 Data URI |
                | prompt | 否 | 识别提示词 |
                
                ---
                
                ### read — 读取文件（只读）
                | 参数 | 必填 | 说明 |
                |------|------|------|
                | file_path | 是 | 相对路径，`.` 表示根目录 |
                | offset | 否 | 起始行号（从 1 开始）|
                | limit | 否 | 最大行数（单次 128KB 保护）|
                
                ### write — 写入/覆盖文件
                | 参数 | 必填 | 说明 |
                |------|------|------|
                | file_path | 是 | 相对路径 |
                | content | 是 | 完整内容 |
                
                ### edit — 精准文本替换（原子性：全成功或全回滚）
                | 参数 | 必填 | 说明 |
                |------|------|------|
                | file_path | 是 | 相对路径 |
                | edits | 是 | `[{old_str, old_StrStartLine, new_str, replace_all?}]` |
                
                ### glob — 通配符搜索文件（只读）
                | 参数 | 必填 | 说明 |
                |------|------|------|
                | pattern | 是 | 如 `**/*.java` |
                | path | 是 | 目录路径 |
                
                ### grep — 递归搜索内容（只读，支持正则）
                | 参数 | 必填 | 说明 |
                |------|------|------|
                | pattern | 是 | 正则表达式 |
                | path | 是 | 目录路径 |
                | include | 否 | 文件模式，如 `*.java` |
                
                ### ls — 列出目录（只读）
                | 参数 | 必填 | 说明 |
                |------|------|------|
                | path | 是 | 目录路径 |
                | recursive | 否 | 是否递归 |
                | show_hidden | 否 | 是否显示隐藏文件 |
                
                ### bash — 执行 Shell 命令
                | 参数 | 必填 | 说明 |
                |------|------|------|
                | command | 是 | 指令 |
                | max_output_chars | 否 | 默认 64000 |
                | timeout | 否 | 毫秒，默认 120000 |
                
                ---
                
                ### skilllist — 列出可用技能（只读，无参数）
                ### skillread — 读取技能说明（只读）
                | 参数 | 必填 | 说明 |
                |------|------|------|
                | name | 是 | 技能路径标识 |
                
                ### skillrefresh — 刷新技能列表（无参数）
                
                ---
                
                ### webfetch — 获取网页（只读）
                | 参数 | 必填 | 说明 |
                |------|------|------|
                | url | 是 | 完整 URL |
                | format | 否 | markdown/text/html，默认 markdown |
                | timeoutMs | 否 | 最大 120000 |
                
                ### codesearch — 代码搜索（只读）
                | 参数 | 必填 | 说明 |
                |------|------|------|
                | query | 是 | 搜索词 |
                | tokensNum | 否 | 1000-50000，默认 5000 |
                
                ---
                
                ### lsp_* — LSP 语言服务（需先启用）
                定义跳转/引用查找/悬停提示/诊断/文档符号/调用层次。
                
                ---
                
                ## 动态工具
                - **MCP** — 通过 `/mcp` 命令管理，工具名带服务器前缀
                - **OpenAPI** — 自动将 REST API 转为工具
                - **Plugin** — `~/.agent4j/plugin/` 下含 `tool.json` + `skill.md` 的目录自动注册

                """;
    }

    // ==================== 主入口：run() ====================

    /**
     * 执行一个用户回合，返回最终的 assistant content。
     * <p>
     * 用户消息会被追加到上下文，工具调用结果也会累积。
     * 下一个回合调用时，上下文已包含上一轮的全部消息。
     * </p>
     */
    public String run(UserMessage userMessage) throws IOException {
        // ---- 标记运行中，防止巡检线程并发冲突 ----
        running.set(true);
        try {
            return doRun(userMessage);
        } finally {
            running.set(false);
        }
    }

    private String doRun(UserMessage userMessage) throws IOException {
        // ---- 根据模型多模态支持清洗用户消息 ----
        userMessage = UserMessageSanitizer.sanitize(userMessage, client.getModel());
        
        // ---- HITL 恢复：用户已审批 / 拒绝 ----
        if (hitlManager.getState() == HitlState.APPROVED) {
            hitlManager.resetState();
            if (hitlManager.hasSandboxPending()) {
                return resumeAfterSandboxHITL(true);
            }
            return resumeAfterHITL(true);
        }
        if (hitlManager.getState() == HitlState.DENIED) {
            hitlManager.resetState();
            if (hitlManager.hasSandboxPending()) {
                return resumeAfterSandboxHITL(false);
            }
            return resumeAfterHITL(false);
        }

        // ---- 追加用户消息 ----
        if (userMessage != null && userMessage.hasContent()) {
            ctx.addUser(userMessage);
        }

        // ---- 每回合初始化 ----
        reasonBreaker.reset();
        resetUserAbort();

        // ---- 进入统一的主推理循环（含自动重试闭环） ----
        return runWithAutoRetry();
    }

    private String runWithAutoRetry() throws IOException {
        return mainLoop();
    }

    // ==================== 统一主推理循环 ====================

    /**
     * 在工具结果写入上下文后继续推理循环。
     * 被 run() / resumeAfterHITL / resumeAfterSandboxHITL 复用。
     * 消除了原 continueConversationLoop() 与 run() 主循环体的重复代码。
     *
     * @return 最终的 assistant content
     */
    private String mainLoop() throws IOException {
        int noToolCallStreak = 0;
        int selfCorrectionAttempts = 0;
        for (int step = 0; ; step++) {
            // ---- 0. 同步外部中断源（子代理检查父级 abort 状态）----
            Runnable extSource = externalAbortSource;
            if (extSource != null) {
                extSource.run();
            }

            // ---- 0.1. 检查用户中断（标志位 + 线程中断，覆盖直接 cancel future 的场景）----
            if (userAbortRequested || Thread.currentThread().isInterrupted()) {
                if (!userAbortRequested) {
                    userAbortRequested = true;
                }
                logAbort();
                String lastContent = ctx.getLastAssistantContent();
                return lastContent != null && !lastContent.isEmpty() ? lastContent : "⏹️ 已停止生成";
            }

            // ---- 0.5. 动态刷新工具列表 ----
            ONode tools = refreshTools();

            // ---- 1. 消息准备：构建 + Healing + 折叠 ----
            PreparedMessages prepared = prepareMessages(step);
            List<ChatMessage> messages = prepared.messages();

            // ---- 2. 流式调用 LLM ----
            StreamResult sr = streamLLM(messages, tools);

            // ---- 2.05. 同步外部中断源（streamLLM 期间父级可能已中断）----
            Runnable extSource2 = externalAbortSource;
            if (extSource2 != null) {
                extSource2.run();
            }

            // ---- 2.1 用户中断（标志位 + 线程中断）----
            if (userAbortRequested || Thread.currentThread().isInterrupted()) {
                safeOutput("abort", () -> output.onLog(LogLevel.INFO, "[abort] 用户请求中断（streamLLM 后检测），停止推理循环"));
                return handleAbortAfterStream(sr);
            }

            // ---- 3. 流式错误恢复 ----
            if (sr.error()) {
                throw new IOException("[stream] API error during streaming");
            }

            safeOutputDebug("contentComplete", output::onContentComplete);

            // ---- 推理断路器 ----
            if (sr.loopAborted()) {
                ctx.addUser("[ReasonBreaker] 检测到思考循环，已提前终止本轮推理。请停止当前思路，尝试不同的方法。");
                continue;
            }

            // ---- 4. 从 reasoning 中回收丢失的工具调用 ----
            ONode toolCalls = scavengeToolCalls(sr.toolCalls(), sr.reasoningContent(), sr.content());
            boolean hasToolCalls = toolCalls != null && toolCalls.isArray() && !toolCalls.getArray().isEmpty();

            // ---- 5. 无 tool calls → 不是终止信号，是需纠正的状态 ----
            if (!hasToolCalls) {
                ctx.addAssistant(sr.content(), null, sr.reasoningContent());
                noToolCallStreak++;
                log.warn("[loop] 第 {} 次无工具调用，累积无工具轮数: {}", step, noToolCallStreak);

                if (noToolCallStreak >= 3) {
                    log.warn("[loop] 连续 {} 轮无工具调用，降级终止", noToolCallStreak);
                    int streak = noToolCallStreak;
                    safeOutput("noToolMax", () -> output.onLog(LogLevel.WARN,
                            "[loop] 连续 " + streak + " 轮无工具调用，降级终止"));
                    String degraded = ctx.getLastAssistantContent();
                    return degraded != null && !degraded.isEmpty() ? degraded : "任务中断，未完成（已收集部分结果）";
                }

                // 渐进式轻推
                ctx.addUser(FinishTool.TIPS);
                continue;
            }

            // ---- 调用了工具 → 重置无工具计数 ----
            noToolCallStreak = 0;

            // ---- HITL 拦截（finish/ask_choice 等免审批工具直接放行） ----
            if (hitlManager.isHitlMode()) {
                String hitlPrompt = hitlManager.interceptForHITL(toolCalls, sr.content(), sr.reasoningContent(), output);
                if (hitlPrompt != null) {
                    return hitlPrompt;
                }
                // 免审批工具：跳过拦截，继续执行
            }

            // ---- 6. 并行执行工具调用 ----
            ToolExecutionResult ter = executeToolCalls(toolCalls);

            // ---- 6.1 沙箱越界 HITL ----
            if (hitlManager.getState() == HitlState.PENDING && hitlManager.hasSandboxPending()) {
                hitlManager.storeSandboxContent(sr.content(), sr.reasoningContent());
                return hitlManager.interceptForSandboxHITL(output);
            }

            // ---- 7. 写入 assistant 消息 + 工具结果 ----
            ctx.addAssistant(sr.content(), ter.tcList(), sr.reasoningContent());
            for (ChatMessage tr : ter.toolResults()) {
                ctx.addToolResult(tr.getToolCallId(), tr.getContent());
            }

            // ---- 7.5. 唯一正常退出：finish 工具被调用 ----
            if (finishContent != null) {
                String result = finishContent;
                finishContent = null;
                safeOutput("finish", () -> output.onLog(LogLevel.DEBUG, result));
                return result;
            }

            // ---- 8. Self-Correction ----
            int updated = handleSelfCorrection(ter.toolResults(), ter.anySuppressed(), selfCorrectionAttempts);
            if (updated < 0) {
                String fallback = "所有工具调用均被风暴断路器抑制，无法继续执行。请换用其他方式完成任务。";
                ctx.addAssistant(fallback, null, null);
                return fallback;
            }
            selfCorrectionAttempts = updated;
        }

    }

    // ==================== HITL 恢复 ====================

    /**
     * HITL 恢复：用户审批/拒绝后，继续执行或跳过工具调用。
     */
    private String resumeAfterHITL(boolean approved) throws IOException {
        HitlManager.PendingHITLState state = hitlManager.drainPendingHITL();

        if (!approved) {
            String denyMsg = "工具调用已被用户拒绝。";
            ctx.addAssistant(denyMsg, null, null);
            return denyMsg;
        }

        // 用户批准：先写入 assistant 消息
        ctx.addAssistant(state.content(), state.tcList(), state.reasoningContent());

        // 并行执行暂存的工具调用
        ToolExecutionResult ter = executeToolCalls(state.toolCalls());

        // 沙箱越界 HITL：暂停并等待用户审批
        if (hitlManager.getState() == HitlState.PENDING && hitlManager.hasSandboxPending()) {
            hitlManager.storeSandboxContent(state.content(), state.reasoningContent());
            return hitlManager.interceptForSandboxHITL(output);
        }

        // 写入工具结果
        for (ChatMessage tr : ter.toolResults()) {
            ctx.addToolResult(tr.getToolCallId(), tr.getContent());
        }

        // 进入统一推理循环
        return mainLoop();
    }

    /**
     * 沙箱越界 HITL 恢复：审批通过后以沙箱旁路模式重放工具调用。
     */
    private String resumeAfterSandboxHITL(boolean approved) throws IOException {
        HitlManager.PendingSandboxState state = hitlManager.drainSandboxHITL();

        if (!approved) {
            // 用户拒绝
            List<ToolCallEntry> tcList = parseToolCallsFromONode(state.toolCalls());
            ctx.addAssistant(state.content(), tcList, state.reasoningContent());
            String denyMsg = "沙箱越界已被用户拒绝。";
            ctx.addAssistant(denyMsg, null, null);
            return denyMsg;
        }

        // 用户批准：写入 assistant 消息，以沙箱旁路模式执行工具
        List<ToolCallEntry> tcList = parseToolCallsFromONode(state.toolCalls());
        ctx.addAssistant(state.content(), tcList, state.reasoningContent());

        reasonBreaker.reset();
        resetUserAbort();

        ToolExecutionResult initialTer = executeToolCalls(state.toolCalls());

        // 写入工具结果
        for (ChatMessage tr : initialTer.toolResults()) {
            ctx.addToolResult(tr.getToolCallId(), tr.getContent());
        }

        // Self-Correction 检查
        int scAttempts = handleSelfCorrection(initialTer.toolResults(), initialTer.anySuppressed(), 0);
        if (scAttempts < 0) {
            String fallback = "所有工具调用均被风暴断路器抑制，无法继续执行。";
            ctx.addAssistant(fallback, null, null);
            return fallback;
        }

        return mainLoop();
    }

    /**
     * 从 ONode 解析工具调用列表（不含暂存副作用）。
     */
    private static List<ToolCallEntry> parseToolCallsFromONode(ONode toolCalls) {
        List<ToolCallEntry> tcList = new ArrayList<>();
        if (toolCalls == null || !toolCalls.isArray()) return tcList;
        for (ONode tc : toolCalls.getArray()) {
            String tcId = tc.get("id").getString();
            ONode func = tc.get("function");
            String tcName = func.get("name").getString();
            if (tcName == null || tcName.isEmpty()) continue;
            String tcArgs = func.get("arguments").getString();
            if (tcArgs == null) tcArgs = "{}";
            tcList.add(new ToolCallEntry(tcId, tcName, tcArgs));
        }
        return tcList;
    }

    // ==================== 步骤 1: 消息准备 ====================

    private PreparedMessages prepareMessages(int step) throws IOException {
        List<ChatMessage> messages = ctx.buildMessages();
        MessageHealer.HealResult healResult = MessageHealer.heal(messages);
        messages = healResult.messages();

        // 预检：token 数接近上下文窗口 80% 时折叠
        boolean foldedThisStep = false;
        int maxCtx = client.getMaxContextTokens();
        int tokenThreshold = (int) (maxCtx * 0.8);
        int estimatedPromptTokens = lastPromptTokens > 0
                ? lastPromptTokens
                : ContextFolding.estimateChars(messages) / 2;
        boolean needFold = estimatedPromptTokens > tokenThreshold;
        if (needFold) {
            try {
                output.onLog(LogLevel.INFO, "[fold] 触发折叠: estimatedTokens=" + estimatedPromptTokens
                        + " threshold=" + tokenThreshold + " maxCtx=" + maxCtx);
            } catch (Exception e) {
                log.warn("[fold] output.onLog异常: {}", e.getMessage());
            }
            messages = ContextFolding.fold(messages, maxTotalChars(), keepTailChars(), client);
            if (messages.size() < ctx.size()) {
                ctx.compact(messages);
                foldedThisStep = true;
                lastPromptTokens = 0;
            }
        }

        try {
            output.onLog(LogLevel.DEBUG, "step=" + step + " messages.size=" + messages.size()
                    + " lastPromptTokens=" + lastPromptTokens + " threshold=" + tokenThreshold);
        } catch (Exception e) {
            log.warn("[prepare] output.onLog异常: {}", e.getMessage());
        }

        // 注入动态工具使用指引
        String instr = buildToolInstructions();
        if (!instr.isEmpty()) {
            List<ChatMessage> withInstr = new ArrayList<>(messages.size() + 1);
            withInstr.add(messages.get(0)); // system prompt
            withInstr.add(ChatMessage.ofUser(instr));
            withInstr.addAll(messages.subList(1, messages.size()));
            messages = withInstr;
        }

        return new PreparedMessages(messages, foldedThisStep);
    }

    // ==================== 步骤 2: 流式调用 LLM ====================

    private StreamResult streamLLM(List<ChatMessage> messages, ONode tools) {
        final StringBuilder contentBuf = new StringBuilder();
        final StringBuilder reasoningBuf = new StringBuilder();
        final AtomicReference<ONode> streamedTcs = new AtomicReference<>();
        final CountDownLatch streamLatch = new CountDownLatch(1);
        final AtomicBoolean streamError = new AtomicBoolean(false);
        final AtomicBoolean loopAborted = new AtomicBoolean(false);
        final String[] loopSnapshot = {null};
        final int[] lastCheckLen = {0};
        // 捕获外部中断源引用，避免回调内重复 volatile 读
        final Runnable capturedExtAbort = externalAbortSource;

        client.chatStream(messages, tools, new ModelClient.StreamCallback() {
            @Override
            public void onReasoningDelta(String token) {
                // 同步外部中断源（子代理检查父级 abort）
                if (capturedExtAbort != null) capturedExtAbort.run();
                if (loopAborted.get() || userAbortRequested || Thread.currentThread().isInterrupted()) return;
                reasoningBuf.append(token);
                safeOutputDebug("reasoningDelta", () -> output.onReasoningDelta(token));
                // 流式增量检测：每 500 字符检查一次思考循环
                int newLen = reasoningBuf.length();
                if (newLen - lastCheckLen[0] >= 500) {
                    lastCheckLen[0] = newLen;
                    ReasonBreaker.LoopResult lr = reasonBreaker.analyze(reasoningBuf.toString());
                    if (lr.looping) {
                        loopSnapshot[0] = reasoningBuf.toString();
                        loopAborted.set(true);
                        safeOutput("ReasonBreaker", () -> output.onLog(LogLevel.WARN, "[ReasonBreaker] " + lr.toWarning()));
                        client.abortStream();
                        streamLatch.countDown();
                    }
                }
            }

            @Override
            public void onContentDelta(String token) {
                contentBuf.append(token);
                safeOutputDebug("contentDelta", () -> output.onContentDelta(token));
            }

            @Override
            public void onToolCalls(ONode tcs) {
                streamedTcs.set(tcs);
            }

            @Override
            public void onUsage(int promptTokens, int completionTokens, int totalTokens,
                                int cacheHit, int cacheMiss) {
                lastPromptTokens = promptTokens;
                if (sessionService != null) {
                    sessionService.updateLastPromptTokens(promptTokens);
                }
                String currentModel = client.getModel();
                safeListener("usage", () -> listener.onUsage(currentModel, promptTokens, completionTokens, totalTokens, cacheHit, cacheMiss));
                safeOutputDebug("usage", () -> output.onUsage(currentModel, promptTokens, completionTokens, totalTokens, cacheHit, cacheMiss));
            }

            @Override
            public void onDone() {
                streamLatch.countDown();
            }

            @Override
            public void onError(String err) {
                streamError.set(true);
                safeOutput("streamError", () -> output.onError("[stream error] " + err));
                streamLatch.countDown();
            }
        });

        // 等待流结束（带超时保护，防止 HTTP 流永不结束导致线程永久挂起）
        try {
            boolean finished = streamLatch.await(DEFAULT_STREAM_LATCH_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!finished) {
                log.error("[stream] LLM 流式响应超时（{}s），主动终止", DEFAULT_STREAM_LATCH_TIMEOUT_SEC);
                client.abortStream();
                return new StreamResult(null, null, null, true);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new StreamResult(null, null, null, true);
        }

        // 同步外部中断源（stream 期间父级可能已中断）
        if (capturedExtAbort != null) capturedExtAbort.run();

        if (userAbortRequested) {
            String content = !contentBuf.isEmpty() ? contentBuf.toString() : null;
            String reasoningContent = !reasoningBuf.isEmpty() ? reasoningBuf.toString() : null;
            return new StreamResult(content, reasoningContent, streamedTcs.get(), false);
        }
        if (streamError.get()) {
            return new StreamResult(null, null, null, true);
        }
        if (loopAborted.get()) {
            String reasoning = loopSnapshot[0] != null ? loopSnapshot[0]
                    : (!reasoningBuf.isEmpty() ? reasoningBuf.toString() : null);
            return new StreamResult(null, reasoning, streamedTcs.get(), false, true);
        }
        String content = !contentBuf.isEmpty() ? contentBuf.toString() : null;
        String reasoningContent = !reasoningBuf.isEmpty() ? reasoningBuf.toString() : null;
        return new StreamResult(content, reasoningContent, streamedTcs.get(), false);
    }

    // ==================== 步骤 4: Scavenger 回收 ====================

    private ONode scavengeToolCalls(ONode toolCalls, String reasoningContent, String content) {
        boolean hasToolCalls = toolCalls != null && toolCalls.isArray() && !toolCalls.getArray().isEmpty();
        if (hasToolCalls) return toolCalls;
        if (reasoningContent == null && content == null) return toolCalls;

        List<Scavenger.ToolCall> scavenged = Scavenger.scavenge(reasoningContent, content, new ArrayList<>());
        if (scavenged.isEmpty()) return toolCalls;

        ONode fakeTcArray = ONode.ofJson("[]").asArray();
        for (Scavenger.ToolCall tc : scavenged) {
            ONode tcn = fakeTcArray.addNew();
            String idSuffix = tc.id() != null ? ""
                    : "_" + Integer.toHexString(tc.arguments().hashCode())
                    + "_" + System.nanoTime();
            tcn.set("id", tc.id() != null ? tc.id() : "scavenged_" + tc.name() + idSuffix);
            tcn.set("type", "function");
            ONode fn = tcn.getOrNew("function");
            fn.set("name", tc.name());
            fn.set("arguments", tc.arguments());
        }
        return fakeTcArray;
    }

    // ==================== 步骤 6: 工具执行 ====================

    private ToolExecutionResult executeToolCalls(ONode toolCalls) {
        if (toolCalls == null){
            return new ToolExecutionResult(Collections.emptyList(),Collections.emptyList(),false);
        }

        List<ONode> tcArray = toolCalls.getArray();

        // 1. 解析并过滤工具调用
        ParsedToolCalls parsed = parseAndFilterToolCalls(tcArray);
        List<ONode> finalTcArray = parsed.nodeList();

        TaskTool.clearUsageCollector();

        // 2. 异步并行分发
        DispatchResult dispatch = dispatchToolCallsAsync(finalTcArray);

        // 3. 等待并收集结果
        List<ChatMessage> toolResults = collectToolResults(dispatch.futures(), finalTcArray);

        // 4. 沙箱越界 HITL
        HitlRequiredException hitlEx = dispatch.hitlRef().get();
        if (hitlEx != null) {
            hitlManager.setSandboxPending(toolCalls, hitlEx.getDetails());
            safeOutput("hitl", () -> output.onLog(LogLevel.WARN,
                    "[hitl] 沙箱越界触发强制审批: " + hitlEx.getDetails()));
        }

        // 5. 收集子代理 token 用量
        if (sessionService != null) {
            var subUsage = TaskTool.drainUsageCollector();
            for (var ur : subUsage) {
                sessionService.addUsage(ur.model(),
                        (int) ur.prompt(), (int) ur.completion(),
                        (int) ur.cacheHit(), (int) ur.cacheMiss());
            }
        }

        return new ToolExecutionResult(parsed.tcList(), toolResults, dispatch.anySuppressed().get());
    }

    private record ParsedToolCalls(List<ToolCallEntry> tcList, List<ONode> nodeList) {}

    /**
     * 解析并过滤工具调用列表，同时触发 onToolCall 回调。
     *
     * @return 包含过滤后的 ToolCallEntry 列表和对应 ONode 列表的记录
     */
    private ParsedToolCalls parseAndFilterToolCalls(List<ONode> tcArray) {
        List<ToolCallEntry> tcList = new ArrayList<>();
        List<ONode> filteredTcList = new ArrayList<>();
        for (ONode tc : tcArray) {
            String tcId = tc.get("id").getString();
            ONode func = tc.get("function");
            String tcName = func.get("name").getString();
            if (tcName == null || tcName.isEmpty()) {
                safeOutput("tool", () -> output.onLog(LogLevel.WARN,
                        "跳过无效 tool call: name=" + tcName + " id=" + tcId));
                continue;
            }
            String tcArgs = func.get("arguments").getString();
            if (tcArgs == null) tcArgs = "{}";

            tcList.add(new ToolCallEntry(tcId, tcName, tcArgs));
            filteredTcList.add(tc);

            final String finalTcName = tcName;
            final String finalTcArgs = tcArgs;
            safeListener("toolCall", () -> listener.onToolCall(finalTcName, finalTcArgs));
            safeOutputDebug("toolCall", () -> output.onToolCall(finalTcName, finalTcArgs));
        }
        return new ParsedToolCalls(tcList, filteredTcList);
    }

    /**
     * 异步并行分发所有工具调用，返回 Future 数组和共享状态引用。
     */
    private record DispatchResult(CompletableFuture<ChatMessage>[] futures,
                                  AtomicBoolean anySuppressed,
                                  AtomicReference<HitlRequiredException> hitlRef) {}

    private DispatchResult dispatchToolCallsAsync(List<ONode> tcArray) {
        int tcCount = tcArray.size();
        @SuppressWarnings("unchecked")
        CompletableFuture<ChatMessage>[] futures = new CompletableFuture[tcCount];
        final AtomicBoolean anySuppressed = new AtomicBoolean(false);
        final AtomicReference<HitlRequiredException> hitlRef = new AtomicReference<>(null);
        final AgentOutput capturedOutput = this.output;
        for (int i = 0; i < tcCount; i++) {
            final int idx = i;
            futures[i] = CompletableFuture.supplyAsync(() -> {
                // 用户已中断 → 立即返回，不执行工具
                if (userAbortRequested) {
                    ONode tc = tcArray.get(idx);
                    String tcId = tc.get("id").getString();
                    return toolResult(tcId,
                            "{\"error\":\"用户已中断\",\"aborted\":true}");
                }

                if (capturedOutput != null) {
                    TaskTool.setCurrentOutput(capturedOutput);
                }
                try {
                    ONode tc = tcArray.get(idx);
                    ToolCall toolCall = getToolCall(tc);
                    FunctionTool fc = registry.get(toolCall.getName());
                    if (fc == null) {
                        String result = "工具不存在";
                        safeListener("toolResult", () -> listener.onToolResult(toolCall.getName(), result));
                        safeOutputDebug("toolResult", () -> output.onToolResult(toolCall.getName(), result));
                        return toolResult(toolCall.getId(), "工具不存在");
                    }
                    //收集拦截器
                    ToolContext.setCurrentController(AgentLoop.this);
                    HashMap<String, Object> extraMap = new HashMap<>();
                    extraMap.put("ctx", new ToolContext(
                            new HashMap<>(),
                            registry.getWorkspace().toAbsolutePath().toString(),
                            this.getSessionId()
                    ));

                    ToolRequest req = new ToolRequest(null,extraMap, toolCall.getArguments());
                    try {
                        ToolResult call = fc.call(req.getArgs());
                        String result = call.getContent();
                        safeListener("toolResult", () -> listener.onToolResult(toolCall.getName(), result));
                        safeOutputDebug("toolResult", () -> output.onToolResult(toolCall.getName(), result));
                        return toolResult(toolCall.getId(), result);
                    } catch (Throwable e) {
                        String result = e.getMessage();
                        safeListener("toolResult", () -> listener.onToolResult(toolCall.getName(), result));
                        safeOutputDebug("toolResult", () -> output.onToolResult(toolCall.getName(), result));
                        return toolResult(toolCall.getId(), result);
                    }
                } finally {
                    TaskTool.clearCurrentOutput();
                }
            });
        }
        return new DispatchResult(futures, anySuppressed, hitlRef);
    }

    /**
     * 等待所有工具 Future 完成（带超时保护），收集结果。
     * 如果用户请求中断，立即取消未完成的 Future 并返回。
     */
    private List<ChatMessage> collectToolResults(CompletableFuture<ChatMessage>[] futures,
                                                 List<ONode> tcArray) {
        // 保存活跃 futures 引用，供 requestUserAbort() 取消
        this.activeToolFutures = futures;
        try {
            // 在等待之前检查用户是否已请求中断
            if (userAbortRequested) {
                cancelAllFutures(futures);
                return buildAbortedResults(futures, tcArray);
            }

            CompletableFuture.allOf(futures).get(toolTimeoutSec(), TimeUnit.SECONDS);

            // allOf 完成后，再次检查是否被用户中断
            if (userAbortRequested) {
                cancelAllFutures(futures);
                return buildAbortedResults(futures, tcArray);
            }
        } catch (TimeoutException e) {
            safeOutput("toolTimeout", () -> output.onLog(LogLevel.WARN,
                    "[tool] 工具执行超时（" + toolTimeoutSec() + "s），取消未完成的调用"));
            cancelAllFutures(futures);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cancelAllFutures(futures);
            return buildAbortedResults(futures, tcArray);
        } catch (CancellationException e) {
            // allOf 被取消（可能是用户中断导致所有 future 被取消）
            safeOutput("toolAborted", () -> output.onLog(LogLevel.INFO,
                    "[tool] 工具执行被用户中断"));
            return buildAbortedResults(futures, tcArray);
        } catch (ExecutionException e) {
            log.debug("[tool] 工具执行异常: {}", e.getMessage());
        } finally {
            this.activeToolFutures = null;
        }

        List<ChatMessage> toolResults = new ArrayList<>();
        for (int i = 0; i < futures.length; i++) {
            CompletableFuture<ChatMessage> f = futures[i];
            try {
                toolResults.add(f.get());
            } catch (CancellationException e) {
                ONode tc = tcArray.get(i);
                String tcId = tc.get("id").getString();
                toolResults.add(toolResult(tcId,
                        "{\"error\":\"工具执行超时（" + toolTimeoutSec()
                                + "s）\",\"rejectedReason\":\"timeout\"}"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                toolResults.add(toolResult("?", "[ERROR] Interrupted"));
            } catch (ExecutionException e) {
                toolResults.add(toolResult("?", "[ERROR] " + e.getMessage()));
            }
        }
        return toolResults;
    }

    /**
     * 取消所有未完成的 Future。
     */
    private void cancelAllFutures(CompletableFuture<ChatMessage>[] futures) {
        for (CompletableFuture<ChatMessage> f : futures) {
            if (f != null && !f.isDone()) {
                f.cancel(true);
            }
        }
    }

    /**
     * 构建用户中断时的工具结果（全部标记为 aborted）。
     */
    private List<ChatMessage> buildAbortedResults(CompletableFuture<ChatMessage>[] futures,
                                                  List<ONode> tcArray) {
        List<ChatMessage> results = new ArrayList<>();
        for (int i = 0; i < futures.length; i++) {
            ONode tc = tcArray.get(i);
            String tcId = tc.get("id").getString();
            results.add(toolResult(tcId,
                    "{\"error\":\"用户已中断\",\"aborted\":true}"));
        }
        return results;
    }

    // ==================== Self-Correction ====================

    private int handleSelfCorrection(List<ChatMessage> toolResults,
                                     boolean anySuppressed, int selfCorrectionAttempts) {
        if (!anySuppressed) return selfCorrectionAttempts;

        boolean allSuppressed = true;
        for (ChatMessage tr : toolResults) {
            String r = tr.getContent();
            if (r == null || !r.contains("\"rejectedReason\":\"storm\"")) {
                allSuppressed = false;
                break;
            }
        }
        if (!allSuppressed) return selfCorrectionAttempts;

        selfCorrectionAttempts++;
        if (selfCorrectionAttempts > maxSelfCorrectionAttempts()) {
            safeOutput("selfCorrect", () -> output.onLog(LogLevel.WARN,
                    "[self-correct] 已达自愈尝试上限（" + maxSelfCorrectionAttempts() + "次），停止循环"));
            return -1;
        }

        final int currentAttempts = selfCorrectionAttempts;
        safeOutput("selfCorrect", () -> output.onLog(LogLevel.INFO,
                "[self-correct] 所有工具调用被 storm 抑制，第" + currentAttempts + "次自愈尝试"));
        ctx.addUser("[系统提示：你刚刚重复调用了相同的工具。请换一种方式完成任务，"
                + "或直接用文本回答。]");
        return selfCorrectionAttempts;
    }

    // ==================== 内部辅助 ====================

    /** 用户中断后：将 streamLLM 已产出的内容写入上下文并返回 */
    private String handleAbortAfterStream(StreamResult sr) {
        String abortMarker = "\n\n<<用户主动停止生成>>";
        if (sr.content() != null && !sr.content().isEmpty()) {
            String markedContent = sr.content() + abortMarker;
            ctx.addAssistant(markedContent, null, sr.reasoningContent());
            return markedContent;
        }
        if (sr.reasoningContent() != null && !sr.reasoningContent().isEmpty()) {
            String markedReasoning = sr.reasoningContent() + abortMarker;
            ctx.addAssistant(null, null, markedReasoning);
            return markedReasoning;
        }
        return "⏹️ 已停止生成";
    }

    private void logAbort() {
        safeOutput("abort", () -> output.onLog(LogLevel.INFO, "[abort] 用户请求中断，停止推理循环"));
    }


    public ToolCall getToolCall(ONode n1) {
        String callId = n1.get("id").getString();

        String index = n1.get("index").getString();

        ONode n1f = n1.get("function");
        String name = n1f.get("name").getString(); //可能是空的
        ONode n1fArgs = n1f.get("arguments");
        String argStr = n1fArgs.getString();

        if (n1fArgs.isString()) {
            //有可能是 json string（还可能只是流的中间消息）
            if (FormatUtil.hasNestedJsonBlock(argStr)) {
                JsonReader reader = new JsonReader(argStr, Options.of(Feature.Read_AutoRepair));
                n1fArgs = reader.readLast();

                if (n1fArgs == null) {
                    log.warn("Parse tool arguments failed: {}", argStr);
                }
            }
        }

        Map<String, Object> argMap = new HashMap<>();
        if (n1fArgs != null) {
            if (n1fArgs.isObject()) {
                argMap = n1fArgs.toBean(Map.class);
            }
        }

        return new ToolCall(index, callId, name, argStr, argMap);
    }
}
