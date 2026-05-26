package site.sorghum.agent4j.bin.agent;

import org.noear.snack4.ONode;

import site.sorghum.agent4j.bin.model.ModelClient;
import site.sorghum.agent4j.bin.tool.ToolRegistry;
import site.sorghum.agent4j.bin.tool.ToolDispatcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Agent 循环 —— 编排 prompt → LLM → 工具调用 → 反馈结果 → LLM 的循环。
 * <p>
 * 每次 {@link #run(String)} 调用代表一个用户回合。
 * 消息历史通过 {@link ConversationContext} 在内存中累积跨回合持久化。
 * </p>
 * <p>
 * 参考 Agent4j TS 的 loop.ts / context-manager.ts 架构。
 * </p>
 *
 * @author Sorghum
 */
public class AgentLoop {

    private final ModelClient client;
    private final ToolRegistry registry;
    private final ToolDispatcher dispatcher;
    private final ConversationContext ctx;
    // 无固定步数限制：循环直到模型返回纯文本
    /** 事件监听（打印思考/工具调用/步骤） */
    private AgentLoopListener listener = new AgentLoopListener() {};
    /** 输出接口（默认为控制台输出，可替换为其他实现） */
    private AgentOutput output = new ConsoleAgentOutput();
    /** 最近一次 API 返回的 prompt_tokens（0 = 尚无数据，回退到字符估算） */
    private int lastPromptTokens = 0;

    public AgentLoop(ModelClient client, ToolRegistry registry, ConversationContext ctx, int ignored) {
        this.client = client;
        this.registry = registry;
        this.dispatcher = new ToolDispatcher(registry);
        this.ctx = ctx;
    }

    /** 手动触发上下文折叠（/compact 命令）— 保留近20条消息，较早消息摘要 */
    public void compactNow() throws IOException {
        List<Map<String, Object>> messages = ctx.buildMessages();
        List<Map<String, Object>> folded = ContextFolding.foldKeepLast(
                messages, 20, client);
        if (folded.size() < ctx.size()) {
            ctx.compact(folded);
            output.onLog(AgentOutput.LogLevel.INFO, "[compact] " + ctx.size() + " 条消息（保留近20条，较早消息已摘要）");
        } else {
            output.onLog(AgentOutput.LogLevel.INFO, "[compact] 无需折叠（总消息数 ≤ 20）");
        }
    }

    /** 获取上下文（用于访问 SessionStore） */
    public ConversationContext getCtx() {
        return ctx;
    }

    /** Plan Mode 控制 */
    public void setPlanMode(boolean on) { dispatcher.setPlanMode(on); }
    public boolean isPlanMode() { return dispatcher.isPlanMode(); }

    /** 设置事件监听器 */
    public void setListener(AgentLoopListener listener) {
        this.listener = listener != null ? listener : new AgentLoopListener() {};
    }

    /**
     * 构建动态工具使用指引（作为 user 消息注入，不持久化到历史）。
     * 随 tool definitions 一起注入，避免冗余硬编码在 system prompt 中。
     * Plan mode 时附加只读约束说明。
     */
    private String buildToolInstructions() {
        StringBuilder sb = new StringBuilder();
        sb.append("编辑文件时使用 edit_file（SEARCH/REPLACE，search 必须唯一）。\n");
        sb.append("多文件批量编辑使用 multi_edit。\n");
        sb.append("不确定文件位置时用 glob/grep 搜索，需要构建/测试时用 run_command。\n");
        if (dispatcher.isPlanMode()) {
            sb.append("\n# Plan mode\n\n");
            sb.append("写入工具（edit_file / multi_edit / write_file / run_command 等）不可用。\n");
            sb.append("只读工具（read_file / glob / grep / tree / get_file_info / web_search）正常使用。\n");
            sb.append("先探索代码库，然后用 submit_plan 提交计划。\n");
            sb.append("用户审批或输入 /execute 退出计划模式后，所有工具恢复正常。\n");
        }
        return sb.toString();
    }

    /** 设置输出接口（用于自定义输出处理，如控制台 / WebSocket SSE / 日志） */
    public void setOutput(AgentOutput output) {
        this.output = output != null ? output : AgentOutput.NOOP;
    }

    /** 获取当前输出接口 */
    public AgentOutput getOutput() {
        return output;
    }

    /**
     * 执行一个用户回合，返回最终的 assistant content。
     * <p>
     * 用户消息会被追加到上下文，工具调用结果也会累积。
     * 下一个回合调用时，上下文已包含上一轮的全部消息。
     * </p>
     */
    public String run(String userMessage) throws IOException {
        // 1. 用户消息入上下文
        ctx.addUser(userMessage);

        // 每回合重置 storm 窗口
        dispatcher.resetStorm();

        // 工具定义从 prefix 缓存获取（跨 turn 稳定 → 缓存命中）
        List<Map<String, Object>> tools = ctx.tools();
        boolean isThinkingMode = client.isThinkingMode();

        for (int step = 0; ; step++) {
            // 2. 构建完整消息列表 = prefix（system msg）+ 累积历史
            List<Map<String, Object>> messages = ctx.buildMessages();

            // Healing: 发送前修复
            messages = MessageHealer.heal(messages, isThinkingMode);

            // 预检：token 数接近上下文窗口 80% 时折叠（优先用 API 返回的真实 token 数）
            boolean foldedThisStep = false;
            int maxCtx = client.getMaxContextTokens();
            int tokenThreshold = (int)(maxCtx * 0.8);

            // 没有真实 token 数时（加载历史会话 / 首次运行），用字符估算
            int estimatedPromptTokens = lastPromptTokens > 0
                    ? lastPromptTokens
                    : ContextFolding.estimateChars(messages) / 2;
            boolean needFold = estimatedPromptTokens > tokenThreshold;
            if (needFold) {
                output.onLog(AgentOutput.LogLevel.INFO, "[fold] 触发折叠: estimatedTokens=" + estimatedPromptTokens
                        + " threshold=" + tokenThreshold + " maxCtx=" + maxCtx);
                messages = ContextFolding.fold(messages, MAX_TOTAL_CHARS, KEEP_TAIL_CHARS, client);
                if (messages.size() < ctx.size()) {
                    ctx.compact(messages);
                    foldedThisStep = true;
                    lastPromptTokens = 0; // 折叠后重置，等下次 API 返回真实值
                }
            }

            output.onLog(AgentOutput.LogLevel.DEBUG, "step=" + step + " messages.size=" + messages.size()
                    + " lastPromptTokens=" + lastPromptTokens + " threshold=" + tokenThreshold);

            // 2e. 注入动态工具使用指引（作为 user 消息，不持久化到历史）
            // 工具特定说明从 system prompt 移出，改为在此处按需注入，仅在发往 API 前附加
            String instr = buildToolInstructions();
            if (!instr.isEmpty()) {
                List<Map<String, Object>> withInstr = new ArrayList<>(messages.size() + 1);
                withInstr.add(messages.get(0)); // system prompt
                Map<String, Object> instrMsg = new LinkedHashMap<>();
                instrMsg.put("role", "user");
                instrMsg.put("content", instr);
                withInstr.add(instrMsg);
                withInstr.addAll(messages.subList(1, messages.size())); // original history
                messages = withInstr;
            }

            // 流式调用
            final StringBuilder contentBuf = new StringBuilder();
            final StringBuilder reasoningBuf = new StringBuilder();
            final ONode[] streamedTcs = {null};
            final CountDownLatch streamLatch = new CountDownLatch(1);
            final AtomicBoolean streamError = new AtomicBoolean(false);

            client.chatStream(messages, tools, new ModelClient.StreamCallback() {
                @Override
                public void onReasoningDelta(String token) {
                    reasoningBuf.append(token);
                    output.onReasoningDelta(token); // 流式思考输出
                }
                @Override
                public void onContentDelta(String token) {
                    contentBuf.append(token);
                    output.onContentDelta(token); // 流式内容输出
                }
                @Override
                public void onToolCalls(ONode tcs) {
                    streamedTcs[0] = tcs;
                }
                @Override
                public void onUsage(int promptTokens, int completionTokens, int totalTokens,
                                     int cacheHit, int cacheMiss) {
                    lastPromptTokens = promptTokens; // 记录真实 token 数，供下次折叠判断
                    listener.onUsage(promptTokens, completionTokens, totalTokens, cacheHit, cacheMiss);
                    output.onUsage(promptTokens, completionTokens, totalTokens, cacheHit, cacheMiss);
                }
                @Override
                public void onDone() { streamLatch.countDown(); }
                @Override
                public void onError(String err) {
                    streamError.set(true);
                    output.onError("[stream error] " + err);
                    streamLatch.countDown();
                }
            });

            // 等待流结束（CountDownLatch 无忙等待）
            try { streamLatch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            if (streamError.get()) {
                // 容错：若本步未折叠且消息量尚可，尝试折叠后重试一次（API 520 常因请求体过大）
                if (!foldedThisStep && ContextFolding.estimateChars(messages) > 50_000) {
                    output.onLog(AgentOutput.LogLevel.INFO, "[recover] API 错误，尝试折叠上下文后重试...");
                    int limit = Math.max(50_000, ContextFolding.estimateChars(messages) / 2);
                    List<Map<String, Object>> recovered = ContextFolding.fold(
                            messages, limit, KEEP_TAIL_CHARS, client);
                    ctx.compact(recovered);
                    lastPromptTokens = 0; // 折叠后重置
                    continue; // 回到迭代开始，用折叠后的消息重试
                }
                throw new IOException("[stream] API error during streaming");
            }

            // 流结束通知
            output.onContentComplete();

            String content = contentBuf.length() > 0 ? contentBuf.toString() : null;
            String reasoningContent = reasoningBuf.length() > 0 ? reasoningBuf.toString() : null;
            ONode toolCalls = streamedTcs[0];
            boolean hasToolCalls = toolCalls != null && toolCalls.isArray() && !toolCalls.getArray().isEmpty();

            // Scavenge: 从 reasoning_content 中回收丢失的工具调用
            if (!hasToolCalls && (reasoningContent != null || content != null)) {
                List<Scavenger.ToolCall> scavenged = Scavenger.scavenge(
                        reasoningContent, content, new ArrayList<>());
                if (!scavenged.isEmpty()) {
                    // 用 ONode 重建 tool_calls
                    ONode fakeTcArray = org.noear.snack4.ONode.ofJson("[]").asArray();
                    for (Scavenger.ToolCall tc : scavenged) {
                        ONode tcn = fakeTcArray.addNew();
                        tcn.set("id", tc.id != null ? tc.id : "scavenged_" + tc.name);
                        tcn.set("type", "function");
                        ONode fn = tcn.getOrNew("function");
                        fn.set("name", tc.name);
                        fn.set("arguments", tc.arguments);
                    }
                    toolCalls = fakeTcArray;
                    hasToolCalls = !toolCalls.getArray().isEmpty();
                }
            }

            // 3. 无 tool_calls → 返回文本回复
            if (!hasToolCalls) {
                // DeepSeek 推理模型：content 为空但 reasoning_content 有内容
                if (content == null || content.isEmpty()) {
                    if (reasoningContent != null && !reasoningContent.isEmpty()) {
                        listener.onReasoning(reasoningContent);
                        output.onReasoning(reasoningContent);
                        ctx.addAssistant(reasoningContent, null, null);
                        return reasoningContent;
                    }
                }
                ctx.addAssistant(content, null, null);
                return content != null ? content : "(empty response)";
            }

            // 4. 有 tool_calls → 并行执行
            String reasoning = reasoningContent;
            ONode[] tcArray = toolCalls.getArray().toArray(new ONode[0]);
            int tcCount = tcArray.length;

            // 先构建 tcList，保证顺序
            List<Map<String, Object>> tcList = new ArrayList<>();
            for (ONode tc : tcArray) {
                String tcId = tc.get("id").getString();
                ONode func = tc.get("function");
                String tcName = func.get("name").getString();
                String tcArgs = func.get("arguments").getString();
                if (tcArgs == null) tcArgs = "{}";

                Map<String, Object> tcMap = new LinkedHashMap<>();
                tcMap.put("id", tcId);
                tcMap.put("name", tcName);
                tcMap.put("arguments", tcArgs);
                tcList.add(tcMap);

                listener.onToolCall(tcName, tcArgs);
                output.onToolCall(tcName, tcArgs);
            }

            // 并行分发（CompletableFuture.supplyAsync）
            @SuppressWarnings("unchecked")
            CompletableFuture<Map<String, Object>>[] futures = new CompletableFuture[tcCount];
            // Storm 自愈：跟踪是否有调用被抑制（AtomicBoolean 保证 happens-before）
            final AtomicBoolean anySuppressed = new AtomicBoolean(false);
            for (int i = 0; i < tcCount; i++) {
                final int idx = i;
                futures[i] = CompletableFuture.supplyAsync(() -> {
                    ONode tc = tcArray[idx];
                    String tcId = tc.get("id").getString();
                    ONode func = tc.get("function");
                    String tcName = func.get("name").getString();
                    String tcArgs = func.get("arguments").getString();
                    if (tcArgs == null) tcArgs = "{}";
                    String result = dispatcher.dispatch(tcName, tcArgs, MAX_RESULT_TOKENS);
                    // 检测 storm 抑制
                    if (result != null && result.contains("\"rejectedReason\":\"storm\"")) {
                        anySuppressed.set(true);
                    }
                    listener.onToolResult(tcName, result);
                    output.onToolResult(tcName, result);
                    return toolResult(tcId, result);
                });
            }

            // 等待全部完成，结果顺序与 tcList 一致
            CompletableFuture.allOf(futures).join();
            List<Map<String, Object>> toolResults = new ArrayList<>();
            for (CompletableFuture<Map<String, Object>> f : futures) {
                try { toolResults.add(f.get()); }
                catch (InterruptedException | java.util.concurrent.ExecutionException e) {
                    toolResults.add(toolResult("?", "[ERROR] " + e.getMessage()));
                }
            }

            // 5. assistant 消息先入上下文，tool 结果在后（API 顺序要求）
            ctx.addAssistant(content, tcList, reasoning);
            for (Map<String, Object> tr : toolResults) {
                ctx.addToolResult((String) tr.get("tool_call_id"), (String) tr.get("content"));
            }

            // Self-Correction：如果所有调用都被 storm 抑制，给模型一次自愈机会
            // （allSuppressed + self-correction）
            if (anySuppressed.get()) {
                boolean allSuppressed = true;
                for (Map<String, Object> tr : toolResults) {
                    String r = (String) tr.get("content");
                    if (r == null || !r.contains("\"rejectedReason\":\"storm\"")) {
                        allSuppressed = false;
                        break;
                    }
                }
                if (allSuppressed) {
                    output.onLog(AgentOutput.LogLevel.INFO, "[self-correct] 所有工具调用被 storm 抑制，给模型一次自愈机会");
                    // 在上下文中插入提示，让模型换种方式继续
                    ctx.addUser("[系统提示：你刚刚重复调用了相同的工具。请换一种方式完成任务，"
                            + "或直接用文本回答。]");
                    continue; // 跳过 addAssistant/toolResult，回到迭代开始
                }
            }
        }

    }  // end run

    private static Map<String, Object> toolResult(String id, String result) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", "tool");
        m.put("tool_call_id", id);
        m.put("content", result);
        return m;
    }

    /** 工具结果最大 token 数 */
    private static final int MAX_RESULT_TOKENS = 8000;

    /** 消息总字符数阈值（超出时触发折叠），约 200KB — 注意 estimateChars 不含 tools JSON，实际请求体会更大 */
    private static final int MAX_TOTAL_CHARS = 200_000;

    /** 折叠时保留的尾部预算（字符数），约 80KB */
    private static final int KEEP_TAIL_CHARS = 80_000;
}
