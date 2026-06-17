package site.sorghum.agent4j.bin.agent.loop;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.noear.snack4.ONode;
import site.sorghum.agent4j.bin.agent.breaker.ReasonBreaker;
import site.sorghum.agent4j.bin.agent.context.ContextFolding;
import site.sorghum.agent4j.bin.agent.context.ConversationContext;
import site.sorghum.agent4j.bin.agent.context.MessageHealer;
import site.sorghum.agent4j.bin.agent.context.Scavenger;
import site.sorghum.agent4j.bin.agent.hitl.HitlManager;
import site.sorghum.agent4j.bin.agent.hitl.HitlState;
import site.sorghum.agent4j.bin.agent.model.*;
import site.sorghum.agent4j.bin.agent.patrol.GoalPatrolManager;
import site.sorghum.agent4j.bin.config.Agent4jConfig;
import site.sorghum.agent4j.bin.goal.Goal;
import site.sorghum.agent4j.bin.goal.GoalStep;
import site.sorghum.agent4j.bin.goal.GoalStore;
import site.sorghum.agent4j.bin.goal.StepStatus;
import site.sorghum.agent4j.bin.model.ModelClient;
import site.sorghum.agent4j.bin.session.SessionService;
import site.sorghum.agent4j.bin.tool.ToolDispatcher;
import site.sorghum.agent4j.bin.tool.ToolRegistry;
import site.sorghum.agent4j.tool.AgentLoopController;
import site.sorghum.agent4j.tool.AgentOutput;
import site.sorghum.agent4j.tool.LogLevel;
import site.sorghum.agent4j.tool.interact.FinishTool;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Agent 循环 —— 编排 prompt → LLM → 工具调用 → 反馈结果 → LLM 的循环。
 * <p>
 * 消息历史通过 {@link ConversationContext} 在内存中累积跨回合持久化。
 * HITL（人工审批）逻辑委托给 {@link HitlManager} 管理。
 * 工具执行委托给 {@link ToolExecutor} 管理。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
public class AgentLoop implements AgentLoopController {

    // ==================== 核心字段 ====================

    private final ModelClient client;
    private final ToolDispatcher dispatcher;
    private final ToolRegistry registry;
    private final LoopConfig config;
    @Getter
    private final ConversationContext ctx;
    private final ReasonBreaker reasonBreaker = new ReasonBreaker();
    @Getter
    private final HitlManager hitlManager;
    private final GoalPatrolManager patrolManager;
    private final ToolExecutor toolExecutor;

    @Setter
    private SessionService sessionService;

    private AgentLoopListener listener = NoOpAgentLoopListener.INSTANCE;

    @Getter
    private AgentOutput output = new site.sorghum.agent4j.bin.agent.output.ConsoleAgentOutput();

    /** 最近一次 API 返回的 prompt_tokens（0 = 尚无数据，回退到字符估算） */
    @Getter
    private int lastPromptTokens = 0;

    /** 用户主动中断标志（前端点击停止按钮时设置） */
    private final AtomicBoolean userAbortRequested = new AtomicBoolean(false);

