package site.sorghum.agent4j.bin.agent;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.noear.snack4.ONode;
import site.sorghum.agent4j.bin.builtin.TaskTool;
import site.sorghum.agent4j.bin.config.Agent4jConfig;
import site.sorghum.agent4j.bin.model.ModelClient;
import site.sorghum.agent4j.bin.session.SessionService;
import site.sorghum.agent4j.bin.tool.ToolDispatcher;
import site.sorghum.agent4j.bin.tool.ToolRegistry;
import site.sorghum.agent4j.tool.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    private int maxTotalChars() {
        return config != null ? config.maxContextChars() : 200_000;
    }

    private int keepTailChars() {
        return config != null ? config.keepTailChars() : 80_000;
    }

    private int toolTimeoutSec() {
        return config != null ? config.toolTimeoutSec() : 360;
    }

    private int maxSelfCorrectionAttempts() {
        return config != null ? config.maxSelfCorrectionAttempts() : 5;
    }

    private int maxStreamErrorRetries() {
        return config != null ? config.maxStreamErrorRetries() : 10;
    }

    private int[] retryDelaysSec() {
        return new int[]{1, 1, 1, 3, 3, 3, 5, 7, 10, 10};
    }

    // ==================== 核心字段 ====================

    private final ModelClient client;
    private final ToolDispatcher dispatcher;
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

    /** 流式错误重试次数（每回合重置） */
    private int streamErrorRetryCount = 0;

    @Setter
    @Getter
    private volatile String sessionId;

    // ==================== 构造器 ====================

    public AgentLoop(ModelClient client, ToolRegistry registry, ConversationContext ctx) {
        this(client, registry, ctx, false, null);
    }

    public AgentLoop(ModelClient client, ToolRegistry registry, ConversationContext ctx, boolean hitlDefault) {
        this(client, registry, ctx, hitlDefault, null);
    }

    public AgentLoop(ModelClient client, ToolRegistry registry, ConversationContext ctx, boolean hitlDefault, Agent4jConfig config) {
        this.client = client;
        this.registry = registry;
        this.ctx = ctx;
        this.config = config;
        this.dispatcher = new ToolDispatcher(registry);
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

    /** 获取待审批的工具调用列表（用于 /agree 命令显示） */
    public List<ToolCallEntry> getPendingHITTcList() {
        return hitlManager.getPendingHITTcList();
    }

    /** 获取模型最大上下文窗口 token 数 */
    public int getMaxContextTokens() {
        return client.getMaxContextTokens();
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

    /** 用户主动中断：设置中断标志并中止当前 HTTP 流式请求 */
    public void requestUserAbort() {
        userAbortRequested = true;
        client.abortStream();
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

    // ==================== AgentLoopController 实现 ====================

    @Override
    public AgentOutput getOutput() {
        return this.output;
    }

    @Override
    public void requestStop() {
        client.abortStream();
        log.info("[loop] 工具请求停止推理循环");
    }


    @Override
    public void injectUserMessage(String message) {
        if (message == null || message.isEmpty()) return;
        ctx.addUser("[系统注入] " + message);
        log.info("[loop] 工具注入用户消息: {}...",
                message.length() > 80 ? message.substring(0, 80) + "..." : message);
    }

    // ==================== 内部辅助方法 ====================

    private static ChatMessage toolResult(String id, String result) {
        return ChatMessage.tool(id, result);
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
     * <p>
     * 用户消息会被追加到上下文，工具调用结果也会累积。
     * 下一个回合调用时，上下文已包含上一轮的全部消息。
     * </p>
     */
    public String run(UserMessage userMessage) throws IOException {
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
        if (userMessage != null && !userMessage.hasContent()) {
            ctx.addUser(userMessage);
        }

        // ---- 每回合初始化 ----
        dispatcher.resetStorm();
        reasonBreaker.reset();
        resetUserAbort();
        streamErrorRetryCount = 0;

        // ---- 进入统一的主推理循环 ----
        return mainLoop(client.isThinkingMode(), 0);
    }

    // ==================== 统一主推理循环 ====================

    /**
     * 在工具结果写入上下文后继续推理循环。
     * 被 run() / resumeAfterHITL / resumeAfterSandboxHITL 复用。
     * 消除了原 continueConversationLoop() 与 run() 主循环体的重复代码。
     *
     * @param isThinkingMode        是否为推理模型
     * @param selfCorrectionAttempts 当前自愈尝试次数
     * @return 最终的 assistant content
     */
    private String mainLoop(boolean isThinkingMode, int selfCorrectionAttempts) throws IOException {
        streamErrorRetryCount = 0;
        for (int step = 0; ; step++) {
            // ---- 0. 检查用户中断 ----
            if (userAbortRequested) {
                logAbort();
                String lastContent = ctx.getLastAssistantContent();
                return lastContent != null && !lastContent.isEmpty() ? lastContent : "⏹️ 已停止生成";
            }

            // ---- 0.5. 动态刷新工具列表 ----
            List<Map<String, Object>> tools = refreshTools();

            // ---- 1. 消息准备：构建 + Healing + 折叠 ----
            PreparedMessages prepared = prepareMessages(step, isThinkingMode);
            List<ChatMessage> messages = prepared.messages();

            // ---- 2. 流式调用 LLM ----
            StreamResult sr = streamLLM(messages, tools);

            // ---- 2.1 用户中断 ----
            if (userAbortRequested) {
                try {
                    output.onLog(LogLevel.INFO, "[abort] 用户请求中断（streamLLM 后检测），停止推理循环");
                } catch (Exception e) {
                    log.warn("[abort] output.onLog异常: {}", e.getMessage());
                }
                return handleAbortAfterStream(sr);
            }

            // ---- 3. 流式错误恢复 ----
            if (sr.error()) {
                if (recoverFromStreamError()) continue;
                throw new IOException("[stream] API error during streaming");
            }

            try {
                output.onContentComplete();
            } catch (Exception e) {
                log.debug("[output] onContentComplete异常(SSE可能已断开): {}", e.getMessage());
            }

            // ---- 推理断路器 ----
            if (sr.loopAborted()) {
                ctx.addUser("[ReasonBreaker] 检测到思考循环，已提前终止本轮推理。请停止当前思路，尝试不同的方法。");
                continue;
            }

            // ---- 4. 从 reasoning 中回收丢失的工具调用 ----
            ONode toolCalls = scavengeToolCalls(sr.toolCalls(), sr.reasoningContent(), sr.content());
            boolean hasToolCalls = toolCalls != null && toolCalls.isArray() && !toolCalls.getArray().isEmpty();

            // ---- 5. 无 tool_calls → 返回文本回复 ----
            if (!hasToolCalls) {
                return handleTextResponse(sr.content(), sr.reasoningContent());
            }

            // ---- HITL 拦截 ----
            if (hitlManager.isHitlMode()) {
                return hitlManager.interceptForHITL(toolCalls, sr.content(), sr.reasoningContent(), output);
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
        dispatcher.resetStorm();

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
        return mainLoop(client.isThinkingMode(), 0);
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

        dispatcher.resetStorm();
        reasonBreaker.reset();
        resetUserAbort();

        // 沙箱旁路执行
        ToolExecutionResult initialTer = executeToolCalls(state.toolCalls(), true);

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

        return mainLoop(client.isThinkingMode(), 0);
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

    private PreparedMessages prepareMessages(int step, boolean isThinkingMode) throws IOException {
        List<ChatMessage> messages = ctx.buildMessages();
        MessageHealer.HealResult healResult = MessageHealer.heal(messages, isThinkingMode);
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
            withInstr.add(ChatMessage.user(instr));
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

        client.chatStream(messages, tools, new ModelClient.StreamCallback() {
            @Override
            public void onReasoningDelta(String token) {
                if (loopAborted.get() || userAbortRequested) return;
                reasoningBuf.append(token);
                try {
                    output.onReasoningDelta(token);
                } catch (Exception e) {
                    log.debug("[stream] onReasoningDelta异常(SSE可能已断开): {}", e.getMessage());
                }
                // 流式增量检测：每 500 字符检查一次思考循环
                int newLen = reasoningBuf.length();
                if (newLen - lastCheckLen[0] >= 500) {
                    lastCheckLen[0] = newLen;
                    ReasonBreaker.LoopResult lr = reasonBreaker.analyze(reasoningBuf.toString());
                    if (lr.looping) {
                        loopSnapshot[0] = reasoningBuf.toString();
                        loopAborted.set(true);
                        try {
                            output.onLog(LogLevel.WARN, "[ReasonBreaker] " + lr.toWarning());
                        } catch (Exception ex) {
                            log.warn("[ReasonBreaker] output.onLog异常: {}", ex.getMessage());
                        }
                        client.abortStream();
                        streamLatch.countDown();
                    }
                }
            }

            @Override
            public void onContentDelta(String token) {
                contentBuf.append(token);
                try {
                    output.onContentDelta(token);
                } catch (Exception e) {
                    log.debug("[stream] onContentDelta异常(SSE可能已断开): {}", e.getMessage());
                }
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
                try {
                    listener.onUsage(currentModel, promptTokens, completionTokens, totalTokens, cacheHit, cacheMiss);
                } catch (Exception e) {
                    log.warn("[usage] listener.onUsage异常: {}", e.getMessage());
                }
                try {
                    output.onUsage(currentModel, promptTokens, completionTokens, totalTokens, cacheHit, cacheMiss);
                } catch (Exception e) {
                    log.debug("[usage] output.onUsage异常(SSE可能已断开): {}", e.getMessage());
                }
            }

            @Override
            public void onDone() {
                streamLatch.countDown();
            }

            @Override
            public void onError(String err) {
                streamError.set(true);
                try {
                    output.onError("[stream error] " + err);
                } catch (Exception e) {
                    log.debug("[stream] onError异常(SSE可能已断开): {}", e.getMessage());
                }
                streamLatch.countDown();
            }
        });

        // 等待流结束
        try {
            streamLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (userAbortRequested) {
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

    // ==================== 流式错误恢复 ====================

    private boolean recoverFromStreamError() {
        streamErrorRetryCount++;
        if (streamErrorRetryCount <= maxStreamErrorRetries()) {
            int delay = retryDelaysSec()[streamErrorRetryCount - 1];
            try {
                output.onLog(LogLevel.WARN, "[recover] API 流式错误，第 " + streamErrorRetryCount
                        + "/" + maxStreamErrorRetries() + " 次重试，等待 " + delay + " 秒...");
            } catch (Exception e) {
                log.warn("[recover] output.onLog异常: {}", e.getMessage());
            }
            try {
                Thread.sleep(delay * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            return true;
        }
        try {
            output.onLog(LogLevel.ERROR, "[recover] 流式错误已达重试上限（" + maxStreamErrorRetries() + "次），放弃");
        } catch (Exception e) {
            log.warn("[recover] output.onLog异常: {}", e.getMessage());
        }
        return false;
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

    // ==================== 步骤 5: 纯文本响应 ====================

    private String handleTextResponse(String content, String reasoningContent) {
        if (content == null || content.isEmpty()) {
            if (reasoningContent != null && !reasoningContent.isEmpty()) {
                try {
                    listener.onReasoning(reasoningContent);
                } catch (Exception e) {
                    log.warn("[text] listener.onReasoning异常: {}", e.getMessage());
                }
                try {
                    output.onReasoning(reasoningContent);
                } catch (Exception e) {
                    log.debug("[text] output.onReasoning异常(SSE可能已断开): {}", e.getMessage());
                }
                ctx.addAssistant(null, null, reasoningContent);
                return reasoningContent;
            }
        }
        ctx.addAssistant(content, null, reasoningContent);
        return content != null ? content : "(empty response)";
    }

    // ==================== 步骤 6: 工具执行 ====================

    private ToolExecutionResult executeToolCalls(ONode toolCalls) {
        return executeToolCalls(toolCalls, false);
    }

    @SuppressWarnings("unchecked")
    private ToolExecutionResult executeToolCalls(ONode toolCalls, boolean skipSandboxCheck) {
        dispatcher.setSessionId(this.sessionId);

        ONode[] tcArray = toolCalls.getArray().toArray(new ONode[0]);

        // 1. 解析 tcList，过滤无效调用
        List<ToolCallEntry> tcList = new ArrayList<>();
        List<ONode> filteredTcList = new ArrayList<>();
        for (ONode tc : tcArray) {
            String tcId = tc.get("id").getString();
            ONode func = tc.get("function");
            String tcName = func.get("name").getString();
            if (tcName == null || tcName.isEmpty()) {
                try {
                    output.onLog(LogLevel.WARN, "跳过无效 tool call: name=" + tcName + " id=" + tcId);
                } catch (Exception e) {
                    log.warn("[tool] output.onLog异常: {}", e.getMessage());
                }
                continue;
            }
            String tcArgs = func.get("arguments").getString();
            if (tcArgs == null) tcArgs = "{}";

            tcList.add(new ToolCallEntry(tcId, tcName, tcArgs));
            filteredTcList.add(tc);

            try {
                listener.onToolCall(tcName, tcArgs);
            } catch (Exception e) {
                log.warn("[tool] listener.onToolCall异常: {}", e.getMessage());
            }
            try {
                output.onToolCall(tcName, tcArgs);
            } catch (Exception e) {
                log.debug("[tool] output.onToolCall异常(SSE可能已断开): {}", e.getMessage());
            }
        }

        final ONode[] finalTcArray = filteredTcList.toArray(new ONode[0]);
        int tcCount = finalTcArray.length;

        TaskTool.clearUsageCollector();

        // 2. 并行分发
        CompletableFuture<ChatMessage>[] futures = new CompletableFuture[tcCount];
        final AtomicBoolean anySuppressed = new AtomicBoolean(false);
        final AtomicReference<HitlRequiredException> hitlRef = new AtomicReference<>(null);
        final AgentOutput capturedOutput = this.output;
        for (int i = 0; i < tcCount; i++) {
            final int idx = i;
            futures[i] = CompletableFuture.supplyAsync(() -> {
                if (skipSandboxCheck) {
                    ToolContext.enableSandboxBypass();
                }
                if (capturedOutput != null) {
                    TaskTool.setCurrentOutput(capturedOutput);
                }
                try {
                    ONode tc = finalTcArray[idx];
                    String tcId = tc.get("id").getString();
                    ONode func = tc.get("function");
                    String tcName = func.get("name").getString();
                    String tcArgs = func.get("arguments").getString();
                    if (tcArgs == null) tcArgs = "{}";
                    try {
                        String result = dispatcher.dispatch(tcName, tcArgs, AgentLoop.this);
                        if (result != null && result.contains("\"rejectedReason\":\"storm\"")) {
                            anySuppressed.set(true);
                        }
                        try {
                            listener.onToolResult(tcName, result);
                        } catch (Exception e) {
                            log.warn("[tool] listener.onToolResult异常: {}", e.getMessage());
                        }
                        try {
                            output.onToolResult(tcName, result);
                        } catch (Exception e) {
                            log.debug("[tool] output.onToolResult异常(SSE可能已断开): {}", e.getMessage());
                        }
                        return toolResult(tcId, result);
                    } catch (HitlRequiredException e) {
                        hitlRef.set(e);
                        return ChatMessage.tool(tcId, "[HITL_PENDING:" + e.getReason() + "] " + e.getDetails());
                    }
                } finally {
                    if (skipSandboxCheck) {
                        ToolContext.disableSandboxBypass();
                    }
                    TaskTool.clearCurrentOutput();
                }
            });
        }

        // 3. 等待全部完成（带超时保护）
        try {
            CompletableFuture.allOf(futures).get(toolTimeoutSec(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            try {
                output.onLog(LogLevel.WARN, "[tool] 工具执行超时（" + toolTimeoutSec() + "s），取消未完成的调用");
            } catch (Exception ex) {
                log.warn("[tool] output.onLog异常: {}", ex.getMessage());
            }
            for (CompletableFuture<ChatMessage> f : futures) {
                if (!f.isDone()) f.cancel(true);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            // 继续逐个收集结果
        }

        List<ChatMessage> toolResults = new ArrayList<>();
        for (int i = 0; i < futures.length; i++) {
            CompletableFuture<ChatMessage> f = futures[i];
            try {
                toolResults.add(f.get());
            } catch (CancellationException e) {
                ONode tc = finalTcArray[i];
                String tcId = tc.get("id").getString();
                toolResults.add(toolResult(tcId, "{\"error\":\"工具执行超时（" + toolTimeoutSec() + "s）\",\"rejectedReason\":\"timeout\"}"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                toolResults.add(toolResult("?", "[ERROR] Interrupted"));
            } catch (ExecutionException e) {
                toolResults.add(toolResult("?", "[ERROR] " + e.getMessage()));
            }
        }

        // 3.1 沙箱越界 HITL
        HitlRequiredException hitlEx = hitlRef.get();
        if (hitlEx != null) {
            hitlManager.setSandboxPending(toolCalls, hitlEx.getDetails());
            try {
                output.onLog(LogLevel.WARN, "[hitl] 沙箱越界触发强制审批: " + hitlEx.getDetails());
            } catch (Exception e) {
                log.warn("[hitl] output.onLog异常: {}", e.getMessage());
            }
        }

        // 收集子代理 token 用量
        if (sessionService != null) {
            var subUsage = TaskTool.drainUsageCollector();
            for (var ur : subUsage) {
                sessionService.addUsage(ur.model(),
                        (int) ur.prompt(), (int) ur.completion(),
                        (int) ur.cacheHit(), (int) ur.cacheMiss());
            }
        }

        return new ToolExecutionResult(tcList, toolResults, anySuppressed.get());
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
            try {
                output.onLog(LogLevel.WARN,
                        "[self-correct] 已达自愈尝试上限（" + maxSelfCorrectionAttempts() + "次），停止循环");
            } catch (Exception e) {
                log.warn("[self-correct] output.onLog异常: {}", e.getMessage());
            }
            return -1;
        }

        try {
            output.onLog(LogLevel.INFO,
                    "[self-correct] 所有工具调用被 storm 抑制，第" + selfCorrectionAttempts + "次自愈尝试");
        } catch (Exception e) {
            log.warn("[self-correct] output.onLog异常: {}", e.getMessage());
        }
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
        try {
            output.onLog(LogLevel.INFO, "[abort] 用户请求中断，停止推理循环");
        } catch (Exception e) {
            log.warn("[abort] output.onLog异常: {}", e.getMessage());
        }
    }
}
