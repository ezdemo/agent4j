package site.sorghum.agent4j.bin.agent;

import lombok.Getter;
import lombok.Setter;
import org.noear.snack4.ONode;
import site.sorghum.agent4j.bin.builtin.TaskTool;
import site.sorghum.agent4j.bin.model.ModelClient;
import site.sorghum.agent4j.bin.session.SessionService;
import site.sorghum.agent4j.bin.tool.ToolDispatcher;
import site.sorghum.agent4j.bin.tool.ToolRegistry;
import site.sorghum.agent4j.tool.HitlRequiredException;
import site.sorghum.agent4j.tool.ToolContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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

    /**
     * storm 自愈尝试次数上限（每回合重置），防止无限循环
     */
    private static final int MAX_SELF_CORRECTION_ATTEMPTS = 5;
    /**
     * 消息总字符数阈值（超出时触发折叠），约 200KB — 注意 estimateChars 不含 tools JSON，实际请求体会更大
     */
    private static final int MAX_TOTAL_CHARS = 200_000;
    /**
     * 折叠时保留的尾部预算（字符数），约 80KB
     */
    private static final int KEEP_TAIL_CHARS = 80_000;
    /**
     * 工具执行超时（秒），单个工具调用最长等待时间
     */
    private static final int TOOL_TIMEOUT_SEC = 360;
    private final ModelClient client;
    // 无固定步数限制：循环直到模型返回纯文本
    private final ToolDispatcher dispatcher;
    /**
     * -- GETTER --
     * 获取上下文（用于访问 SessionStore）
     */
    @Getter
    private final ConversationContext ctx;
    /**
     * 推理断路器（每回合重置），检测思考循环
     */
    private final ReasonBreaker reasonBreaker = new ReasonBreaker();
    /**
     * 会话服务引用（用于同步 lastPromptTokens 到 usage 文件）
     * -- SETTER --
     *  设置会话服务（用于同步 lastPromptTokens）

     */
    @Setter
    private SessionService sessionService;
    /**
     * 事件监听（打印思考/工具调用/步骤）
     */
    private AgentLoopListener listener = NoOpAgentLoopListener.INSTANCE;
    /**
     * 输出接口（默认为控制台输出，可替换为其他实现）
     * -- GETTER --
     *  获取当前输出接口

     */
    @Getter
    private AgentOutput output = new ConsoleAgentOutput();

    // ==================== HITL (Human-In-The-Loop) ====================
    /**
     * 最近一次 API 返回的 prompt_tokens（0 = 尚无数据，回退到字符估算）
     * -- GETTER --
     *  获取最近一次 API 返回的 prompt_tokens

     */
    @Getter
    private int lastPromptTokens = 0;
    /**
     * 用户主动中断标志（前端点击停止按钮时设置）
     */
    private volatile boolean userAbortRequested = false;
    /**
     * 当前会话ID（用于传递给工具执行上下文）
     * -- GETTER --
     *  获取当前会话ID
     * -- SETTER --
     *  设置当前会话ID（用于传递给工具执行上下文）


     */
    @Setter
    @Getter
    private volatile String sessionId;
    /**
     * HITL 模式开关（true = 执行非只读工具前需用户审批）
     * -- GETTER --
     *  获取 HITL 模式状态
     * -- SETTER --
     *  直接设置 HITL 模式（用于配置热更新）


     */
    @Setter
    @Getter
    private volatile boolean hitlMode;
    /**
     * HITL 当前审批状态
     */
    private volatile HitlState hitlState = HitlState.NONE;
    /**
     * HITL 暂存的工具调用（ONode 数组）
     */
    private volatile ONode pendingHITLToolCalls;
    /**
     * HITL 暂存的 assistant content
     */
    private volatile String pendingHITLContent;

    // ==================== 沙箱越界 HITL（强制审批，不受 hitlMode 影响） ====================
    /**
     * HITL 暂存的 reasoning_content
     */
    private volatile String pendingHITLReasoning;
    /**
     * HITL 暂存的解析后工具调用列表
     * -- GETTER --
     *  获取待审批的工具调用列表（用于 /agree 命令显示）

     */
    @Getter
    private volatile List<ToolCallEntry> pendingHITTcList;
    /**
     * 沙箱越界 HITL 暂存：完整的工具调用 ONode（供重放）
     */
    private volatile ONode pendingSandboxHITToolCalls;
    /**
     * 沙箱越界 HITL 暂存：assistant content
     */
    private volatile String pendingSandboxHITContent;
    /**
     * 沙箱越界 HITL 暂存：reasoning_content
     */
    private volatile String pendingSandboxHITReasoning;
    /**
     * 沙箱越界 HITL 暂存：越界详情（展示给用户）
     */
    private volatile String pendingSandboxHITDetails;

    public AgentLoop(ModelClient client, ToolRegistry registry, ConversationContext ctx) {
        this(client, registry, ctx, false);
    }

    public AgentLoop(ModelClient client, ToolRegistry registry, ConversationContext ctx, boolean hitlDefault) {
        this.client = client;
        this.dispatcher = new ToolDispatcher(registry);
        this.ctx = ctx;
        this.hitlMode = hitlDefault;
    }

    private static ChatMessage toolResult(String id, String result) {
        return ChatMessage.tool(id, result);
    }

    /**
     * 切换 HITL 模式
     */
    public synchronized void toggleHitl() {
        hitlMode = !hitlMode;
    }

    /**
     * 获取模型最大上下文窗口 token 数
     */
    public int getMaxContextTokens() {
        return client.getMaxContextTokens();
    }

    /**
     * 批准待执行的工具调用
     */
    public void approveHITL() {
        hitlState = HitlState.APPROVED;
    }

    /**
     * 拒绝待执行的工具调用
     */
    public void denyHITL() {
        hitlState = HitlState.DENIED;
    }

    /**
     * 是否有待审批的工具调用
     */
    public boolean hasPendingHITL() {
        return hitlState == HitlState.PENDING;
    }

    /**
     * 手动触发上下文折叠（/compact 命令）— 保留近20条消息，较早消息摘要
     */
    public void compactNow() throws IOException {
        List<ChatMessage> messages = ctx.buildMessages();
        List<ChatMessage> folded = ContextFolding.foldKeepLast(
                messages, 20, client);
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

    /**
     * Plan Mode 控制
     */
    public void setPlanMode(boolean on) {
        dispatcher.setPlanMode(on);
    }

    /**
     * 用户主动中断：设置中断标志并中止当前 HTTP 流式请求
     */
    public void requestUserAbort() {
        userAbortRequested = true;
        client.abortStream();
    }

    /**
     * 重置用户中断标志（每回合开始时调用）
     */
    public void resetUserAbort() {
        userAbortRequested = false;
    }

    /**
     * 设置事件监听器
     */
    public void setListener(AgentLoopListener listener) {
        this.listener = listener != null ? listener : NoOpAgentLoopListener.INSTANCE;
    }

    /**
     * 构建动态工具使用指引（作为 user 消息注入，不持久化到历史）。
     * Plan mode 规则已永久在 system prompt 中描述，此处仅注入通用工具使用提示。
     */
    private String buildToolInstructions() {
        return """
                编辑文件时使用 edit_file（SEARCH/REPLACE，search 必须唯一）。
                多文件批量编辑使用 multi_edit。
                不确定文件位置时用 glob/grep 搜索，需要构建/测试时用 run_command。
                """;
    }

    // ==================== HITL 拦截与恢复 ====================

    /**
     * 设置输出接口（用于自定义输出处理，如控制台 / WebSocket SSE / 日志）
     */
    public void setOutput(AgentOutput output) {
        this.output = output != null ? output : AgentOutput.NOOP;
    }

    /**
     * 执行一个用户回合，返回最终的 assistant content。
     * <p>
     * 用户消息会被追加到上下文，工具调用结果也会累积。
     * 下一个回合调用时，上下文已包含上一轮的全部消息。
     * </p>
     */
    public String run(String userMessage) throws IOException {
        // ---- HITL 恢复：用户已审批 / 拒绝 ----
        if (hitlState == HitlState.APPROVED) {
            hitlState = HitlState.NONE;
            // 区分沙箱越界 HITL 与普通 HITL
            if (pendingSandboxHITToolCalls != null) {
                try {
                    output.onLog(LogLevel.INFO, "[hitl] 用户批准沙箱越界，重放工具调用...");
                } catch (Exception e) {
                    // 忽略异常
                }
                return resumeAfterSandboxHITL(true);
            }
            try {
                output.onLog(LogLevel.INFO, "[hitl] 用户批准，执行工具调用...");
            } catch (Exception e) {
                // 忽略异常
            }
            return resumeAfterHITL(true);
        }
        if (hitlState == HitlState.DENIED) {
            hitlState = HitlState.NONE;
            // 区分沙箱越界 HITL 与普通 HITL
            if (pendingSandboxHITToolCalls != null) {
                try {
                    output.onLog(LogLevel.INFO, "[hitl] 用户拒绝沙箱越界。");
                } catch (Exception e) {
                    // 忽略异常
                }
                return resumeAfterSandboxHITL(false);
            }
            try {
                output.onLog(LogLevel.INFO, "[hitl] 用户拒绝，跳过工具调用。");
            } catch (Exception e) {
                // 忽略异常
            }
            return resumeAfterHITL(false);
        }

        ctx.addUser(userMessage);
        dispatcher.resetStorm();
        reasonBreaker.reset();
        resetUserAbort(); // 重置用户中断标志
        int selfCorrectionAttempts = 0;
        List<Map<String, Object>> tools = ctx.tools();
        boolean isThinkingMode = client.isThinkingMode();

        for (int step = 0; ; step++) {
            // 0. 检查用户中断请求
            if (userAbortRequested) {
                try {
                    output.onLog(LogLevel.INFO, "[abort] 用户请求中断，停止推理循环");
                } catch (Exception e) {
                    // 忽略异常
                }
                // 返回已生成的内容（如果有），或返回中断提示
                String lastContent = ctx.getLastAssistantContent();
                if (lastContent != null && !lastContent.isEmpty()) {
                    return lastContent;
                }
                return "⏹️ 已停止生成";
            }

            // 1. 准备消息：构建 + Healing + 折叠 + 注入工具指引
            PreparedMessages prepared = prepareMessages(step, isThinkingMode);
            List<ChatMessage> messages = prepared.messages;

            // 2. 流式调用 LLM
            StreamResult sr = streamLLM(messages, tools);

            // 2.1 用户中断：streamLLM 已提前返回，直接退出循环，不执行工具、不重试
            if (userAbortRequested) {
                try {
                    output.onLog(LogLevel.INFO, "[abort] 用户请求中断（streamLLM 后检测），停止推理循环");
                } catch (Exception e) {
                    // 忽略异常
                }
                String content = sr.content;
                String reasoningContent = sr.reasoningContent;
                if (content != null && !content.isEmpty()) {
                    ctx.addAssistant(content, null, reasoningContent);
                    return content;
                }
                if (reasoningContent != null && !reasoningContent.isEmpty()) {
                    ctx.addAssistant(null, null, reasoningContent);
                    return reasoningContent;
                }
                return "⏹️ 已停止生成";
            }

            // 3. 流式错误恢复
            if (sr.error) {
                if (recoverFromStreamError(messages, prepared.foldedThisStep)) {
                    continue;
                }
                throw new IOException("[stream] API error during streaming");
            }

            try {
                output.onContentComplete();
            } catch (Exception e) {
                // SSE连接断开时忽略异常，继续执行
            }

            // 推理断路器：流式检测到循环则注入警告
            if (sr.loopAborted) {
                ctx.addUser("[ReasonBreaker] 检测到思考循环，已提前终止本轮推理。请停止当前思路，尝试不同的方法。");
                selfCorrectionAttempts = 0;
                continue;
            }

            // 4. 从 reasoning 中回收丢失的工具调用（Scavenger）
            ONode toolCalls = scavengeToolCalls(sr.toolCalls, sr.reasoningContent, sr.content);
            boolean hasToolCalls = toolCalls != null && toolCalls.isArray() && !toolCalls.getArray().isEmpty();

            // 5. 无 tool_calls → 返回文本回复
            if (!hasToolCalls) {
                return handleTextResponse(sr.content, sr.reasoningContent);
            }

            // ---- HITL 拦截：非只读工具需要用户审批 ----
            if (hitlMode) {
                return interceptForHITL(toolCalls, sr.content, sr.reasoningContent);
            }

            // 6. 并行执行工具调用
            ToolExecutionResult ter = executeToolCalls(toolCalls);

            // 6.1 沙箱越界 HITL：暂停并等待用户审批
            if (hitlState == HitlState.PENDING && pendingSandboxHITToolCalls != null) {
                // 暂存 assistant content（审批通过后写入上下文）
                this.pendingSandboxHITContent = sr.content;
                this.pendingSandboxHITReasoning = sr.reasoningContent;
                return interceptForSandboxHITL();
            }

            // 7. 将 assistant 消息和工具结果写入上下文（API 顺序要求：assistant 先于 tool result）
            ctx.addAssistant(sr.content, ter.tcList, sr.reasoningContent);
            for (ChatMessage tr : ter.toolResults) {
                ctx.addToolResult(tr.getToolCallId(), tr.getContent());
            }

            // 8. Self-Correction：所有调用被 storm 抑制时，给模型有限次自愈机会
            selfCorrectionAttempts = handleSelfCorrection(
                    ter.toolResults, ter.anySuppressed, selfCorrectionAttempts);
            if (selfCorrectionAttempts < 0) {
                String fallback = "所有工具调用均被风暴断路器抑制，无法继续执行。请换用其他方式完成任务。";
                ctx.addAssistant(fallback, null, null);
                return fallback;
            }
        }
    }  // end run

    // ==================== 沙箱越界 HITL ====================

    /**
     * HITL 拦截：暂存工具调用，返回审批提示给用户。
     */
    private String interceptForHITL(ONode toolCalls, String content, String reasoningContent) {
        // 解析工具调用列表
        List<ToolCallEntry> tcList = parseToolCalls(toolCalls);

        // 暂存状态
        this.pendingHITLToolCalls = toolCalls;
        this.pendingHITLContent = content;
        this.pendingHITLReasoning = reasoningContent;
        this.pendingHITTcList = tcList;
        this.hitlState = HitlState.PENDING;

        // 构建审批提示
        StringBuilder sb = new StringBuilder();
        sb.append("⏸️  **HITL 模式：以下工具调用需要审批**\n\n");
        for (ToolCallEntry tc : tcList) {
            String name = tc.name();
            Object argsObj = tc.arguments();
            String args = argsObj != null ? argsObj.toString() : null;
            sb.append("- `").append(name).append("`");
            if (args != null && !args.isEmpty() && !"{}".equals(args)) {
                // 截断过长的参数
                String display = args.length() > 200 ? args.substring(0, 200) + "..." : args;
                sb.append(" ").append(display);
            }
            sb.append("\n");
        }
        sb.append("\n请选择：");

        String message = sb.toString();
        try {
            output.onContentDelta(message);
        } catch (Exception e) {
            // SSE连接断开时忽略异常
        }
        // 发送选项按钮（前端渲染为可点击按钮，CLI 渲染为文本菜单）
        try {
            output.onChoice(Arrays.asList(
                    new ChoiceOption("/agree", "同意执行"),
                    new ChoiceOption("/deny", "拒绝执行")
            ));
        } catch (Exception e) {
            // SSE连接断开时忽略异常
        }
        try {
            output.onContentComplete();
        } catch (Exception e) {
            // SSE连接断开时忽略异常
        }
        return message;
    }

    /**
     * 解析 ONnode 工具调用为 Map 列表（供 tcList 使用）。
     */
    private List<ToolCallEntry> parseToolCalls(ONode toolCalls) {
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

    // ==================== 统一的继续对话循环（消除 HITL 恢复中的重复代码） ====================

    /**
     * HITL 恢复：用户审批/拒绝后，继续执行或跳过工具调用。
     */
    private String resumeAfterHITL(boolean approved) throws IOException {
        ONode toolCalls = this.pendingHITLToolCalls;
        String content = this.pendingHITLContent;
        String reasoningContent = this.pendingHITLReasoning;
        List<ToolCallEntry> tcList = this.pendingHITTcList;

        // 清空暂存
        this.pendingHITLToolCalls = null;
        this.pendingHITLContent = null;
        this.pendingHITLReasoning = null;
        this.pendingHITTcList = null;

        if (!approved) {
            // 用户拒绝：不写入带 tool_calls 的 assistant（否则 API 要求必须跟 tool 消息），
            // 只写入一条纯文本拒绝消息告知模型
            String denyMsg = "工具调用已被用户拒绝。";
            ctx.addAssistant(denyMsg, null, null);
            return denyMsg;
        }

        // 用户批准：先写入 assistant 消息，再并行执行工具
        // 注意：工具结果尚未写入，与沙箱越界拦截配合时避免脏数据传给 LLM
        ctx.addAssistant(content, tcList, reasoningContent);

        dispatcher.resetStorm();

        // 并行执行暂存的工具调用
        ToolExecutionResult ter = executeToolCalls(toolCalls);

        // 沙箱越界 HITL：暂停并等待用户审批（不写入占位结果，不继续 LLM 调用）
        if (hitlState == HitlState.PENDING && pendingSandboxHITToolCalls != null) {
            this.pendingSandboxHITContent = content;
            this.pendingSandboxHITReasoning = reasoningContent;
            return interceptForSandboxHITL();
        }

        // 写入工具结果
        for (ChatMessage tr : ter.toolResults) {
            ctx.addToolResult(tr.getToolCallId(), tr.getContent());
        }

        // 委托给统一的内部循环
        return continueConversationLoop();
    }

    /**
     * 沙箱越界 HITL 拦截：向用户展示越界详情，等待审批。
     */
    private String interceptForSandboxHITL() {
        String details = this.pendingSandboxHITDetails != null
                ? this.pendingSandboxHITDetails : "未知路径越界";

        String message = "⏸️  **沙箱越界 — 需要审批**\n\n" +
                "检测到工具试图访问工作区之外的路径：\n\n" +
                "> " + details + "\n\n" +
                "请选择：";
        try {
            output.onContentDelta(message);
        } catch (Exception e) {
            // SSE连接断开时忽略异常
        }
        // 发送选项按钮（前端渲染为可点击按钮，CLI 渲染为文本菜单）
        try {
            output.onChoice(Arrays.asList(
                    new ChoiceOption("/agree", "同意执行"),
                    new ChoiceOption("/deny", "拒绝执行")
            ));
        } catch (Exception e) {
            // SSE连接断开时忽略异常
        }
        try {
            output.onContentComplete();
        } catch (Exception e) {
            // SSE连接断开时忽略异常
        }
        return message;
    }

    // ==================== 内部数据类 ====================

    /**
     * 沙箱越界 HITL 恢复：审批通过后以沙箱旁路模式重放工具调用。
     */
    private String resumeAfterSandboxHITL(boolean approved) throws IOException {
        ONode toolCalls = this.pendingSandboxHITToolCalls;
        String content = this.pendingSandboxHITContent;
        String reasoningContent = this.pendingSandboxHITReasoning;

        // 清空暂存
        this.pendingSandboxHITToolCalls = null;
        this.pendingSandboxHITContent = null;
        this.pendingSandboxHITReasoning = null;
        this.pendingSandboxHITDetails = null;

        if (!approved) {
            // 用户拒绝：将 assistant 消息（含工具调用）写入上下文，返回拒绝提示
            List<ToolCallEntry> tcList = parseToolCalls(toolCalls);
            ctx.addAssistant(content, tcList, reasoningContent);
            String denyMsg = "沙箱越界已被用户拒绝。";
            ctx.addAssistant(denyMsg, null, null);
            return denyMsg;
        }

        // 用户批准：先写入 assistant 消息，再以沙箱旁路模式执行工具
        List<ToolCallEntry> tcList = parseToolCalls(toolCalls);
        ctx.addAssistant(content, tcList, reasoningContent);

        dispatcher.resetStorm();
        reasonBreaker.reset();
        resetUserAbort();

        // 重放执行工具调用（沙箱旁路：pass skipSandboxCheck=true 到异步线程）
        ToolExecutionResult initialTer;
        try {
            initialTer = executeToolCalls(toolCalls, true);
        } catch (Exception e) {
            output.onLog(LogLevel.ERROR, "[hitl] 沙箱旁路重放失败: " + e.getMessage());
            throw new IOException("沙箱旁路重放工具调用失败: " + e.getMessage(), e);
        }

        // 写入工具结果
        for (ChatMessage tr : initialTer.toolResults) {
            ctx.addToolResult(tr.getToolCallId(), tr.getContent());
        }

        // Self-Correction 检查与委托给统一的内部循环
        int scAttempts = handleSelfCorrection(initialTer.toolResults, initialTer.anySuppressed, 0);
        if (scAttempts < 0) {
            String fallback = "所有工具调用均被风暴断路器抑制，无法继续执行。";
            ctx.addAssistant(fallback, null, null);
            return fallback;
        }

        // 委托给统一的内部循环（沙箱 HITL 恢复后仍需检查沙箱越界）
        return continueConversationLoop();
    }

    /**
     * 在工具结果写入上下文后继续推理循环。
     * 被 resumeAfterHITL / resumeAfterSandboxHITL 以及主循环复用。
     *
     * @return 最终的 assistant content
     */
    private String continueConversationLoop() throws IOException {
        boolean isThinkingMode = client.isThinkingMode();
        List<Map<String, Object>> tools = ctx.tools();
        int selfCorrectionAttempts = 0;

        for (int step = 0; ; step++) {
            if (userAbortRequested) {
                try {
                    output.onLog(LogLevel.INFO, "[abort] 用户请求中断，停止推理循环");
                } catch (Exception e) {
                    // 忽略异常
                }
                String lastContent = ctx.getLastAssistantContent();
                if (lastContent != null && !lastContent.isEmpty()) {
                    return lastContent;
                }
                return "⏹️ 已停止生成";
            }

            PreparedMessages prepared = prepareMessages(step, isThinkingMode);
            List<ChatMessage> messages = prepared.messages;

            StreamResult sr = streamLLM(messages, tools);

            if (userAbortRequested) {
                String abortContent = sr.content;
                String abortReasoning = sr.reasoningContent;
                if (abortContent != null && !abortContent.isEmpty()) {
                    ctx.addAssistant(abortContent, null, abortReasoning);
                    return abortContent;
                }
                if (abortReasoning != null && !abortReasoning.isEmpty()) {
                    ctx.addAssistant(null, null, abortReasoning);
                    return abortReasoning;
                }
                return "⏹️ 已停止生成";
            }

            if (sr.error) {
                if (recoverFromStreamError(messages, prepared.foldedThisStep)) continue;
                throw new IOException("[stream] API error during streaming");
            }
            try {
                output.onContentComplete();
            } catch (Exception e) {
                // SSE连接断开时忽略异常，继续执行
            }

            if (sr.loopAborted) {
                ctx.addUser("[ReasonBreaker] 检测到思考循环，已提前终止本轮推理。请停止当前思路，尝试不同的方法。");
                selfCorrectionAttempts = 0;
                continue;
            }

            ONode toolCalls = scavengeToolCalls(sr.toolCalls, sr.reasoningContent, sr.content);
            boolean hasToolCalls = toolCalls != null && toolCalls.isArray()
                    && !toolCalls.getArray().isEmpty();

            if (!hasToolCalls) {
                return handleTextResponse(sr.content, sr.reasoningContent);
            }

            if (hitlMode) {
                return interceptForHITL(toolCalls, sr.content, sr.reasoningContent);
            }

            ToolExecutionResult ter = executeToolCalls(toolCalls);

            // 沙箱越界 HITL：暂停并等待用户审批
            if (hitlState == HitlState.PENDING && pendingSandboxHITToolCalls != null) {
                this.pendingSandboxHITContent = sr.content;
                this.pendingSandboxHITReasoning = sr.reasoningContent;
                return interceptForSandboxHITL();
            }

            ctx.addAssistant(sr.content, ter.tcList, sr.reasoningContent);
            for (ChatMessage tr : ter.toolResults) {
                ctx.addToolResult(tr.getToolCallId(), tr.getContent());
            }

            selfCorrectionAttempts = handleSelfCorrection(
                    ter.toolResults, ter.anySuppressed, selfCorrectionAttempts);
            if (selfCorrectionAttempts < 0) {
                String fallback = "所有工具调用均被风暴断路器抑制，无法继续执行。请换用其他方式完成任务。";
                ctx.addAssistant(fallback, null, null);
                return fallback;
            }
        }
    }

    /**
     * 步骤 1: 构建消息 + Healing + 预检折叠 + 注入工具指引 + 调试日志。
     * 处理 lastPromptTokens / ctx.compact / foldedThisStep 等副作用。
     */
    private PreparedMessages prepareMessages(int step, boolean isThinkingMode) throws IOException {
        List<ChatMessage> messages = ctx.buildMessages();
        messages = MessageHealer.heal(messages, isThinkingMode);

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
                // 忽略异常
            }
            messages = ContextFolding.fold(messages, MAX_TOTAL_CHARS, KEEP_TAIL_CHARS, client);
            if (messages.size() < ctx.size()) {
                ctx.compact(messages);
                foldedThisStep = true;
                lastPromptTokens = 0; // 折叠后重置，等下次 API 返回真实值
            }
        }

        try {
            output.onLog(LogLevel.DEBUG, "step=" + step + " messages.size=" + messages.size()
                    + " lastPromptTokens=" + lastPromptTokens + " threshold=" + tokenThreshold);
        } catch (Exception e) {
            // 忽略异常
        }

        // 注入动态工具使用指引（作为 user 消息，不持久化到历史）
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

    // ==================== 拆分后的子方法 ====================

    /**
     * 步骤 2: 流式调用 LLM API，阻塞等待流结束，返回内容/思考/工具调用/错误状态。
     * 副作用：更新 lastPromptTokens（通过回调），触发 listener/output 事件。
     */
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
                    // SSE连接断开时忽略异常，继续执行
                    // output可能是SseEmitter桥接，连接断开后会抛出异常
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
                            output.onLog(LogLevel.WARN,
                                    "[ReasonBreaker] " + lr.toWarning());
                        } catch (Exception ex) {
                            // 忽略异常
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
                    // SSE连接断开时忽略异常，继续执行
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
                // 同步 lastPromptTokens 到 SessionService
                if (sessionService != null) {
                    sessionService.updateLastPromptTokens(promptTokens);
                }
                // 获取当前模型名称，用于按模型分别计费
                String currentModel = client.getModel();
                try {
                    listener.onUsage(currentModel, promptTokens, completionTokens, totalTokens, cacheHit, cacheMiss);
                } catch (Exception e) {
                    // 忽略异常
                }
                try {
                    output.onUsage(currentModel, promptTokens, completionTokens, totalTokens, cacheHit, cacheMiss);
                } catch (Exception e) {
                    // SSE连接断开时忽略异常，继续执行
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
                    // SSE连接断开时忽略异常
                }
                streamLatch.countDown();
            }
        });

        // 等待流结束（CountDownLatch 无忙等待）
        try {
            streamLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 用户主动中断
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

    /**
     * 步骤 3: 流式错误后尝试折叠恢复。
     *
     * @return true 表示已恢复（应 continue 重试），false 表示无法恢复（应抛异常）
     */
    private boolean recoverFromStreamError(List<ChatMessage> messages, boolean foldedThisStep) throws IOException {
        if (!foldedThisStep && ContextFolding.estimateChars(messages) > 50_000) {
            try {
                output.onLog(LogLevel.INFO, "[recover] API 错误，尝试折叠上下文后重试...");
            } catch (Exception e) {
                // 忽略异常
            }
            int limit = Math.max(50_000, ContextFolding.estimateChars(messages) / 2);
            List<ChatMessage> recovered = ContextFolding.fold(
                    messages, limit, KEEP_TAIL_CHARS, client);
            ctx.compact(recovered);
            lastPromptTokens = 0; // 折叠后重置
            return true;
        }
        return false;
    }

    /**
     * 步骤 4: 从 reasoning_content 中回收丢失的工具调用（Scavenger）。
     * 如果 LLM 在 thinking 中输出了工具调用但未正确格式化，此处补救。
     */
    private ONode scavengeToolCalls(ONode toolCalls, String reasoningContent, String content) {
        boolean hasToolCalls = toolCalls != null && toolCalls.isArray() && !toolCalls.getArray().isEmpty();
        if (hasToolCalls) {
            return toolCalls;
        }
        if (reasoningContent == null && content == null) {
            return toolCalls;
        }

        List<Scavenger.ToolCall> scavenged = Scavenger.scavenge(
                reasoningContent, content, new ArrayList<>());
        if (scavenged.isEmpty()) {
            return toolCalls;
        }

        ONode fakeTcArray = ONode.ofJson("[]").asArray();
        for (Scavenger.ToolCall tc : scavenged) {
            ONode tcn = fakeTcArray.addNew();
            tcn.set("id", tc.id() != null ? tc.id() : "scavenged_" + tc.name());
            tcn.set("type", "function");
            ONode fn = tcn.getOrNew("function");
            fn.set("name", tc.name());
            fn.set("arguments", tc.arguments());
        }
        return fakeTcArray;
    }

    /**
     * 步骤 5: 处理纯文本响应（无工具调用），将结果写入上下文并返回。
     * 处理 DeepSeek 推理模型 content 为空但 reasoning_content 有内容的特殊情况。
     */
    private String handleTextResponse(String content, String reasoningContent) {
        if (content == null || content.isEmpty()) {
            if (reasoningContent != null && !reasoningContent.isEmpty()) {
                try {
                    listener.onReasoning(reasoningContent);
                } catch (Exception e) {
                    // 忽略异常
                }
                try {
                    output.onReasoning(reasoningContent);
                } catch (Exception e) {
                    // SSE连接断开时忽略异常，继续执行
                }
                ctx.addAssistant(null, null, reasoningContent);
                return reasoningContent;
            }
        }
        ctx.addAssistant(content, null, reasoningContent);
        return content != null ? content : "(empty response)";
    }

    /**
     * 步骤 6: 解析工具调用列表并并行执行，返回 tcList + toolResults + storm 抑制状态。
     * 丢弃 name 为 null 的无效 tool call（SSE 截断 / 历史损坏保护）。
     */
    private ToolExecutionResult executeToolCalls(ONode toolCalls) {
        return executeToolCalls(toolCalls, false);
    }

    @SuppressWarnings("unchecked")
    private ToolExecutionResult executeToolCalls(ONode toolCalls, boolean skipSandboxCheck) {
        // 将 sessionId 设置到 dispatcher（供工具执行时使用）
        dispatcher.setSessionId(this.sessionId);

        ONode[] tcArray = toolCalls.getArray().toArray(new ONode[0]);

        // 1. 解析 tcList，过滤无效调用，通知监听器
        // 关键：同时构建 filteredTcArray（仅有效调用），确保 tcList 和 toolResults 数量严格一致
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
                    // 忽略异常
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
                // 忽略异常
            }
            try {
                output.onToolCall(tcName, tcArgs);
            } catch (Exception e) {
                // SSE连接断开时忽略异常，继续执行
            }
        }

        // 使用过滤后的工具调用列表创建并行任务，确保 tcList 和 toolResults 数量严格一致
        final ONode[] finalTcArray = filteredTcList.toArray(new ONode[0]);
        int tcCount = finalTcArray.length;

        // 清空子代理用量收集器（task 工具会在 Future 中写入用量数据）
        site.sorghum.agent4j.bin.builtin.TaskTool.clearUsageCollector();

        // 2. 并行分发（CompletableFuture.supplyAsync）
        CompletableFuture<ChatMessage>[] futures = new CompletableFuture[tcCount];
        final AtomicBoolean anySuppressed = new AtomicBoolean(false);
        final AtomicReference<HitlRequiredException> hitlRef =
                new AtomicReference<>(null);
        // 捕获当前 AgentOutput，用于传播到子代理（如 TaskTool）
        final AgentOutput capturedOutput = this.output;
        for (int i = 0; i < tcCount; i++) {
            final int idx = i;
            futures[i] = CompletableFuture.supplyAsync(() -> {
                // 沙箱旁路：在异步工作线程上设置 ThreadLocal
                if (skipSandboxCheck) {
                    ToolContext.enableSandboxBypass();
                }
                // 将父 AgentOutput 设置到 TaskTool 的线程局部变量，
                // 使得 TaskTool.execute() 中创建的 SubAgent 能通过同一输出通道实时推流
                if (capturedOutput != null) {
                    site.sorghum.agent4j.bin.builtin.TaskTool.setCurrentOutput(capturedOutput);
                }
                try {
                    ONode tc = finalTcArray[idx];
                    String tcId = tc.get("id").getString();
                    ONode func = tc.get("function");
                    String tcName = func.get("name").getString();
                    String tcArgs = func.get("arguments").getString();
                    if (tcArgs == null) tcArgs = "{}";
                    try {
                        String result = dispatcher.dispatch(tcName, tcArgs);
                        if (result != null && result.contains("\"rejectedReason\":\"storm\"")) {
                            anySuppressed.set(true);
                        }
                        try {
                            listener.onToolResult(tcName, result);
                        } catch (Exception e) {
                            // 忽略异常
                        }
                        try {
                            output.onToolResult(tcName, result);
                        } catch (Exception e) {
                            // SSE连接断开时忽略异常，继续执行
                        }
                        return toolResult(tcId, result);
                    } catch (HitlRequiredException e) {
                        // 沙箱越界 → 暂存 HITL 信息，不执行
                        hitlRef.set(e);
                        return ChatMessage.tool(tcId, "[HITL_PENDING:" + e.reason() + "] " + e.details());
                    }
                } finally {
                    if (skipSandboxCheck) {
                        ToolContext.disableSandboxBypass();
                    }
                    site.sorghum.agent4j.bin.builtin.TaskTool.clearCurrentOutput();
                }
            });
        }

        // 3. 等待全部完成（带超时保护），结果顺序与 tcList 一致
        try {
            CompletableFuture.allOf(futures).get(TOOL_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            try {
                output.onLog(LogLevel.WARN, "[tool] 工具执行超时（" + TOOL_TIMEOUT_SEC + "s），取消未完成的调用");
            } catch (Exception ex) {
                // 忽略异常
            }
            // 取消未完成的 Future
            for (CompletableFuture<ChatMessage> f : futures) {
                if (!f.isDone()) {
                    f.cancel(true);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            // allOf 在某个 future 异常完成时抛出 ExecutionException，继续逐个收集结果
        }

        List<ChatMessage> toolResults = new ArrayList<>();
        for (int i = 0; i < futures.length; i++) {
            CompletableFuture<ChatMessage> f = futures[i];
            try {
                toolResults.add(f.get());
            } catch (CancellationException e) {
                // 超时取消的，返回带有工具ID的占位错误
                ONode tc = finalTcArray[i];
                String tcId = tc.get("id").getString();
                toolResults.add(toolResult(tcId, "{\"error\":\"工具执行超时（" + TOOL_TIMEOUT_SEC + "s）\",\"rejectedReason\":\"timeout\"}"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                toolResults.add(toolResult("?", "[ERROR] Interrupted"));
            } catch (ExecutionException e) {
                toolResults.add(toolResult("?", "[ERROR] " + e.getMessage()));
            }
        }

        // 3.1 沙箱越界 HITL：暂存并标记待审批
        HitlRequiredException hitlEx = hitlRef.get();
        if (hitlEx != null) {
            this.pendingSandboxHITToolCalls = toolCalls;
            this.pendingSandboxHITDetails = hitlEx.details();
            this.hitlState = HitlState.PENDING;
            try {
                output.onLog(LogLevel.WARN,
                        "[hitl] 沙箱越界触发强制审批: " + hitlEx.details());
            } catch (Exception e) {
                // 忽略异常
            }
        }

        // 收集子代理的 token 用量，同步到父会话
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

    /**
     * 步骤 7: Self-Correction — 所有工具调用被 storm 抑制时，给模型有限次自愈机会。
     *
     * @return 更新后的尝试次数；返回 -1 表示已达上限，应停止循环返回 fallback
     */
    private int handleSelfCorrection(List<ChatMessage> toolResults,
                                     boolean anySuppressed, int selfCorrectionAttempts) {
        if (!anySuppressed) {
            return selfCorrectionAttempts;
        }

        boolean allSuppressed = true;
        for (ChatMessage tr : toolResults) {
            String r = tr.getContent();
            if (r == null || !r.contains("\"rejectedReason\":\"storm\"")) {
                allSuppressed = false;
                break;
            }
        }
        if (!allSuppressed) {
            return selfCorrectionAttempts;
        }

        selfCorrectionAttempts++;
        if (selfCorrectionAttempts > MAX_SELF_CORRECTION_ATTEMPTS) {
            try {
                output.onLog(LogLevel.WARN,
                        "[self-correct] 已达自愈尝试上限（" + MAX_SELF_CORRECTION_ATTEMPTS + "次），停止循环");
            } catch (Exception e) {
                // 忽略异常
            }
            return -1; // 信号：停止循环
        }

        try {
            output.onLog(LogLevel.INFO,
                    "[self-correct] 所有工具调用被 storm 抑制，第" + selfCorrectionAttempts + "次自愈尝试");
        } catch (Exception e) {
            // 忽略异常
        }
        ctx.addUser("[系统提示：你刚刚重复调用了相同的工具。请换一种方式完成任务，"
                + "或直接用文本回答。]");
        return selfCorrectionAttempts; // 信号：继续循环
    }

    /**
     * HITL 审批状态
     */
    private enum HitlState {NONE, PENDING, APPROVED, DENIED}

    // ==================== 常量 ====================

    /**
     * 流式调用结果封装
     */
    private record StreamResult(String content, String reasoningContent, ONode toolCalls,
                                boolean error, boolean loopAborted) {
        StreamResult(String content, String reasoningContent, ONode toolCalls, boolean error) {
            this(content, reasoningContent, toolCalls, error, false);
        }
    }

    /**
     * 消息准备结果（含是否发生了折叠）
     */
    private record PreparedMessages(List<ChatMessage> messages, boolean foldedThisStep) {
    }

    /**
     * 工具并行执行结果
     */
    private record ToolExecutionResult(List<ToolCallEntry> tcList,
                                       List<ChatMessage> toolResults,
                                       boolean anySuppressed) {
    }
}