    /** 外部中断源（Runnable）—— 由父级 AgentLoopController 设置，子代理主循环会同步检查 */
    private volatile Runnable externalAbortSource = null;

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
                     boolean hitlDefault, Agent4jConfig agent4jConfig) {
        this.client = client;
        this.registry = registry;
        this.ctx = ctx;
        this.config = new LoopConfig(agent4jConfig);
        this.dispatcher = new ToolDispatcher(registry);
        this.hitlManager = new HitlManager(hitlDefault);
        this.patrolManager = new GoalPatrolManager(null, ctx, running);
        this.toolExecutor = new ToolExecutor(
                dispatcher, this, output, listener,
                hitlManager, sessionService, ctx, config,
                userAbortRequested
        );
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

    public boolean isPlanMode() {
        return dispatcher.isPlanMode();
    }

    public void setPlanMode(boolean on) {
        dispatcher.setPlanMode(on);
    }

    /** 用户主动中断 */
    public void requestUserAbort() {
        doAbort();
    }

    @Override
    public boolean isAbortRequested() {
        return userAbortRequested.get();
    }

    /** 重置用户中断标志（每回合开始时调用） */
    public void resetUserAbort() {
        userAbortRequested.set(false);
    }

    public void setListener(AgentLoopListener listener) {
        this.listener = listener != null ? listener : NoOpAgentLoopListener.INSTANCE;
    }

    public void setOutput(AgentOutput output) {
        this.output = output != null ? output : AgentOutput.NOOP;
    }

    /**
     * 设置外部中断源 —— 子代理通过此方法绑定父级的 AgentLoopController。
     */
    public void setExternalAbortSource(AgentLoopController parentController) {
        if (parentController == null) {
            this.externalAbortSource = null;
            return;
        }
        this.externalAbortSource = () -> {
            if (parentController.isAbortRequested() && !userAbortRequested.get()) {
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
        userAbortRequested.set(true);
        client.abortStream();
        toolExecutor.cancelActiveFutures();
    }

    @Override
    public void finish(String content) {
        if (content == null || content.isBlank()) {
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
        if (message == null || message.isEmpty()) {
            log.warn("[loop] 工具试图注入空消息，已忽略");
            return;
        }
        ctx.addUser("[系统注入] " + message);
        log.info("[loop] 工具注入用户消息: {}...",
                message.length() > 80 ? message.substring(0, 80) + "..." : message);
    }

    // ==================== 内部辅助方法 ====================

    /** 安全调用 output 方法，异常记录 warn 日志 */
    private void safeOutput(String tag, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("[{}] output异常: {}", tag, e.getMessage());
        }
    }

    /** 安全调用 output 方法，异常记录 debug 日志 */
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

    private List<Map<String, Object>> refreshTools() {
        registry.refresh();
        return registry.toOpenAiTools();
    }

    private String buildToolInstructions() {
        return """
                编辑文件时使用 edit_file（SEARCH/REPLACE，search 必须唯一）。
                多文件批量编辑使用 multi_edit。
                不确定文件位置时用 glob/grep 搜索，需要构建/测试时用 run_command。
                """;
    }

    // ==================== 主入口：run() ====================

    /**
     * 执行一个用户回合，返回最终的 assistant content。
     */
    public String run(UserMessage userMessage) throws IOException {
        running.set(true);
        try {
            return doRun(userMessage);
        } finally {
            running.set(false);
        }
    }

    private String doRun(UserMessage userMessage) throws IOException {
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
        dispatcher.resetStorm();
        reasonBreaker.reset();
        resetUserAbort();

        // ---- 进入统一的主推理循环（含自动重试闭环） ----
        return runWithAutoRetry();
    }

    /**
     * 执行主推理循环，并在完成后检查目标步骤是否需要自动重试。
     */
    private String runWithAutoRetry() throws IOException {
        String result = mainLoop();

        // === 自动重试闭环 ===
        boolean hasActiveGoal = false;
        while (true) {
            GoalPatrolManager.GoalAndStore gs = patrolManager.findRetriableGoal();
            if (gs == null) break;

            hasActiveGoal = true;
            Goal goal = gs.goal();
            GoalStore goalStore = gs.goalStore();

            GoalStep failedStep = null;
            for (GoalStep s : goal.getSteps()) {
                if (s.getStatus() == StepStatus.FAILED
                        && s.getRetryCount() < goal.getMaxRetries()) {
                    failedStep = s;
                    break;
                }
            }
            if (failedStep == null) break;

            failedStep.setStatus(StepStatus.PENDING);
            failedStep.setRetryCount(failedStep.getRetryCount() + 1);
            goal.setUpdatedAt(java.time.Instant.now());
            goalStore.save(goal);

            log.info("[goal] 自动重试: step={}, retry={}/{}",
                    failedStep.getIndex() + 1,
                    failedStep.getRetryCount(),
                    goal.getMaxRetries());

            ctx.addUser(
                    "⚠️ [系统自动重试] 上一步执行失败，正在重试（"
                    + failedStep.getRetryCount() + "/" + goal.getMaxRetries() + "）。\n\n"
                    + "### 需要重试的步骤\n"
                    + "步骤 " + (failedStep.getIndex() + 1) + "：" + failedStep.getDescription() + "\n"
                    + (failedStep.getLastError() != null
                        ? "### 上一次失败原因\n" + failedStep.getLastError() + "\n"
                        : "")
                    + "\n请重新执行此步骤。注意分析上次失败的原因，避免同样的错误。");

            result = mainLoop();
        }

        if (!hasActiveGoal) {
            if (patrolManager.isRunning()) {
                patrolManager.tick();
            }
        } else {
            patrolManager.startPatrol();
        }

        return result;
    }

    // ==================== 统一主推理循环 ====================

    /**
     * 在工具结果写入上下文后继续推理循环。
     * 被 run() / resumeAfterHITL / resumeAfterSandboxHITL 复用。
     */
    private String mainLoop() throws IOException {
        int noToolCallStreak = 0;
        int selfCorrectionAttempts = 0;
        for (int step = 0; ; step++) {
            // ---- 0. 同步外部中断源 ----
            Runnable extSource = externalAbortSource;
            if (extSource != null) {
                extSource.run();
            }

            // ---- 0.1. 检查用户中断 ----
            if (userAbortRequested.get() || Thread.currentThread().isInterrupted()) {
                if (!userAbortRequested.get()) {
                    userAbortRequested.set(true);
                }
                logAbort();
                String lastContent = ctx.getLastAssistantContent();
                return lastContent != null && !lastContent.isEmpty() ? lastContent : "⏹️ 已停止生成";
            }

            // ---- 0.5. 动态刷新工具列表 ----
            List<Map<String, Object>> tools = refreshTools();

            // ---- 1. 消息准备：构建 + Healing + 折叠 ----
            PreparedMessages prepared = prepareMessages(step);
            List<ChatMessage> messages = prepared.messages();

            // ---- 2. 流式调用 LLM ----
            StreamResult sr = streamLLM(messages, tools);

            // ---- 2.05. 同步外部中断源 ----
            Runnable extSource2 = externalAbortSource;
            if (extSource2 != null) {
                extSource2.run();
            }

            // ---- 2.1 用户中断 ----
            if (userAbortRequested.get() || Thread.currentThread().isInterrupted()) {
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

            // ---- 5. 无 tool calls ----
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

                ctx.addUser(FinishTool.TIPS);
                continue;
            }

            // ---- 调用了工具 → 重置无工具计数 ----
            noToolCallStreak = 0;

            // ---- HITL 拦截 ----
            if (hitlManager.isHitlMode()) {
                return hitlManager.interceptForHITL(toolCalls, sr.content(), sr.reasoningContent(), output);
            }

            // ---- 6. 并行执行工具调用 ----
            ToolExecutionResult ter = toolExecutor.execute(toolCalls);

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
            int updated = toolExecutor.handleSelfCorrection(ter.toolResults(), ter.anySuppressed(), selfCorrectionAttempts);
            if (updated < 0) {
                String fallback = "所有工具调用均被风暴断路器抑制，无法继续执行。请换用其他方式完成任务。";
                ctx.addAssistant(fallback, null, null);
                return fallback;
            }
            selfCorrectionAttempts = updated;
        }
    }

    // ==================== HITL 恢复 ====================

    private String resumeAfterHITL(boolean approved) throws IOException {
        HitlManager.PendingHITLState state = hitlManager.drainPendingHITL();

        if (!approved) {
            String denyMsg = "工具调用已被用户拒绝。";
            ctx.addAssistant(denyMsg, null, null);
            return denyMsg;
        }

        ctx.addAssistant(state.content(), state.tcList(), state.reasoningContent());
        dispatcher.resetStorm();

        ToolExecutionResult ter = toolExecutor.execute(state.toolCalls());

        if (hitlManager.getState() == HitlState.PENDING && hitlManager.hasSandboxPending()) {
            hitlManager.storeSandboxContent(state.content(), state.reasoningContent());
            return hitlManager.interceptForSandboxHITL(output);
        }

        for (ChatMessage tr : ter.toolResults()) {
            ctx.addToolResult(tr.getToolCallId(), tr.getContent());
        }

        return mainLoop();
    }

    private String resumeAfterSandboxHITL(boolean approved) throws IOException {
        HitlManager.PendingSandboxState state = hitlManager.drainSandboxHITL();

        if (!approved) {
            List<ToolCallEntry> tcList = ToolExecutor.parseToolCallsFromONode(state.toolCalls());
            ctx.addAssistant(state.content(), tcList, state.reasoningContent());
            String denyMsg = "沙箱越界已被用户拒绝。";
            ctx.addAssistant(denyMsg, null, null);
            return denyMsg;
        }

        List<ToolCallEntry> tcList = ToolExecutor.parseToolCallsFromONode(state.toolCalls());
        ctx.addAssistant(state.content(), tcList, state.reasoningContent());

        dispatcher.resetStorm();
        reasonBreaker.reset();
        resetUserAbort();

        ToolExecutionResult initialTer = toolExecutor.execute(state.toolCalls(), true);

        for (ChatMessage tr : initialTer.toolResults()) {
            ctx.addToolResult(tr.getToolCallId(), tr.getContent());
        }

        int scAttempts = toolExecutor.handleSelfCorrection(initialTer.toolResults(), initialTer.anySuppressed(), 0);
        if (scAttempts < 0) {
            String fallback = "所有工具调用均被风暴断路器抑制，无法继续执行。";
            ctx.addAssistant(fallback, null, null);
            return fallback;
        }

        return mainLoop();
    }

    // ==================== 步骤 1: 消息准备 ====================

    private PreparedMessages prepareMessages(int step) throws IOException {
        List<ChatMessage> messages = ctx.buildMessages();
        MessageHealer.HealResult healResult = MessageHealer.heal(messages);
        messages = healResult.messages();

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
            messages = ContextFolding.fold(messages, config.maxTotalChars(), config.keepTailChars(), client);
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

        String instr = buildToolInstructions();
        if (!instr.isEmpty()) {
            List<ChatMessage> withInstr = new ArrayList<>(messages.size() + 1);
            withInstr.add(messages.get(0));
            withInstr.add(ChatMessage.ofUser(instr));
            withInstr.addAll(messages.subList(1, messages.size()));
            messages = withInstr;
        }

        return new PreparedMessages(messages, foldedThisStep);
    }

    // ==================== 步骤 2: 流式调用 LLM ====================

    private StreamResult streamLLM(List<ChatMessage> messages, List<Map<String, Object>> tools) {
        final StringBuilder contentBuf = new StringBuilder();
        final StringBuilder reasoningBuf = new StringBuilder();
        final ONode[] streamedTcs = {null};
        final CountDownLatch streamLatch = new CountDownLatch(1);
        final AtomicBoolean streamError = new AtomicBoolean(false);
        final AtomicBoolean loopAborted = new AtomicBoolean(false);
        final String[] loopSnapshot = {null};
        final int[] lastCheckLen = {0};
        final Runnable capturedExtAbort = externalAbortSource;

        client.chatStream(messages, tools, new ModelClient.StreamCallback() {
            @Override
            public void onReasoningDelta(String token) {
                if (capturedExtAbort != null) capturedExtAbort.run();
                if (loopAborted.get() || userAbortRequested.get() || Thread.currentThread().isInterrupted()) return;
                reasoningBuf.append(token);
                safeOutputDebug("reasoningDelta", () -> output.onReasoningDelta(token));
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
                streamedTcs[0] = tcs;
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

        try {
            boolean finished = streamLatch.await(LoopConfig.DEFAULT_STREAM_LATCH_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!finished) {
                log.error("[stream] LLM 流式响应超时（{}s），主动终止", LoopConfig.DEFAULT_STREAM_LATCH_TIMEOUT_SEC);
                client.abortStream();
                return new StreamResult(null, null, null, true);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new StreamResult(null, null, null, true);
        }

        if (capturedExtAbort != null) capturedExtAbort.run();

        if (userAbortRequested.get()) {
            String content = !contentBuf.isEmpty() ? contentBuf.toString() : null;
            String reasoningContent = !reasoningBuf.isEmpty() ? reasoningBuf.toString() : null;
            return new StreamResult(content, reasoningContent, streamedTcs[0], false);
        }
        if (streamError.get()) {
            return new StreamResult(null, null, null, true);
        }
        if (loopAborted.get()) {
            String reasoning = loopSnapshot[0] != null ? loopSnapshot[0]
                    : (!reasoningBuf.isEmpty() ? reasoningBuf.toString() : null);
            return new StreamResult(null, reasoning, streamedTcs[0], false, true);
        }
        String content = !contentBuf.isEmpty() ? contentBuf.toString() : null;
        String reasoningContent = !reasoningBuf.isEmpty() ? reasoningBuf.toString() : null;
        return new StreamResult(content, reasoningContent, streamedTcs[0], false);
    }

    // ==================== Scavenger 回收 ====================

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

    // ==================== 内部辅助 ====================

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
}
