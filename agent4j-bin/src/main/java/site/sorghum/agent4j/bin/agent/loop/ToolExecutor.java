package site.sorghum.agent4j.bin.agent.loop;

import lombok.extern.slf4j.Slf4j;
import org.noear.snack4.ONode;
import site.sorghum.agent4j.bin.agent.context.ConversationContext;
import site.sorghum.agent4j.bin.agent.hitl.HitlManager;
import site.sorghum.agent4j.bin.agent.model.ChatMessage;
import site.sorghum.agent4j.bin.agent.model.ToolCallEntry;
import site.sorghum.agent4j.bin.agent.model.ToolExecutionResult;
import site.sorghum.agent4j.bin.builtin.TaskTool;
import site.sorghum.agent4j.bin.session.SessionService;
import site.sorghum.agent4j.bin.tool.ToolDispatcher;
import site.sorghum.agent4j.tool.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 工具执行器 —— 从 {@link AgentLoop} 中提取的工具调用执行逻辑。
 * <p>
 * 负责工具调用的解析、过滤、异步并行分发、结果收集和自愈逻辑。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
public class ToolExecutor {

    private final ToolDispatcher dispatcher;
    private final AgentLoopController loopController;
    private final AgentOutput output;
    private final AgentLoopListener listener;
    private final HitlManager hitlManager;
    private final SessionService sessionService;
    private final ConversationContext ctx;
    private final LoopConfig config;
    private final AtomicBoolean userAbortRequested;

    /** 当前正在执行的工具 Future 数组（用于 abort 时取消） */
    private volatile CompletableFuture<ChatMessage>[] activeToolFutures = null;

    private volatile String sessionId;

    public ToolExecutor(ToolDispatcher dispatcher, AgentLoopController loopController,
                        AgentOutput output, AgentLoopListener listener,
                        HitlManager hitlManager, SessionService sessionService,
                        ConversationContext ctx, LoopConfig config,
                        AtomicBoolean userAbortRequested) {
        this.dispatcher = dispatcher;
        this.loopController = loopController;
        this.output = output;
        this.listener = listener;
        this.hitlManager = hitlManager;
        this.sessionService = sessionService;
        this.ctx = ctx;
        this.config = config;
        this.userAbortRequested = userAbortRequested;
    }

    /** 设置会话 ID（由 AgentLoop 同步） */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /** 取消正在执行的工具 Future（由 AgentLoop 的中断逻辑调用） */
    public void cancelActiveFutures() {
        CompletableFuture<ChatMessage>[] futures = activeToolFutures;
        if (futures != null) {
            for (CompletableFuture<ChatMessage> f : futures) {
                if (f != null && !f.isDone()) {
                    f.cancel(true);
                }
            }
        }
    }

    // ==================== 工具执行入口 ====================

    public ToolExecutionResult execute(ONode toolCalls) {
        return execute(toolCalls, false);
    }

