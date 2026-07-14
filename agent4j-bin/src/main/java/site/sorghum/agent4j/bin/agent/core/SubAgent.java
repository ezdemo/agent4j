package site.sorghum.agent4j.bin.agent.core;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import site.sorghum.agent4j.bin.agent.context.ConversationContext;
import site.sorghum.agent4j.bin.agent.hitl.SubAgentHITLBroker;
import site.sorghum.agent4j.bin.agent.listener.AgentLoopListener;
import site.sorghum.agent4j.bin.agent.model.UserMessage;
import site.sorghum.agent4j.bin.agent.output.SubAgentAgentOutput;
import site.sorghum.agent4j.bin.agent.prompt.PromptPrefix;
import site.sorghum.agent4j.bin.config.Agent4jConfig;
import site.sorghum.agent4j.bin.model.ModelClient;
import site.sorghum.agent4j.bin.session.SessionService;
import site.sorghum.agent4j.bin.tool.ToolRegistry;
import site.sorghum.agent4j.tool.AgentLoopController;
import site.sorghum.agent4j.tool.AgentOutput;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 子代理 —— 隔离的子 AgentLoop，继承父工具集。
 * <p>
 * 参考 Agent4j TS 的 subagent.ts：
 * 创建一个独立的子循环，继承父级的 ToolRegistry 但排除某些工具，
 * 执行完返回最终结果。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
public class SubAgent {

