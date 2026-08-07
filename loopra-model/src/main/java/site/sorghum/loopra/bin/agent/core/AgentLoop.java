package site.sorghum.loopra.bin.agent.core;

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
import site.sorghum.loopra.bin.agent.context.*;
import site.sorghum.loopra.bin.agent.hitl.HitlManager;
import site.sorghum.loopra.bin.agent.listener.AgentLoopListener;
import site.sorghum.loopra.bin.agent.listener.NoOpAgentLoopListener;
import site.sorghum.loopra.bin.agent.model.*;
import site.sorghum.loopra.bin.agent.output.ConsoleAgentOutput;
import site.sorghum.loopra.bin.agent.output.ParentOutputHolder;
import site.sorghum.loopra.bin.agent.resilient.ReasonBreaker;
import site.sorghum.loopra.bin.agent.resilient.Scavenger;
import site.sorghum.loopra.bin.agent.resilient.StormBreaker;
import site.sorghum.loopra.bin.agent.spi.AgentConfig;
import site.sorghum.loopra.bin.agent.spi.GoalGuard;
import site.sorghum.loopra.bin.agent.spi.SessionUsageSink;
import site.sorghum.loopra.bin.model.ModelApiError;
import site.sorghum.loopra.bin.model.ModelClient;
import site.sorghum.loopra.bin.model.UserMessageSanitizer;
import site.sorghum.loopra.bin.tool.ToolMetadata;
import site.sorghum.loopra.bin.tool.ToolRegistry;
import site.sorghum.loopra.tool.*;
import site.sorghum.loopra.tool.interact.FinishTool;
import site.sorghum.loopra.bin.session.SessionFileChangeTracker;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

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
    /** 服务端确认上下文超限时最多执行两次折叠恢复。 */
    private static final int MAX_CONTEXT_RECOVERIES = 2;
    /** 工具任务必须由可中断的 Future 承载，CompletableFuture.cancel(true) 不会中断运行线程。 */
    private static final AtomicInteger TOOL_THREAD_COUNTER = new AtomicInteger();
    private static final ExecutorService TOOL_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "loopra-tool-" + TOOL_THREAD_COUNTER.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

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
    private final AgentConfig config;
    private volatile boolean terminateOnNoToolCall;
    @Getter
    private final ConversationContext ctx;
    private final ReasonBreaker reasonBreaker = new ReasonBreaker();
    private final StormBreaker stormBreaker;
    private final ToolCallValidator toolCallValidator;
    /** Goal 守卫（由上层注入）；null 时等价于无开放 Goal，守卫全部放行。 */
    @Setter
    private volatile GoalGuard goalGuard;
    /** Goal 生命周期只由拥有会话的主代理约束，子代理共享 sessionId 但不拥有 Goal。 */
    @Setter
    private volatile boolean goalGuardEnabled = true;
    /** 子代理可冻结这两部分，确保其整个生命周期内模型请求前缀完全一致。 */
    private volatile ONode frozenTools;
    private volatile String frozenToolInstructions;
    /**
     * 计划模式 —— 会话级状态：仅允许只读工具（工具列表过滤 + 执行拒绝 + 指令感知）。
     * 由 /plan、/execute 命令切换；不修改共享的 ToolRegistry，因此不影响同工作区的其他会话。
     */
    private volatile boolean planMode = false;
    /**
     * 计划模式下经 submit_plan 提交、待用户审查的执行计划。
     * /execute 批准时取出并注入为执行依据（取出即清空）。
     */
    private volatile String pendingPlan = null;
    /** 上层会话存储回调：计划提交/消费时同步持久化；子代理默认不设置。 */
    @Setter
    private Consumer<String> pendingPlanSink;
    @Getter
    private final HitlManager hitlManager;

    @Setter
    private SessionUsageSink sessionUsageSink;

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

    /** 工作区根目录 —— compact 折叠时用于沉淀长期记忆到 .loopra/loopra-memory.md。 null 则跳过沉淀。 */
    @Setter
    @Getter
    private volatile Path workspace;

    /** 当前正在执行的工具 Future 数组（用于 abort 时取消） */
    private volatile Future<ChatMessage>[] activeToolFutures = null;

    /** 当前工具的显式取消控制器（用于停止子代理等长运行任务） */
    private volatile ToolExecutionControl[] activeToolControls = null;

    /** 工具工作线程对应的取消控制器。 */
    private final ThreadLocal<ToolExecutionControl> currentToolControl = new ThreadLocal<>();

    /** 已脱离单次工具 Future、但仍需随用户停止而终止的资源。 */
    private final ConcurrentHashMap<String, Runnable> abortResources = new ConcurrentHashMap<>();

    /** 任务完成标志 —— finish 工具设置，非空时主循环将退出并返回该内容 */
    private volatile String finishContent = null;

    @Setter
    @Getter
    private volatile String sessionId;

    /**
     * 是否在工具批次结束时提取文件变更。
     * 子代理与父代理共用同一会话范围时关闭此开关，防止子循环提前取走父轮次的变更记录。
     */
    @Setter
    private volatile boolean drainFileChanges = true;

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
                     String hitlMode, AgentConfig config) {
        this(client, registry, ctx, hitlMode, config, null);
    }

    AgentLoop(ModelClient client, ToolRegistry registry, ConversationContext ctx,
              String hitlMode, AgentConfig config, ToolCallValidator toolCallValidator) {
        this.client = client;
        this.registry = registry;
        this.ctx = ctx;
        this.config = config;
        this.terminateOnNoToolCall = config == null || config.terminateOnNoToolCall();
        this.hitlManager = new HitlManager(hitlMode);
        this.hitlManager.setConfig(config);
        this.stormBreaker = StormBreaker.fromConfig(config);
        this.toolCallValidator = toolCallValidator != null ? toolCallValidator
                : ToolCallValidator.fromConfig(config, registry == null ? null : registry.getWorkspace());
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
        List<ChatMessage> folded = ContextFolding.foldKeepLast(messages, 20, client, workspace);
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
        cancelAbortResources();
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
    public SessionUsageSink getSessionUsageSink() {
        return this.sessionUsageSink;
    }

    @Override
    public ModelClient getModelClient() {
        return client;
    }

    @Override
    public AgentConfig getAgentConfig() {
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

    @Override
    public void registerAbortResource(String resourceId, Runnable cancellation) {
        if (resourceId == null || resourceId.isBlank() || cancellation == null) return;
        if (userAbortRequested) {
            runAbortResource(resourceId, cancellation);
            return;
        }
        abortResources.put(resourceId, cancellation);
        if (userAbortRequested && abortResources.remove(resourceId, cancellation)) {
            runAbortResource(resourceId, cancellation);
        }
    }

    @Override
    public void clearAbortResource(String resourceId) {
        if (resourceId != null) abortResources.remove(resourceId);
    }

    private void cancelAbortResources() {
        abortResources.forEach((resourceId, cancellation) -> {
            if (abortResources.remove(resourceId, cancellation)) {
                runAbortResource(resourceId, cancellation);
            }
        });
    }

    private static void runAbortResource(String resourceId, Runnable cancellation) {
        try {
            cancellation.run();
        } catch (Exception e) {
            log.warn("[abort] 取消工具资源失败: {}, {}", resourceId, e.getMessage());
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

    private static ChatMessage timedToolResult(String id, String result, long startedAt) {
        return ChatMessage.tool(id, result, startedAt, System.currentTimeMillis());
    }

    private static ChatMessage timedToolResult(ChatMessage result, long startedAt) {
        result.setToolTiming(startedAt, System.currentTimeMillis());
        return result;
    }

    public ONode refreshTools() {
        ONode fixed = frozenTools;
        if (fixed != null) {
            return fixed;
        }
        registry.refresh();
        ONode tools = registry.toOpenAiTools();
        return planMode ? filterReadOnlyTools(tools) : tools;
    }

    /** 固定后续请求的 system 附加指令和工具定义；必须在首轮模型调用前执行。 */
    public void freezePromptPrefix() {
        frozenToolInstructions = buildToolInstructions();
        ONode tools = registry.toOpenAiTools();
        frozenTools = ONode.ofJson((planMode ? filterReadOnlyTools(tools) : tools).toJson());
    }

    private String currentToolInstructions() {
        String fixed = frozenToolInstructions;
        String base = fixed != null ? fixed : buildToolInstructions();
        return planMode ? base + "\n\n" + PLAN_MODE_INSTRUCTIONS : base;
    }

    // ==================== 计划模式 ====================

    /**
     * 计划模式动态指令 —— 激活时追加到工具协作约定末尾。
     * 放在动态尾部而非 system prompt 固定前缀，避免模式切换破坏前缀缓存稳定性。
     */
    private static final String PLAN_MODE_INSTRUCTIONS = """
            ## Plan Mode（当前处于计划模式）

            - 仅只读工具可用：写入/修改/执行类工具已从工具列表移除，任何此类调用都会被直接拒绝
            - 不要尝试修改文件、执行命令等任何改变操作，也不要尝试绕过此限制
            - 先用只读工具充分探索，理解现状、明确目标、影响面与风险
            - 探索完成后用 `submit_plan` 工具提交完整执行计划供用户审查，然后向用户简要总结计划要点并结束本轮
            - 用户批准计划后系统会退出计划模式并按计划执行
            """;

    @Override
    public boolean isPlanMode() {
        return planMode;
    }

    /**
     * 切换计划模式（会话级状态，仅影响本循环）。
     * <p>切换在下一步立即生效：工具列表过滤为只读集合、非只读调用被拒绝、
     * 计划模式指令注入工具约定。持久化与事件通知由上层（LoopraAgent）负责。</p>
     */
    public void setPlanMode(boolean enabled) {
        this.planMode = enabled;
    }

    /**
     * 保存 submit_plan 提交的任务计划，并通过 plan_submitted 事件通知前端。
     */
    @Override
    public void submitPlan(String planMarkdown) {
        this.pendingPlan = planMarkdown;
        persistPendingPlan(planMarkdown);
        ONode data = ONode.ofJson("{}").asObject();
        data.set("plan", planMarkdown != null ? planMarkdown : "");
        safeOutput("planSubmitted", () -> output.sendEvent("plan_submitted", data.toJson()));
    }

    /** 获取待审查计划（不移除）。 */
    public String getPendingPlan() {
        return pendingPlan;
    }

    /** 静默恢复持久化的待审查计划，不触发事件或重复写盘。 */
    public void restorePendingPlan(String planMarkdown) {
        this.pendingPlan = planMarkdown;
    }

    /** 清除待审查计划并同步持久化。 */
    public void clearPendingPlan() {
        this.pendingPlan = null;
        persistPendingPlan(null);
    }

    /** 取出并清空待审查计划（/execute 批准时调用）。 */
    public String consumePendingPlan() {
        String plan = pendingPlan;
        clearPendingPlan();
        return plan;
    }

    private void persistPendingPlan(String planMarkdown) {
        Consumer<String> sink = pendingPlanSink;
        if (sink == null) return;
        try {
            sink.accept(planMarkdown);
        } catch (Exception e) {
            log.warn("[plan] 持久化待审查计划失败: {}", e.getMessage());
        }
    }

    /**
     * 将工具列表过滤为只读集合（不修改注册表及其缓存，返回新节点）。
     * 注册表中找不到对应 FunctionTool 的条目一律剔除（保守策略）。
     */
    private ONode filterReadOnlyTools(ONode tools) {
        if (tools == null || !tools.isArray()) {
            return tools;
        }
        Map<String, FunctionTool> available = registry.all();
        ONode filtered = ONode.ofJson(tools.toJson()).asArray();
        filtered.getArray().removeIf(item -> {
            String name = item.get("function").get("name").getString();
            FunctionTool tool = name != null ? available.get(name) : null;
            return tool == null || !ToolMetadata.isReadOnly(tool);
        });
        return filtered;
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
                ctx.buildMessages(), tools, currentToolInstructions());
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
                - 浏览器遇到登录、验证码、人机验证、二维码、短信/邮箱确认或安全风控时，严禁尝试绕过、猜测验证答案或索取敏感凭据。必须调用 `browser_request_user_action` 请求用户在可见浏览器中手动完成；用户确认后重新截图再继续。
                - 浏览器超过 16 个标签页时仍可新建，但会返回清理提醒；应在当前步骤完成后用 `browser_tabs` 查看并关闭不再需要的非活动标签。达到 20 个硬上限时，必须清理后才能创建新标签。优先用 `browser_navigate` 复用当前标签，避免反复重试。
                - 工作流和目标工具只用于需要跨回合追踪、人工审批或失败恢复的任务；普通的短任务无需创建工作流。
                %s
                """.formatted(terminateOnNoToolCall()
                ? "- 无工具调用时，模型的纯文本回复会结束对话"
                : "- 结束对话**必须**调用 `finish`，纯文本回复不会退出循环")
                + "\n\n" + currentGoalInstruction();
    }

    private String currentGoalInstruction() {
        if (!goalGuardEnabled || goalGuard == null) return "";
        try {
            return goalGuard.instruction(loadOpenGoal());
        } catch (Exception e) {
            log.warn("[goal] 读取当前 Goal 失败", e);
            return "## Goal 状态不可用\n持久化 Goal 暂时无法读取。不要声称目标已完成；可以说明错误并结束当前回合。"
                    + "若持续无法读取，可提示用户执行 `/goal reset` 清除损坏快照后重试。";
        }
    }

    private GoalGuard.GoalView loadOpenGoal() throws IOException {
        if (goalGuard == null || sessionId == null || sessionId.isBlank() || registry == null || registry.getWorkspace() == null) {
            return null;
        }
        return goalGuard.openGoal(registry.getWorkspace(), sessionId);
    }

    private String takeFinishContentIfAllowed() {
        String requested = finishContent;
        finishContent = null;
        if (requested == null || !goalGuardEnabled || goalGuard == null) return requested;
        try {
            GoalGuard.GoalView goal = loadOpenGoal();
            if (goal == null || !goal.requiresAgentWork()) return requested;
            String reminder = "[Goal guard] 当前 Goal 仍在推进：" + goal.title()
                    + "（" + goal.progressText() + "）。请继续当前步骤并调用 goal_complete；"
                    + "只有确实需要用户输入或外部状态变化时才调用 goal_block。";
            injectUserMessage(reminder);
            safeOutput("goalFinishGuard", () -> output.onLog(LogLevel.WARN, reminder));
            client.resetStreamAbort();
            return null;
        } catch (Exception e) {
            String reminder = "[Goal guard] 无法读取持久化 Goal 状态，已拒绝 finish：" + e.getMessage()
                    + "。若持续无法读取，可提示用户执行 `/goal reset` 清除损坏快照后重试。";
            injectUserMessage(reminder);
            safeOutput("goalFinishGuard", () -> output.onLog(LogLevel.ERROR, reminder));
            client.resetStreamAbort();
            return null;
        }
    }

    private String finishAfterToolBatch() {
        String requested = takeFinishContentIfAllowed();
        if (requested != null) {
            safeOutput("finish", () -> output.onLog(LogLevel.DEBUG, requested));
        }
        return requested;
    }

    private void discardFinishRequest() {
        finishContent = null;
        client.resetStreamAbort();
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
        userMessage = UserMessageSanitizer.sanitize(userMessage, client);
        
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
        int contextRecoveryAttempts = 0;
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
                if (ModelApiError.isContextLengthExceeded(sr.errorMessage())) {
                    boolean recovered = false;
                    while (contextRecoveryAttempts < MAX_CONTEXT_RECOVERIES) {
                        contextRecoveryAttempts++;
                        if (compactAfterContextOverflow(contextRecoveryAttempts)) {
                            recovered = true;
                            break;
                        }
                    }
                    if (recovered) {
                        continue;
                    }
                    safeOutput("streamError", () -> output.onError("[stream error] " + sr.errorMessage()));
                }
                String detail = sr.errorMessage() != null ? ": " + sr.errorMessage() : "";
                throw new IOException("[stream] API error during streaming" + detail);
            }

            // 当前请求成功后，下一次 step 重新拥有完整的上下文恢复预算。
            contextRecoveryAttempts = 0;
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
                GoalGuard.GoalView openGoal;
                try {
                    openGoal = (goalGuardEnabled && goalGuard != null) ? loadOpenGoal() : null;
                } catch (Exception e) {
                    String reminder = "[Goal guard] 无法读取持久化 Goal 状态：" + e.getMessage()
                            + "。若持续无法读取，可提示用户执行 `/goal reset` 清除损坏快照后重试。";
                    safeOutput("goalRead", () -> output.onLog(LogLevel.ERROR, reminder));
                    String content = sr.content() == null ? "" : sr.content();
                    return reminder + (content.isBlank() ? "" : "\n" + content);
                }
                if ((openGoal != null && !openGoal.requiresAgentWork())
                        || (terminateOnNoToolCall() && openGoal == null)) {
                    return sr.content() == null ? "" : sr.content();
                }

                noToolCallStreak++;
                log.warn("[loop] 第 {} 次无工具调用，累积无工具轮数: {}", step, noToolCallStreak);
                if (noToolCallStreak >= 3) {
                    log.warn("[loop] 连续 {} 轮无工具调用，降级终止", noToolCallStreak);
                    String degraded = ctx.getLastAssistantContent();
                    String fallback = degraded != null && !degraded.isEmpty()
                            ? degraded : "任务中断，未完成（已收集部分结果）";
                    return openGoal != null
                            ? "[Goal 尚未关闭，后续回合可继续]\n" + fallback
                            : fallback;
                }

                ctx.addUser(openGoal != null
                        ? "[Goal guard] Goal 尚未关闭。请继续执行当前步骤，记录证据，并调用 goal_complete 后再结束。"
                        : FinishTool.TIPS);
                continue;
            }

            noToolCallStreak = 0;

            // ---- HITL 拦截（配置校验模型时由 AI 代替原本的人工审批） ----
            if (hitlManager.isHitlMode() && hitlManager.requiresHITL(toolCalls)) {
                if (toolCallValidator.enabled()) {
                    ToolCallValidator.Decision decision = validateHITLToolCalls(toolCalls);
                if (decision.requiresHuman()) {
                    hitlManager.setSandboxPending(toolCalls, decision.reason());
                    hitlManager.storeSandboxContent(sr.content(), sr.reasoningContent());
                    return hitlManager.interceptForSandboxHITL(output);
                }
                if (decision.failed()) {
                    // 校验模型调用失败（如超时）：回退到人工审批，由用户决定是否执行。
                    safeOutput("toolValidator", () -> output.onLog(LogLevel.WARN,
                            "[tool-validator] " + decision.reason() + "，回退人工审批"));
                    String hitlPrompt = hitlManager.interceptForHITL(
                            toolCalls, sr.content(), sr.reasoningContent(), output);
                    if (hitlPrompt != null) {
                        return hitlPrompt;
                    }
                }
                if (!decision.allowed()) {
                        String denyMsg = "工具调用未通过 AI 审批: " + decision.reason();
                        safeOutput("toolValidator", () -> output.onLog(LogLevel.WARN,
                                "[tool-validator] " + denyMsg));
                        ctx.addAssistant(denyMsg, null, null);
                        return denyMsg;
                    }
                    safeOutput("toolValidator", () -> output.onLog(LogLevel.INFO,
                            "[tool-validator] AI 审批通过，继续执行工具调用"));
                } else {
                    String hitlPrompt = hitlManager.interceptForHITL(
                            toolCalls, sr.content(), sr.reasoningContent(), output);
                    if (hitlPrompt != null) {
                        return hitlPrompt;
                    }
                }
            }

            // ---- 6. 并行执行工具调用 ----
            ToolExecutionResult ter = executeToolCalls(toolCalls);

            // ---- 6.1 沙箱越界 HITL ----
            if (hitlManager.getState() == HitlState.PENDING && hitlManager.hasSandboxPending()) {
                hitlManager.storeSandboxContent(sr.content(), sr.reasoningContent());
                return hitlManager.interceptForSandboxHITL(output);
            }

            // ---- 7. 写入 assistant 消息 + 工具结果 ----
            ctx.addAssistant(sr.content(), ter.tcList(), sr.reasoningContent(), ter.fileChanges());
            for (ChatMessage tr : ter.toolResults()) {
                ctx.addToolResult(tr);
            }

            // ---- 7.5. finish 工具显式结束本轮（Goal 判定在整批工具落盘后执行） ----
            String requestedFinish = finishAfterToolBatch();
            if (requestedFinish != null) {
                return requestedFinish;
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
            discardFinishRequest();
            String denyMsg = "工具调用已被用户拒绝。";
            ctx.addAssistant(denyMsg, null, null);
            return denyMsg;
        }

        // 用户批准：先写入 assistant 消息
        ctx.addAssistant(state.content(), state.tcList(), state.reasoningContent());

        stormBreaker.reset();

        // 并行执行暂存的工具调用
        ToolExecutionResult ter = executeToolCalls(state.toolCalls());
        ctx.setLatestAssistantFileChanges(ter.fileChanges());

        // 沙箱越界 HITL：暂停并等待用户审批
        if (hitlManager.getState() == HitlState.PENDING && hitlManager.hasSandboxPending()) {
            hitlManager.storeSandboxContent(state.content(), state.reasoningContent());
            return hitlManager.interceptForSandboxHITL(output);
        }

        // 写入工具结果
        for (ChatMessage tr : ter.toolResults()) {
            ctx.addToolResult(tr);
        }
        String requestedFinish = finishAfterToolBatch();
        if (requestedFinish != null) return requestedFinish;

        // 进入统一推理循环
        return mainLoop();
    }

    /**
     * 沙箱越界 HITL 恢复：审批通过后以沙箱旁路模式重放工具调用。
     */
    private String resumeAfterSandboxHITL(boolean approved) throws IOException {
        HitlManager.PendingSandboxState state = hitlManager.drainSandboxHITL();

        if (!approved) {
            discardFinishRequest();
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
        ctx.setLatestAssistantFileChanges(initialTer.fileChanges());

        // 写入工具结果
        for (ChatMessage tr : initialTer.toolResults()) {
            ctx.addToolResult(tr);
        }
        String requestedFinish = finishAfterToolBatch();
        if (requestedFinish != null) return requestedFinish;

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
            String responseReasoning = tc.get("response_reasoning").getString();
            tcList.add(new ToolCallEntry(tcId, tcName, tcArgs, responseReasoning));
        }
        return tcList;
    }

    /**
     * 服务端确认上下文超限时执行一次兜底折叠，避免估算误差导致请求持续失败。
     * 第一次保留较多尾部历史，第二次进一步压缩。
     */
    private boolean compactAfterContextOverflow(int recoveryAttempt) {
        int keepCount = recoveryAttempt == 1 ? 20 : 8;
        try {
            List<ChatMessage> before = ctx.getHistory();
            List<ChatMessage> folded = ContextFolding.foldKeepLast(
                    ctx.buildMessages(), keepCount, client, workspace);
            boolean reduced = folded.size() < before.size()
                    || ContextFolding.estimateChars(folded) < ContextFolding.estimateChars(before);
            if (!reduced) {
                safeOutput("contextOverflow", () -> output.onLog(LogLevel.WARN,
                        "[context] 服务端确认上下文超限，但当前历史无法进一步折叠"));
                return false;
            }
            ctx.compact(folded);
            lastPromptTokens = 0;
            lastContextEstimate = null;
            safeOutput("contextOverflow", () -> output.onLog(LogLevel.WARN,
                    "[context] 服务端确认上下文超限，已折叠历史并重试本轮请求（保留近"
                            + keepCount + "条）"));
            return true;
        } catch (IOException e) {
            log.warn("[context] 上下文超限后的兜底折叠失败: {}", e.getMessage());
            return false;
        }
    }

    // ==================== 步骤 1: 消息准备 ====================

    static int effectivePromptTokens(int offlineEstimate, int lastPromptTokens) {
        return Math.max(offlineEstimate, lastPromptTokens);
    }

    private PreparedMessages prepareMessages(int step, ONode tools) throws IOException {
        List<ChatMessage> messages = ctx.buildMessages();
        MessageHealer.HealResult healResult = MessageHealer.heal(messages);
        messages = healResult.messages();

        String instr = currentToolInstructions();
        ContextTokenEstimate beforeFold = ContextTokenEstimator.estimate(messages, tools, instr);

        // 预检：token 数接近上下文窗口 80% 时折叠
        boolean foldedThisStep = false;
        int maxCtx = client.getMaxContextTokens();
        int tokenThreshold = (int) (maxCtx * 0.8);
        // 服务端最近一次 prompt_tokens 是真实用量下界；即使离线 tokenizer 可用，
        // 不同模型 tokenizer 及请求封装差异也可能让离线估算偏低，因此取两者较大值。
        int estimatedPromptTokens = effectivePromptTokens(beforeFold.totalTokens(), lastPromptTokens);
        boolean needFold = estimatedPromptTokens > tokenThreshold;
        if (needFold) {
            try {
                output.onLog(LogLevel.INFO, "[fold] 触发折叠: estimatedTokens=" + estimatedPromptTokens
                        + " threshold=" + tokenThreshold + " maxCtx=" + maxCtx);
            } catch (Exception e) {
                log.warn("[fold] output.onLog异常: {}", e.getMessage());
            }
            messages = ContextFolding.fold(messages, maxTotalChars(), keepTailChars(), client, workspace);
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
        final String[] streamErrorMessage = {null};
        // 连续命中计数：连续两次 analyze 命中才硬终止，单次命中只发软警告
        final int[] consecutiveHits = {0};
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
                // 流式增量检测：每 1000 字符检查一次思考循环
                int newLen = reasoningBuf.length();
                if (newLen - lastCheckLen[0] >= 1000) {
                    lastCheckLen[0] = newLen;
                    ReasonBreaker.LoopResult lr = reasonBreaker.analyze(reasoningBuf.toString());
                    if (lr.looping) {
                        consecutiveHits[0]++;
                        if (consecutiveHits[0] == 1) {
                            // 首次命中：软警告，不中断流，给模型自己跳出的机会
                            safeOutput("ReasonBreaker", () -> output.onLog(LogLevel.WARN,
                                    "[ReasonBreaker] 疑似思考循环（软警告）—— " + lr.toWarning()));
                        } else {
                            // 连续两次命中：硬终止
                            loopSnapshot[0] = reasoningBuf.toString();
                            loopAborted.set(true);
                            reasonBreaker.recordTrigger();
                            safeOutput("ReasonBreaker", () -> output.onLog(LogLevel.WARN,
                                    "[ReasonBreaker] 连续两次检测到思考循环，终止本轮推理。" + lr.toWarning()));
                            client.abortStream();
                            streamLatch.countDown();
                        }
                    } else {
                        // 未命中：重置连续计数（模型已跳出循环）
                        consecutiveHits[0] = 0;
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
                if (sessionUsageSink != null) {
                    sessionUsageSink.updateLastPromptTokens(promptTokens);
                }
                String currentModel = client.getModel();
                safeListener("usage", () -> listener.onUsage(currentModel, promptTokens, completionTokens, totalTokens, cacheHit, cacheMiss));
                safeOutputDebug("usage", () -> output.onUsage(currentModel, promptTokens, completionTokens, totalTokens, cacheHit, cacheMiss));
            }

            @Override
            public void onRetry(String reason, int retryAttempt, int maxAttempts, int delaySeconds) {
                String message = "AI 接口暂时不可用（" + reason + "），将在 " + delaySeconds
                        + " 秒后重试（" + retryAttempt + "/" + maxAttempts + "）";
                safeOutput("modelRetry", () -> output.onLog(LogLevel.WARN, message));
            }

            @Override
            public void onDone() {
                streamLatch.countDown();
            }

            @Override
            public void onError(String err) {
                streamError.set(true);
                streamErrorMessage[0] = err;
                if (!ModelApiError.isContextLengthExceeded(err)) {
                    safeOutput("streamError", () -> output.onError("[stream error] " + err));
                }
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
            return new StreamResult(null, null, null, true, streamErrorMessage[0]);
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
            return new ToolExecutionResult(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), false);
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

        List<FileChange> fileChanges = drainFileChanges
                ? SessionFileChangeTracker.drain(registry.getWorkspace(), getSessionId())
                : List.of();
        return new ToolExecutionResult(parsed.tcList(), toolResults, fileChanges,
                dispatch.anySuppressed().get());
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
            String responseReasoning = tc.get("response_reasoning").getString();

            tcList.add(new ToolCallEntry(tcId, tcName, tcArgs, responseReasoning));
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
        private final AtomicLong startedAt = new AtomicLong(0L);
        private final AtomicReference<Runnable> cancellation = new AtomicReference<>();

        void markStarted(long timestamp) {
            startedAt.compareAndSet(0L, timestamp);
        }

        long startedAt() {
            return startedAt.get();
        }

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

    private record DispatchResult(Future<ChatMessage>[] futures,
                                  ToolExecutionControl[] controls,
                                  AtomicBoolean anySuppressed,
                                  AtomicReference<HitlRequiredException> hitlRef) {}

    private DispatchResult dispatchToolCallsAsync(List<ONode> tcArray) {
        int tcCount = tcArray.size();
        @SuppressWarnings("unchecked")
        Future<ChatMessage>[] futures = new Future[tcCount];
        ToolExecutionControl[] controls = new ToolExecutionControl[tcCount];
        final AtomicBoolean anySuppressed = new AtomicBoolean(false);
        final AtomicReference<HitlRequiredException> hitlRef = new AtomicReference<>(null);
        final AgentOutput capturedOutput = this.output;
        for (int i = 0; i < tcCount; i++) {
            final int idx = i;
            controls[i] = new ToolExecutionControl();
            futures[i] = TOOL_EXECUTOR.submit(() -> {
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
                    ParentOutputHolder.set(capturedOutput);
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

                    // 计划模式硬约束：非只读工具直接拒绝。
                    // 正常情况下写工具已从工具列表移除，此处兼容模型凭记忆幻觉调用的兑底。
                    if (planMode && !ToolMetadata.isReadOnly(fc)) {
                        String result = rejectedToolResult(
                                "当前处于计划模式，仅允许只读工具；本次调用已被拒绝。请继续使用只读工具探索，完成后用 submit_plan 提交计划。",
                                "plan_mode");
                        safeListener("toolResult", () -> listener.onToolResult(toolCall.getName(), result));
                        safeOutputDebug("toolResult", () -> output.onToolResult(toolCall.getName(), result));
                        return toolResult(toolCall.getId(), result);
                    }

                    String argumentsJson = toolCall.getArgumentsStr();
                    if (!ToolMetadata.isStormExempt(fc)) {
                        boolean readOnly = ToolMetadata.isReadOnly(fc);
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

                    // 收集工具调用上下文；终端工具通过 __cwd 获取实际执行目录。
                    ToolContext.setCurrentController(AgentLoop.this);
                    String workspacePath = registry.getWorkspace().toAbsolutePath().normalize().toString();
                    HashMap<String, Object> extraMap = new HashMap<>();
                    extraMap.put("__cwd", workspacePath);
                    extraMap.put("ctx", new ToolContext(
                            new HashMap<>(),
                            workspacePath,
                            this.getSessionId()
                    ));

                    ToolRequest req = new ToolRequest(null,extraMap, toolCall.getArguments());
                    long toolStartedAt = System.currentTimeMillis();
                    control.markStarted(toolStartedAt);
                    try {
                        ToolResult call = fc.call(req.getArgs());
                        String rawResult = call.getContent();
                        ImageToolResult.ImageResult imageResult = ("read_image".equals(toolCall.getName())
                                || "browser_screenshot".equals(toolCall.getName()))
                                ? ImageToolResult.parseResult(rawResult) : null;
                        String result = imageResult == null ? rawResult : imageResult.summary();
                        safeListener("toolResult", () -> listener.onToolResult(toolCall.getName(), result));
                        safeOutputDebug("toolResult", () -> output.onToolResult(toolCall.getName(), result));
                        ChatMessage toolMessage = imageResult == null ? ChatMessage.tool(toolCall.getId(), result)
                                : ChatMessage.toolWithImage(toolCall.getId(), result,
                                imageResult.dataUri(), imageResult.detail());
                        return timedToolResult(toolMessage, toolStartedAt);
                    } catch (HitlRequiredException e) {
                        hitlRef.compareAndSet(null, e);
                        return toolResult(toolCall.getId(),
                                "[HITL_PENDING:" + e.getReason() + "] " + e.getDetails());
                    } catch (Throwable e) {
                        String result = e.getMessage();
                        safeListener("toolResult", () -> listener.onToolResult(toolCall.getName(), result));
                        safeOutputDebug("toolResult", () -> output.onToolResult(toolCall.getName(), result));
                        return timedToolResult(toolCall.getId(), result, toolStartedAt);
                    }
                } finally {
                    SessionFileChangeTracker.clearBinding();
                    currentToolControl.remove();
                    ToolContext.clearCurrentController();
                    ParentOutputHolder.clear();
                }
            });
        }
        return new DispatchResult(futures, controls, anySuppressed, hitlRef);
    }

    ToolCallValidator.Decision validateHITLToolCalls(ONode toolCalls) {
        if (toolCalls == null || !toolCalls.isArray()) {
            return ToolCallValidator.Decision.deny("工具调用格式无效");
        }
        for (ONode node : toolCalls.getArray()) {
            ToolCall toolCall = getToolCall(node);
            if (toolCall.getName() == null || toolCall.getName().isBlank()) {
                return ToolCallValidator.Decision.deny("工具名称为空");
            }
            ToolCallValidator.Decision decision = toolCallValidator.validate(
                    toolCall.getName(), ONode.ofBean(toolCall.getArguments()).toJson());
            if (decision.requiresHuman()) {
                return ToolCallValidator.Decision.requireHuman(
                        toolCall.getName() + ": " + decision.reason());
            }
            if (decision.failed()) {
                return ToolCallValidator.Decision.failed(
                        toolCall.getName() + ": " + decision.reason());
            }
            if (!decision.allowed()) {
                return ToolCallValidator.Decision.deny(toolCall.getName() + ": " + decision.reason());
            }
        }
        return ToolCallValidator.Decision.allow();
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
    private List<ChatMessage> collectToolResults(Future<ChatMessage>[] futures,
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
                        results.set(i, timeoutResult(tcArray.get(i), timeoutLabel, timeoutSec, controls[i]));
                    } catch (CancellationException e) {
                        if (userAbortRequested) {
                            cancelAllFutures(futures, controls);
                            return buildAbortedResults(futures, tcArray);
                        }
                        results.set(i, timeoutResult(tcArray.get(i), timeoutLabel, timeoutSec, controls[i]));
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

    private static ChatMessage timeoutResult(ONode toolCall, String label, int timeoutSec,
                                             ToolExecutionControl control) {
        String content = ONode.ofJson("{}")
                .asObject()
                .set("error", label + "执行超时（" + timeoutSec + "s）")
                .set("rejectedReason", "timeout")
                .toJson();
        long startedAt = control == null ? 0L : control.startedAt();
        return startedAt > 0L
                ? ChatMessage.tool(toolCall.get("id").getString(), content,
                startedAt, System.currentTimeMillis())
                : toolResult(toolCall.get("id").getString(), content);
    }

    private static void cancelFuture(Future<ChatMessage> future,
                                     ToolExecutionControl control) {
        if (control != null) control.cancel();
        if (future != null && !future.isDone()) future.cancel(true);
    }

    private static void cancelAllFutures(Future<ChatMessage>[] futures,
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
    private List<ChatMessage> buildAbortedResults(Future<ChatMessage>[] futures,
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