    public ToolExecutionResult execute(ONode toolCalls, boolean skipSandboxCheck) {
        dispatcher.setSessionId(this.sessionId);

        ONode[] tcArray = toolCalls.getArray().toArray(new ONode[0]);

        // 1. 解析并过滤工具调用
        ParsedToolCalls parsed = parseAndFilterToolCalls(tcArray);
        final ONode[] finalTcArray = parsed.nodeList().toArray(new ONode[0]);

        TaskTool.clearUsageCollector();

        // 2. 异步并行分发
        DispatchResult dispatch = dispatchToolCallsAsync(finalTcArray, skipSandboxCheck);

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

    // ==================== 自愈逻辑 ====================

    public int handleSelfCorrection(List<ChatMessage> toolResults,
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
        if (selfCorrectionAttempts > config.maxSelfCorrectionAttempts()) {
            safeOutput("selfCorrect", () -> output.onLog(LogLevel.WARN,
                    "[self-correct] 已达自愈尝试上限（" + config.maxSelfCorrectionAttempts() + "次），停止循环"));
            return -1;
        }

        final int currentAttempts = selfCorrectionAttempts;
        safeOutput("selfCorrect", () -> output.onLog(LogLevel.INFO,
                "[self-correct] 所有工具调用被 storm 抑制，第" + currentAttempts + "次自愈尝试"));
        ctx.addUser("[系统提示：你刚刚重复调用了相同的工具。请换一种方式完成任务，"
                + "或直接用文本回答。]");
        return selfCorrectionAttempts;
    }

    // ==================== 内部解析 ====================

    private record ParsedToolCalls(List<ToolCallEntry> tcList, List<ONode> nodeList) {}

    /**
     * 解析并过滤工具调用列表，同时触发 onToolCall 回调。
     */
    private ParsedToolCalls parseAndFilterToolCalls(ONode[] tcArray) {
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

    // ==================== 异步分发 ====================

    /**
     * 异步并行分发所有工具调用，返回 Future 数组和共享状态引用。
     */
    private record DispatchResult(CompletableFuture<ChatMessage>[] futures,
                                  AtomicBoolean anySuppressed,
                                  AtomicReference<HitlRequiredException> hitlRef) {}

    private DispatchResult dispatchToolCallsAsync(ONode[] tcArray, boolean skipSandboxCheck) {
        int tcCount = tcArray.length;
        @SuppressWarnings("unchecked")
        CompletableFuture<ChatMessage>[] futures = new CompletableFuture[tcCount];
        final AtomicBoolean anySuppressed = new AtomicBoolean(false);
        final AtomicReference<HitlRequiredException> hitlRef = new AtomicReference<>(null);
        final AgentOutput capturedOutput = this.output;
        for (int i = 0; i < tcCount; i++) {
            final int idx = i;
            futures[i] = CompletableFuture.supplyAsync(() -> {
                // 用户已中断 → 立即返回，不执行工具
                if (userAbortRequested.get()) {
                    ONode tc = tcArray[idx];
                    String tcId = tc.get("id").getString();
                    return toolResult(tcId,
                            "{\"error\":\"用户已中断\",\"aborted\":true}");
                }

                if (skipSandboxCheck) {
                    ToolContext.enableSandboxBypass();
                }
                if (capturedOutput != null) {
                    TaskTool.setCurrentOutput(capturedOutput);
                }
                try {
                    ONode tc = tcArray[idx];
                    String tcId = tc.get("id").getString();
                    ONode func = tc.get("function");
                    String tcName = func.get("name").getString();
                    String tcArgs = func.get("arguments").getString();
                    if (tcArgs == null) tcArgs = "{}";
                    try {
                        String result = dispatcher.dispatch(tcName, tcArgs, loopController);
                        if (result != null && result.contains("\"rejectedReason\":\"storm\"")) {
                            anySuppressed.set(true);
                        }
                        safeListener("toolResult", () -> listener.onToolResult(tcName, result));
                        safeOutputDebug("toolResult", () -> output.onToolResult(tcName, result));
                        return toolResult(tcId, result);
                    } catch (HitlRequiredException e) {
                        hitlRef.set(e);
                        return ChatMessage.tool(tcId,
                                "[HITL_PENDING:" + e.getReason() + "] " + e.getDetails());
                    }
                } finally {
                    if (skipSandboxCheck) {
                        ToolContext.disableSandboxBypass();
                    }
                    TaskTool.clearCurrentOutput();
                }
            });
        }
        return new DispatchResult(futures, anySuppressed, hitlRef);
    }

    // ==================== 结果收集 ====================

    /**
     * 等待所有工具 Future 完成（带超时保护），收集结果。
     */
    private List<ChatMessage> collectToolResults(CompletableFuture<ChatMessage>[] futures,
                                                  ONode[] tcArray) {
        this.activeToolFutures = futures;
        try {
            if (userAbortRequested.get()) {
                cancelAllFutures(futures);
                return buildAbortedResults(futures, tcArray);
            }

            CompletableFuture.allOf(futures).get(config.toolTimeoutSec(), TimeUnit.SECONDS);

            if (userAbortRequested.get()) {
                cancelAllFutures(futures);
                return buildAbortedResults(futures, tcArray);
            }
        } catch (TimeoutException e) {
            safeOutput("toolTimeout", () -> output.onLog(LogLevel.WARN,
                    "[tool] 工具执行超时（" + config.toolTimeoutSec() + "s），取消未完成的调用"));
            cancelAllFutures(futures);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cancelAllFutures(futures);
            return buildAbortedResults(futures, tcArray);
        } catch (CancellationException e) {
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
                ONode tc = tcArray[i];
                String tcId = tc.get("id").getString();
                toolResults.add(toolResult(tcId,
                        "{\"error\":\"工具执行超时（" + config.toolTimeoutSec()
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

    private void cancelAllFutures(CompletableFuture<ChatMessage>[] futures) {
        for (CompletableFuture<ChatMessage> f : futures) {
            if (f != null && !f.isDone()) {
                f.cancel(true);
            }
        }
    }

    private List<ChatMessage> buildAbortedResults(CompletableFuture<ChatMessage>[] futures,
                                                   ONode[] tcArray) {
        List<ChatMessage> results = new ArrayList<>();
        for (int i = 0; i < futures.length; i++) {
            ONode tc = tcArray[i];
            String tcId = tc.get("id").getString();
            results.add(toolResult(tcId,
                    "{\"error\":\"用户已中断\",\"aborted\":true}"));
        }
        return results;
    }

    // ==================== 静态工具方法 ====================

    public static List<ToolCallEntry> parseToolCallsFromONode(ONode toolCalls) {
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

    // ==================== 安全调用辅助 ====================

    private void safeOutput(String tag, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("[{}] output异常: {}", tag, e.getMessage());
        }
    }

    private void safeOutputDebug(String tag, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.debug("[{}] output异常(SSE可能已断开): {}", tag, e.getMessage());
        }
    }

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
}