    /**
     * 子代理禁止使用的工具名集合（全局维护，新增工具自动对子代理可用）。
     * <p>排除：递归 spawn、工作流管理、会话任务跟踪、用户交互。</p>
     * <ul>
     *   <li><b>task</b> — 防止递归子代理 spawn</li>
     *   <li>workflow_start / workflow_step / workflow_status — 工作流管理，主代理专用</li>
     *   <li>goal_mark_step — 目标跟踪，主代理专用</li>
     *   <li>ask_choice — 用户交互，主代理专用（子代理无用户交互）</li>
     * </ul>
     * <p>public 可见性供 {@code TaskTool} 构建子代理 system prompt 时保持一致的过滤逻辑。</p>
     */
    public static final Set<String> SUB_AGENT_DENY = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "task",                // 防止递归子代理 spawn
            "workflow_start",      // 工作流创建（主代理专用）
            "workflow_step",       // 工作流转步（主代理专用）
            "workflow_status",     // 工作流状态（主代理专用）
            "goal_mark_step",      // 目标步骤标记（主代理专用）
            "ask_choice"           // 用户交互（主代理专用）
    )));

    private final ModelClient client;
    private final ToolRegistry registry;
    private final String systemPrompt;
    /**
     * 父级 AgentLoopController —— 用于传播父级的中断信号到子代理。
     * 子代理的主循环会同时检查自身中断标志和父级的 isAbortRequested()。
     */
    private final AgentLoopController parentController;
    /**
     * 父代理的 AgentOutput 引用 —— 用于将子代理的流式输出实时推送给用户。
     * 通过 {@link #setOutput(AgentOutput)} 由 TaskTool 注入。
     */
    private AgentOutput parentOutput = null;
    /** 父会话 ID（用于子代理 tools 中的 sessionId 传递） */
    private String sessionId = null;
    /** 父会话服务（用于子代理 token 用量直接上报） */
    private SessionService sessionService = null;
    /** 代理配置（继承父级，确保上下文折叠/工具超时等行为一致） */
    private Agent4jConfig config = null;
    /** 子代理 HITL 模式。默认 "free"，由父代理精确继承 free/approval/auto。 */
    private String hitlMode = "free";
    /** 子代理的 AgentLoop 引用（用于 HITL 暂停-恢复） */
    private volatile AgentLoop subLoop = null;
    /** 在子循环创建前收到的取消信号。 */
    private final AtomicBoolean abortRequested = new AtomicBoolean(false);
    /** 子代理唯一标识（由 SubAgentAgentOutput 分配，用于 HITL Broker 注册） */
    private int subAgentId = 0;
    /**
     * 获取按模型分别累计的 token 用量: model -> [prompt, completion, cacheHit, cacheMiss]
     */
    @Getter
    private final Map<String, long[]> modelUsage = new LinkedHashMap<>();
    /**
     * 获取累计 prompt token 数
     */
    // ==================== 子代理用量追踪 ====================
    @Getter
    private long totalPromptTokens;
    /**
     * 获取累计 completion token 数
     */
    @Getter
    private long totalCompletionTokens;
    /**
     * 获取累计 cache hit token 数
     */
    @Getter
    private long totalCacheHit;
    /**
     * 获取累计 cache miss token 数
     */
    @Getter
    private long totalCacheMiss;

    /**
     * 构造函数（接受 ModelClient 接口，便于 DI）
     *
     * @param client            模型客户端（与父级共享同一实例）
     * @param parentRegistry    父级工具注册表（复制并过滤后用于子代理）
     * @param systemPrompt      子代理的系统提示词
     * @param parentController  父级 AgentLoopController（可 null，用于传播中断信号）
     */
    public SubAgent(ModelClient client, ToolRegistry parentRegistry, String systemPrompt,
                    AgentLoopController parentController) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        // 创建独立注册表，通过 forceDenyTools 硬性过滤（禁止递归 spawn 等）
        this.registry = Objects.requireNonNull(parentRegistry, "parentRegistry must not be null").copy();
        this.registry.setForceDenyTools(SUB_AGENT_DENY);
        this.systemPrompt = systemPrompt;
        this.parentController = parentController;
    }

    /**
     * 构造函数（无父级 controller，用于测试或独立场景）
     */
    public SubAgent(ModelClient client, ToolRegistry parentRegistry, String systemPrompt) {
        this(client, parentRegistry, systemPrompt, null);
    }

    /**
     * 设置父代理的 AgentOutput，使子代理的流式输出能通过父代理的通道实时推送给用户。
     *
     * @param output 父代理的输出接口（ConsoleAgentOutput / SseAgentOutput 等）
     */
    public void setOutput(AgentOutput output) {
        this.parentOutput = output;
    }

    /**
     * 设置父会话 ID，用于子代理 tools 中的 sessionId 传递。
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * 设置父会话服务，用于子代理 token 用量直接上报。
     */
    public void setSessionService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /**
     * 设置代理配置，继承父级以确保上下文折叠/工具超时等行为一致。
     */
    public void setConfig(Agent4jConfig config) {
        this.config = config;
    }

    /**
     * 是否有用量数据
     */
    public boolean hasUsage() {
        return totalPromptTokens > 0 || totalCompletionTokens > 0;
    }

    /**
     * 设置子代理 HITL 模式。
     * <p>默认 "free"，保持向后兼容；TaskTool 会传入父代理当前的完整模式。</p>
     *
     * @param mode "free" / "approval" / "auto"，向后兼容 "true"/"false"
     */
    public void setHitlMode(String mode) {
        this.hitlMode = mode;
    }

    /**
     * 获取子代理 HITL 模式。
     */
    public String getHitlMode() {
        return hitlMode;
    }

    /**
     * 获取子代理唯一标识（由 SubAgentAgentOutput 创建时分配）。
     */
    public int getSubAgentId() {
        return subAgentId;
    }

    /**
     * 显式停止子代理。可在子循环创建前调用，创建后会中止模型流和活动工具。
     */
    public void abort() {
        abortRequested.set(true);
        AgentLoop loop = subLoop;
        if (loop != null) {
            loop.requestUserAbort();
        }
    }

    /**
     * 运行子代理，返回最终回复。
     * 子代理拥有独立的 ConversationContext 和 AgentLoop，
     * 继承父级工具集（排除递归 spawn 和用户交互工具）。
     *
     * @param task     子代理的任务描述
     * @param listener 事件监听（可选）
     * @return 子代理的最终回复文本
     */
    public String run(String task, AgentLoopListener listener) throws IOException {
        if (abortRequested.get()) {
            return "⏹️ 子代理已取消";
        }
        ConversationContext ctx = new ConversationContext(
                new PromptPrefix(systemPrompt, registry.toOpenAiTools()));
        // 继承父级配置（config、sessionId、sessionService）和父代理的完整 HITL 模式。
        Agent4jConfig effectiveConfig = (this.config != null) ? this.config : Agent4jConfig.getInstance();
        this.subLoop = new AgentLoop(client, registry, ctx, this.hitlMode, effectiveConfig);
        AgentLoop subLoop = this.subLoop;

        if (abortRequested.get()) {
            subLoop.requestUserAbort();
            return "⏹️ 子代理已取消";
        }

        // 同时观察显式子代理取消和父级用户中断，覆盖 run() 启动前后的竞争窗口。
        subLoop.setExternalAbortCheck(() -> abortRequested.get()
                || (parentController != null && parentController.isAbortRequested()));

        // 将父代理的 AgentOutput 传递给子代理的推理循环，
        // 使用 SubAgentAgentOutput 包装器将所有事件以 sub_xxx 前缀独立通道发送，
        // 前端在独立 Modal 中渲染子代理输出，不占用主消息流。
        if (parentOutput != null) {
            SubAgentAgentOutput wrapped = new SubAgentAgentOutput(parentOutput, task);
            this.subAgentId = wrapped.getSubId();
            subLoop.setOutput(wrapped);
        }

        // 继承父级 sessionId 和 sessionService（用于 tools 中正确的会话上下文和用量上报）
        if (sessionId != null) {
            subLoop.setSessionId(sessionId);
        }
        if (sessionService != null) {
            subLoop.setSessionService(sessionService);
        }

        // 创建用量捕获监听器：拦截 onUsage 记录到 SubAgent 字段，同时委托给外部 listener
        AgentLoopListener capturingListener = new AgentLoopListener() {
            @Override
            public void onReasoning(String r) {
                if (listener != null) listener.onReasoning(r);
            }

            @Override
            public void onToolCall(String n, String a) {
                if (listener != null) listener.onToolCall(n, a);
            }

            @Override
            public void onToolResult(String n, String r) {
                if (listener != null) listener.onToolResult(n, r);
            }

            @Override
            public void onUsage(String model, int prompt, int completion, int total,
                                int cacheHit, int cacheMiss) {
                // 累计总量
                totalPromptTokens += prompt;
                totalCompletionTokens += completion;
                totalCacheHit += cacheHit;
                totalCacheMiss += cacheMiss;
                // 按模型累计
                modelUsage.computeIfAbsent(model != null ? model : "unknown",
                        k -> new long[4]);
                long[] mu = modelUsage.get(model != null ? model : "unknown");
                mu[0] += prompt;
                mu[1] += completion;
                mu[2] += cacheHit;
                mu[3] += cacheMiss;
                // 委托给外部 listener
                if (listener != null) {
                    listener.onUsage(model, prompt, completion, total, cacheHit, cacheMiss);
                }
            }
        };
        subLoop.setListener(capturingListener);
        String result = subLoop.run(UserMessage.of(task));

        // ==================== HITL 暂停-恢复循环 ====================
        // 当子代理的 HitlManager 拦截工具调用时，mainLoop 返回 HITL prompt，
        // subLoop.hasPendingHITL() 为 true。此时阻塞等待用户审批，审批后通过
        // subLoop.run(null) 触发 resumeAfterHITL → mainLoop 继续推理。
        // 如果子代理在执行中再次触发 HITL，while 循环会再次进入等待。
        while (subLoop.hasPendingHITL()) {
            // sub_choice 事件已由 HitlManager 通过 SubAgentAgentOutput 发送到前端
            log.info("[sub] 子代理 HITL 审批等待中, subId={}", subAgentId);

            // 注册到 Broker，然后轮询等待（每秒检查中断状态）
            SubAgentHITLBroker.Pending pending = SubAgentHITLBroker.register(subAgentId);
            boolean approved = false;
            boolean released = false;
            try {
                while (!released) {
                    released = pending.latch().await(1, TimeUnit.SECONDS);
                    // 检查父代理或用户是否已请求中断
                    if (!released && subLoop.isAbortRequested()) {
                        log.info("[sub] 子代理 HITL 被中断信号取消, subId={}", subAgentId);
                        SubAgentHITLBroker.resolve(subAgentId, false);
                        // 等待 resolve 生效
                        pending.latch().await(100, TimeUnit.MILLISECONDS);
                        released = true;
                    }
                }
                approved = pending.approved().get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                approved = false;
            } finally {
                SubAgentHITLBroker.remove(subAgentId);
            }

            log.info("[sub] 子代理 HITL 审批结果: {}, subId={}", approved ? "批准" : "拒绝", subAgentId);
            if (approved) {
                subLoop.approveHITL();
            } else {
                subLoop.denyHITL();
            }
            result = subLoop.run(null); // 触发 resumeAfterHITL → mainLoop 继续
        }

        return result;
    }
}
