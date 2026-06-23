package site.sorghum.agent4j.bin.agent.core;

import lombok.Getter;
import site.sorghum.agent4j.bin.agent.context.ConversationContext;
import site.sorghum.agent4j.bin.agent.listener.AgentLoopListener;
import site.sorghum.agent4j.bin.agent.model.UserMessage;
import site.sorghum.agent4j.bin.agent.output.SubAgentAgentOutput;
import site.sorghum.agent4j.bin.agent.prompt.PromptPrefix;
import site.sorghum.agent4j.bin.model.ModelClient;
import site.sorghum.agent4j.bin.tool.ToolRegistry;
import site.sorghum.agent4j.tool.AgentLoopController;
import site.sorghum.agent4j.tool.AgentOutput;

import java.io.IOException;
import java.util.*;

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
public class SubAgent {

    /**
     * 子代理禁止使用的工具名集合（全局维护，新增工具自动对子代理可用）。
     * <p>排除：递归 spawn（task 工具）、计划管理、用户交互、会话任务跟踪。</p>
     * <p>子代理创建时复制父工具集时过滤此名单：</p>
     * <ul>
     *   <li><b>task</b> — 防止递归子代理 spawn（子代理不应再创建子代理）</li>
     *   <li>submit_plan / mark_step_complete / revise_plan — 计划管理，主代理专用</li>
     *   <li>ask_choice — 用户交互，主代理专用（子代理无用户交互）</li>
     * </ul>
     * <p>public 可见性供 {@code TaskTool} 构建子代理 system prompt 时保持一致的过滤逻辑。</p>
     */
    public static final Set<String> SUB_AGENT_DENY = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "task",                // 防止递归子代理 spawn
            "multi_task",          // 防止递归多子代理 spawn
            "submit_plan",         // 计划管理（主代理专用）
            "mark_step_complete",  // 计划管理（主代理专用）
            "revise_plan",         // 计划管理（主代理专用）
            "ask_choice"          // 用户交互（主代理专用）
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
     * 是否有用量数据
     */
    public boolean hasUsage() {
        return totalPromptTokens > 0 || totalCompletionTokens > 0;
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
        ConversationContext ctx = new ConversationContext(
                new PromptPrefix(systemPrompt, registry.toOpenAiTools()));
        AgentLoop subLoop = new AgentLoop(client, registry, ctx);

        // 传播父级中断源：子代理主循环会同时检查自身中断标志和父级的 isAbortRequested()
        if (parentController != null) {
            subLoop.setExternalAbortSource(parentController);
        }

        // 将父代理的 AgentOutput 传递给子代理的推理循环，
        // 使用 SubAgentAgentOutput 包装器将所有事件以 sub_xxx 前缀独立通道发送，
        // 前端在独立 Modal 中渲染子代理输出，不占用主消息流。
        if (parentOutput != null) {
            AgentOutput wrapped = new SubAgentAgentOutput(parentOutput, task);
            subLoop.setOutput(wrapped);
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
        return subLoop.run(UserMessage.of(task));
    }
}
