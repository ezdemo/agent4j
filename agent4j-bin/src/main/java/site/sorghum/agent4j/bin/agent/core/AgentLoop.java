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
import site.sorghum.agent4j.bin.agent.context.*;
import site.sorghum.agent4j.bin.agent.hitl.HitlManager;
import site.sorghum.agent4j.bin.agent.listener.AgentLoopListener;
import site.sorghum.agent4j.bin.agent.listener.NoOpAgentLoopListener;
import site.sorghum.agent4j.bin.agent.model.*;
import site.sorghum.agent4j.bin.agent.output.ConsoleAgentOutput;
import site.sorghum.agent4j.bin.agent.resilient.ReasonBreaker;
import site.sorghum.agent4j.bin.agent.resilient.Scavenger;
import site.sorghum.agent4j.bin.agent.resilient.StormBreaker;
import site.sorghum.agent4j.bin.builtin.SubAgentTool;
import site.sorghum.agent4j.bin.config.Agent4jConfig;
import site.sorghum.agent4j.bin.model.ModelClient;
import site.sorghum.agent4j.bin.model.UserMessageSanitizer;
import site.sorghum.agent4j.bin.session.SessionService;
import site.sorghum.agent4j.bin.tool.ToolRegistry;
import site.sorghum.agent4j.tool.solon.common.SessionFileChangeTracker;
import site.sorghum.agent4j.tool.*;
import site.sorghum.agent4j.tool.interact.FinishTool;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

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
    /** 子代理完整执行超时秒数（默认 1 小时） */
    private static final int DEFAULT_SUB_AGENT_TIMEOUT_SEC = 3600;
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

    private int subAgentTimeoutSec() {
        return config != null ? config.subAgentTimeoutSec() : DEFAULT_SUB_AGENT_TIMEOUT_SEC;
    }

    private int maxSelfCorrectionAttempts() {
        return config != null ? config.maxSelfCorrectionAttempts() : DEFAULT_MAX_SELF_CORRECTION;
    }

    // ==================== 核心字段 ====================

    private final ModelClient client;
    private final ToolRegistry registry;
    private final Agent4jConfig config;
    private volatile boolean terminateOnNoToolCall;
    @Getter
    private final ConversationContext ctx;
    private final ReasonBreaker reasonBreaker = new ReasonBreaker();
    private final StormBreaker stormBreaker;
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

    /** 最近一次请求的离线上下文构成，用于展示和折叠预检。 */
    @Getter
    private volatile ContextTokenEstimate lastContextEstimate;

    /** 用户主动中断标志（前端点击停止按钮时设置） */
    private volatile boolean userAbortRequested = false;

    /** 外部中断源（Runnable）—— 由父级 AgentLoopController 设置，子代理主循环会同步检查 */
    private volatile Runnable externalAbortSource = null;

    /** 外部中断条件（父代理中断或子代理显式取消）。 */
    private volatile BooleanSupplier externalAbortCheck = null;

    /** 当前正在执行的工具 Future 数组（用于 abort 时取消） */
    private volatile CompletableFuture<ChatMessage>[] activeToolFutures = null;

    /** 当前工具的显式取消控制器（用于停止子代理等长运行任务） */
    private volatile ToolExecutionControl[] activeToolControls = null;

    /** 工具工作线程对应的取消控制器。 */
    private final ThreadLocal<ToolExecutionControl> currentToolControl = new ThreadLocal<>();

    /** 任务完成标志 —— finish 工具设置，非空时主循环将退出并返回该内容 */
    private volatile String finishContent = null;

    @Setter
    @Getter
    private volatile String sessionId;

    /** 主循环是否正在执行中（防止巡检线程与主循环冲突） */
    private final AtomicBoolean running = new AtomicBoolean(false);

    // ==================== 构造器 ====================

    public AgentLoop(ModelClient client, ToolRegistry registry, ConversationContext ctx) {
        this(client, registry, ctx, "free", null);
    }

    public AgentLoop(ModelClient client, ToolRegistry registry, ConversationContext ctx, String hitlMode) {
        this(client, registry, ctx, hitlMode, null);
    }

    public AgentLoop(ModelClient client, ToolRegistry registry, ConversationContext ctx,
                     String hitlMode, Agent4jConfig config) {
        this.client = client;
        this.registry = registry;
        this.ctx = ctx;
        this.config = config;
        this.terminateOnNoToolCall = config == null || config.terminateOnNoToolCall();
        this.hitlManager = new HitlManager(hitlMode);
        this.stormBreaker = StormBreaker.fromConfig(config);
    }

    // ==================== 公共控制 API ====================

    /** 切换 HITL 模式 */
    public void toggleHitl() {
        hitlManager.toggleHitl();
    }

    /** 获取 HITL 模式状态 */
    @Override
    public boolean isHitlMode() {
        return hitlManager.isHitlMode();
    }

    /**
     * 获取当前 HITL 模式名称。
     */
    @Override
    public String getHitlMode() {
        return hitlManager.getHitlMode();
    }

    /** 设置 HITL 模式（用于配置热更新） */
    public void setHitlMode(String mode) {
        hitlManager.setHitlMode(mode);
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

    @Override
    public boolean terminateOnNoToolCall() {
        return terminateOnNoToolCall;
    }

    /** 运行时更新无工具调用时的结束策略。 */
    public void setTerminateOnNoToolCall(boolean enabled) {
        this.terminateOnNoToolCall = enabled;
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
     * <p>子代理中此方法还会同步检查父级的中断状态，确保在长工具执行期间也能及时响应父级中止。</p>
     */
    @Override
    public boolean isAbortRequested() {
        Runnable source = externalAbortSource;
        if (source != null) source.run();
        return userAbortRequested;
    }

    /** 重置用户中断标志（每回合开始时调用） */
    public void resetUserAbort() {
        userAbortRequested = false;
        client.resetStreamAbort();
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
            this.externalAbortCheck = null;
            return;
        }
        setExternalAbortCheck(parentController::isAbortRequested);
    }

    void setExternalAbortCheck(BooleanSupplier abortCheck) {
        this.externalAbortCheck = abortCheck;
        this.externalAbortSource = () -> {
            BooleanSupplier check = this.externalAbortCheck;
            if (check != null && check.getAsBoolean() && !userAbortRequested) {
                doAbort();
                log.info("[loop] 检测到外部中断信号，子代理同步中止");
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
        cancelAllFutures(activeToolFutures, activeToolControls);
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

    @Override
    public SessionService getSessionService() {
        return this.sessionService;
    }

    @Override
    public ModelClient getModelClient() {
        return client;
    }

    @Override
    public Agent4jConfig getAgentConfig() {
        return config;
    }

    @Override
    public void registerToolCancellation(Runnable cancellation) {
        ToolExecutionControl control = currentToolControl.get();
        if (control != null) {
            control.register(cancellation);
        }
    }

    @Override
    public void clearToolCancellation() {
        ToolExecutionControl control = currentToolControl.get();
        if (control != null) {
            control.clearCancellation();
        }
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

    /**
     * 从当前上下文重新计算 token 构成。
     * <p>供历史会话在未执行新一轮模型请求时按持久化消息生成预估。</p>
     */
    public ContextTokenEstimate estimateCurrentContext() {
        ONode tools = refreshTools();
        ContextTokenEstimate estimate = ContextTokenEstimator.estimate(
                ctx.buildMessages(), tools, buildToolInstructions());
        lastContextEstimate = estimate;
        return estimate;
    }

    private String buildToolInstructions() {
        return """
                ## 工具协作约定

                工具的名称、参数和返回格式以本轮工具上下文为准，无需重复记忆工具清单。

                - `sub_agent` 用于可独立推进的子任务。子代理有独立上下文，不能再派生子代理。收到结果后由主代理负责整合、复核并向用户交付。

                | 角色 | 只读 | 适用场景 | 汇报格式 |
                |------|------|----------|----------|
                | `explore` | ✅ | 只调查不修改——定位代码、追溯调用链、理解实现、排查问题原因 | 发现 / 证据（文件与位置）/ 建议 |
                | `implement` | ❌ | 按指定范围实现功能或修复——最小化改动，完成后运行相关检查 | 修改 / 验证 / 剩余风险 |
                | `test` | ❌ | 添加或调整测试——先确认覆盖缺口，不修改生产代码（除非任务要求） | 覆盖场景 / 测试结果 / 发现的问题 |
                | `review` | ✅ | 代码审查——寻找真实缺陷、回归、并发/安全问题、测试缺口 | 按严重性排序列出问题，附位置、影响和修复方向 |
                | `plan` | ✅ | 方案设计——先理解现状，再给出可执行的分步方案，说明架构影响和取舍 | 分步方案，含涉及模块、兼容性、验证方法 |

                选择角色的通用建议：需要探索或分析用 `explore`；需要方案设计用 `plan`；需要审查已有代码用 `review`；需要写代码或修 bug 用 `implement`；需要补充测试用 `test`。
                派发时务必通过 workspace_write 共享必要上下文，并要求子代理将结果写回约定 key，避免结果散落在对话中。

                - `workspace_*` 是主代理和子代理之间的共享通信通道，不是项目文件系统。用它传递任务背景、调查证据、中间结论和可复用交付物；不要用它替代对代码文件的读写。
                - 派发子任务前，主代理应将需要共享的背景写入 `workspace_write`，并在任务中告知子代理准确的 key。子代理先用 `workspace_read` 获取所需上下文，完成后将重要发现、修改摘要和验证结果写回约定 key；主代理用 `workspace_read` 汇总。仅在不知道 key 时使用 `workspace_list` 按前缀查找。
                - 使用稳定、可归属的 key，例如 `tasks/<task-id>/context`、`tasks/<task-id>/findings`、`tasks/<task-id>/result`。写入结果应包含结论、证据位置和未解决事项，避免只写“已完成”之类不可复用的信息。
                - 只有需要用户在互斥方案之间作出选择，且该选择会实质改变实现或外部影响时，才使用 `ask_choice`；能通过现有上下文或合理工程判断解决的问题不要打断用户。
                - 工作流和目标工具只用于需要跨回合追踪、人工审批或失败恢复的任务；普通的短任务无需创建工作流。
                %s
                """.formatted(terminateOnNoToolCall()
                ? "- 无工具调用时，模型的纯文本回复会结束对话"
                : "- 结束对话**必须**调用 `finish`，纯文本回复不会退出循环");
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
        stormBreaker.reset();
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
            PreparedMessages prepared = prepareMessages(step, tools);
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

            // ---- 5. 无 tool calls → 根据配置结束或要求模型继续 ----
            if (!hasToolCalls) {
                ctx.addAssistant(sr.content(), null, sr.reasoningContent());
                if (terminateOnNoToolCall()) {
                    return sr.content() == null ? "" : sr.content();
                }

                noToolCallStreak++;
                log.warn("[loop] 第 {} 次无工具调用，累积无工具轮数: {}", step, noToolCallStreak);
                if (noToolCallStreak >= 3) {
                    log.warn("[loop] 连续 {} 轮无工具调用，降级终止", noToolCallStreak);
                    String degraded = ctx.getLastAssistantContent();
                    return degraded != null && !degraded.isEmpty() ? degraded : "任务中断，未完成（已收集部分结果）";
                }

                ctx.addUser(FinishTool.TIPS);
                continue;
            }

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

            // ---- 7.5. finish 工具显式结束本轮 ----
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

        stormBreaker.reset();

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
        stormBreaker.reset();
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

    private PreparedMessages prepareMessages(int step, ONode tools) throws IOException {
        List<ChatMessage> messages = ctx.buildMessages();
        MessageHealer.HealResult healResult = MessageHealer.heal(messages);
        messages = healResult.messages();

        String instr = buildToolInstructions();
        ContextTokenEstimate beforeFold = ContextTokenEstimator.estimate(messages, tools, instr);

        // 预检：token 数接近上下文窗口 80% 时折叠
        boolean foldedThisStep = false;
        int maxCtx = client.getMaxContextTokens();
        int tokenThreshold = (int) (maxCtx * 0.8);
        int estimatedPromptTokens = beforeFold.exactTokenizer() || lastPromptTokens <= 0
                ? beforeFold.totalTokens()
                : lastPromptTokens;
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

        // 注入动态工具使用指引到系统提示词
        if (!instr.isEmpty()) {
            ChatMessage sysMsg = messages.get(0);
            String enhancedContent = sysMsg.getContent() + "\n\n" + instr;
            ChatMessage enhancedSys = ChatMessage.ofSystem(enhancedContent);
            List<ChatMessage> withInstr = new ArrayList<>(messages.size());
            withInstr.add(enhancedSys);
            withInstr.addAll(messages.subList(1, messages.size()));
            messages = withInstr;
        }

        lastContextEstimate = ContextTokenEstimator.estimate(messages, tools, null);

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

    ToolExecutionResult executeToolCalls(ONode toolCalls) {
        if (toolCalls == null){
            return new ToolExecutionResult(Collections.emptyList(),Collections.emptyList(),false);
        }

        List<ONode> tcArray = toolCalls.getArray();

        // 1. 解析并过滤工具调用
        ParsedToolCalls parsed = parseAndFilterToolCalls(tcArray);
        List<ONode> finalTcArray = parsed.nodeList();

        // 2. 异步并行分发
        DispatchResult dispatch = dispatchToolCallsAsync(finalTcArray);

        // 3. 等待并收集结果
        List<ChatMessage> toolResults = collectToolResults(
                dispatch.futures(), dispatch.controls(), finalTcArray);

        // 4. 沙箱越界 HITL
        HitlRequiredException hitlEx = dispatch.hitlRef().get();
        if (hitlEx != null) {
            hitlManager.setSandboxPending(toolCalls, hitlEx.getDetails());
            safeOutput("hitl", () -> output.onLog(LogLevel.WARN,
                    "[hitl] 沙箱越界触发强制审批: " + hitlEx.getDetails()));
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
    private static final class ToolExecutionControl {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicReference<Runnable> cancellation = new AtomicReference<>();

        boolean isCancelled() {
            return cancelled.get();
        }

        void register(Runnable action) {
            if (action == null) return;
            if (cancelled.get()) {
                runCancellation(action);
                return;
            }
            cancellation.set(action);
            if (cancelled.get() && cancellation.compareAndSet(action, null)) {
                runCancellation(action);
            }
        }

        void clearCancellation() {
            cancellation.set(null);
        }

        void cancel() {
            cancelled.set(true);
            Runnable action = cancellation.getAndSet(null);
            if (action != null) {
                runCancellation(action);
            }
        }

        private static void runCancellation(Runnable action) {
            try {
                action.run();
            } catch (Exception e) {
                log.warn("[tool] 显式取消动作执行失败: {}", e.getMessage());
            }
        }
    }

    private record DispatchResult(CompletableFuture<ChatMessage>[] futures,
                                  ToolExecutionControl[] controls,
                                  AtomicBoolean anySuppressed,
                                  AtomicReference<HitlRequiredException> hitlRef) {}

    private DispatchResult dispatchToolCallsAsync(List<ONode> tcArray) {
        int tcCount = tcArray.size();
        @SuppressWarnings("unchecked")
        CompletableFuture<ChatMessage>[] futures = new CompletableFuture[tcCount];
        ToolExecutionControl[] controls = new ToolExecutionControl[tcCount];
        final AtomicBoolean anySuppressed = new AtomicBoolean(false);
        final AtomicReference<HitlRequiredException> hitlRef = new AtomicReference<>(null);
        final AgentOutput capturedOutput = this.output;
        for (int i = 0; i < tcCount; i++) {
            final int idx = i;
            controls[i] = new ToolExecutionControl();
            futures[i] = CompletableFuture.supplyAsync(() -> {
                // 同步外部中断源（子代理检查父级 abort 状态，确保父级中断后工具不继续执行）
                Runnable extAbort = externalAbortSource;
                if (extAbort != null) {
                    extAbort.run();
                }
                // 用户已中断 → 立即返回，不执行工具
                ToolExecutionControl control = controls[idx];
                if (userAbortRequested || control.isCancelled()) {
                    ONode tc = tcArray.get(idx);
                    String tcId = tc.get("id").getString();
                    return toolResult(tcId,
                            "{\"error\":\"用户已中断\",\"aborted\":true}");
                }

                if (capturedOutput != null) {
                    SubAgentTool.setCurrentOutput(capturedOutput);
                }
                currentToolControl.set(control);
                SessionFileChangeTracker.bind(registry.getWorkspace(), getSessionId());
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

                    String argumentsJson = toolCall.getArgumentsStr();
                    if (!toolMetaFlag(fc, "stormExempt")) {
                        boolean readOnly = toolMetaFlag(fc, "readOnly");
                        StormBreaker.SuppressResult suppression =
                                stormBreaker.inspect(toolCall.getName(), argumentsJson, readOnly);
                        if (suppression.suppressed()) {
                            anySuppressed.set(true);
                            String result = rejectedToolResult(suppression.reason(), "storm");
                            safeListener("toolResult", () -> listener.onToolResult(toolCall.getName(), result));
                            safeOutputDebug("toolResult", () -> output.onToolResult(toolCall.getName(), result));
                            return toolResult(toolCall.getId(), result);
                        }
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
                    } catch (HitlRequiredException e) {
                        hitlRef.compareAndSet(null, e);
                        return toolResult(toolCall.getId(),
                                "[HITL_PENDING:" + e.getReason() + "] " + e.getDetails());
                    } catch (Throwable e) {
                        String result = e.getMessage();
                        safeListener("toolResult", () -> listener.onToolResult(toolCall.getName(), result));
                        safeOutputDebug("toolResult", () -> output.onToolResult(toolCall.getName(), result));
                        return toolResult(toolCall.getId(), result);
                    }
                } finally {
                    SessionFileChangeTracker.clearBinding();
                    currentToolControl.remove();
                    ToolContext.clearCurrentController();
                    SubAgentTool.clearCurrentOutput();
                }
            });
        }
        return new DispatchResult(futures, controls, anySuppressed, hitlRef);
    }

    private static boolean toolMetaFlag(FunctionTool tool, String key) {
        Map<String, Object> meta = tool.meta();
        Object value = meta != null ? meta.get(key) : null;
        if (value instanceof Boolean flag) {
            return flag;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private static String rejectedToolResult(String message, String reason) {
        return ONode.ofJson("{}")
                .asObject()
                .set("error", message)
                .set("rejectedReason", reason)
                .toJson();
    }

    /**
     * 等待所有工具 Future 完成（带超时保护），收集结果。
     * 如果用户请求中断，立即取消未完成的 Future 并返回。
     */
    private List<ChatMessage> collectToolResults(CompletableFuture<ChatMessage>[] futures,
                                                 ToolExecutionControl[] controls,
                                                 List<ONode> tcArray) {
        this.activeToolControls = controls;
        this.activeToolFutures = futures;
        long startedAt = System.nanoTime();
        long regularDeadline = startedAt + TimeUnit.SECONDS.toNanos(Math.max(1, toolTimeoutSec()));
        long subAgentDeadline = startedAt + TimeUnit.SECONDS.toNanos(Math.max(1, subAgentTimeoutSec()));
        List<ChatMessage> results = new ArrayList<>(Collections.nCopies(futures.length, null));

        try {
            // 先处理普通工具，确保它们不会因同批次长运行子代理而获得更长超时。
            for (int pass = 0; pass < 2; pass++) {
                boolean waitForSubAgent = pass == 1;
                for (int i = 0; i < futures.length; i++) {
                    boolean subAgentCall = isSubAgentCall(tcArray.get(i));
                    if (subAgentCall != waitForSubAgent) continue;

                    if (userAbortRequested) {
                        cancelAllFutures(futures, controls);
                        return buildAbortedResults(futures, tcArray);
                    }

                    int timeoutSec = subAgentCall ? subAgentTimeoutSec() : toolTimeoutSec();
                    long deadline = subAgentCall ? subAgentDeadline : regularDeadline;
                    long remaining = deadline - System.nanoTime();
                    String timeoutLabel = subAgentCall ? "子代理" : "工具";
                    try {
                        if (remaining <= 0) {
                            throw new TimeoutException(timeoutLabel + " execution deadline exceeded");
                        }
                        results.set(i, futures[i].get(remaining, TimeUnit.NANOSECONDS));
                    } catch (TimeoutException e) {
                        cancelFuture(futures[i], controls[i]);
                        int resultIndex = i;
                        safeOutput("toolTimeout", () -> output.onLog(LogLevel.WARN,
                                "[tool] " + timeoutLabel + "执行超时（" + timeoutSec
                                        + "s），已请求停止: " + toolName(tcArray.get(resultIndex))));
                        results.set(i, timeoutResult(tcArray.get(i), timeoutLabel, timeoutSec));
                    } catch (CancellationException e) {
                        if (userAbortRequested) {
                            cancelAllFutures(futures, controls);
                            return buildAbortedResults(futures, tcArray);
                        }
                        results.set(i, timeoutResult(tcArray.get(i), timeoutLabel, timeoutSec));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        cancelAllFutures(futures, controls);
                        return buildAbortedResults(futures, tcArray);
                    } catch (ExecutionException e) {
                        results.set(i, toolResult(tcArray.get(i).get("id").getString(),
                                "[ERROR] " + e.getMessage()));
                    }
                }
            }
        } finally {
            this.activeToolFutures = null;
            this.activeToolControls = null;
        }
        return results;
    }

    private static boolean isSubAgentCall(ONode toolCall) {
        return "sub_agent".equals(toolName(toolCall));
    }

    private static String toolName(ONode toolCall) {
        return toolCall.get("function").get("name").getString();
    }

    private static ChatMessage timeoutResult(ONode toolCall, String label, int timeoutSec) {
        return toolResult(toolCall.get("id").getString(), ONode.ofJson("{}")
                .asObject()
                .set("error", label + "执行超时（" + timeoutSec + "s）")
                .set("rejectedReason", "timeout")
                .toJson());
    }

    private static void cancelFuture(CompletableFuture<ChatMessage> future,
                                     ToolExecutionControl control) {
        if (control != null) control.cancel();
        if (future != null && !future.isDone()) future.cancel(true);
    }

    private static void cancelAllFutures(CompletableFuture<ChatMessage>[] futures,
                                         ToolExecutionControl[] controls) {
        if (futures == null) return;
        for (int i = 0; i < futures.length; i++) {
            ToolExecutionControl control = controls != null && i < controls.length ? controls[i] : null;
            cancelFuture(futures[i], control);
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
