package site.sorghum.agent4j.bin.agent;

import site.sorghum.agent4j.bin.model.ModelClient;
import site.sorghum.agent4j.bin.model.HttpModelClient;
import site.sorghum.agent4j.bin.tool.ToolDef;
import site.sorghum.agent4j.bin.tool.ToolRegistry;

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
     * <p>排除：递归 spawn、计划管理、用户交互、会话任务跟踪。</p>
     */
    private static final Set<String> SUB_AGENT_DENY = new HashSet<>(Arrays.asList(
            "task",                // 防止递归子代理 spawn
            "submit_plan",         // 计划管理（主代理专用）
            "mark_step_complete",  // 计划管理（主代理专用）
            "revise_plan",         // 计划管理（主代理专用）
            "ask_choice",          // 用户交互（主代理专用）
            "todo_write"           // 会话任务跟踪（主代理专用）
    ));

    private final ModelClient client;
    private final ToolRegistry registry;
    private final String systemPrompt;
    private final int maxSteps;

    /** 构造函数（接受 ModelClient 接口，便于 DI） */
    public SubAgent(ModelClient client, ToolRegistry parentRegistry, String systemPrompt) {
        this.client = client;
        this.registry = new ToolRegistry();
        for (Map.Entry<String, ToolDef> e : parentRegistry.all().entrySet()) {
            if (!SUB_AGENT_DENY.contains(e.getKey())) {
                registry.register(e.getValue());
            }
        }
        this.systemPrompt = systemPrompt;
        this.maxSteps = 15;
    }

    /** 构造函数（接受 apiUrl/apiKey/model 字符串，用于非 DI 场景） */
    public SubAgent(String apiUrl, String apiKey, String model,
                     ToolRegistry parentRegistry, String systemPrompt) {
        this(new HttpModelClient(apiUrl, apiKey, model), parentRegistry, systemPrompt);
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
        AgentLoop subLoop = new AgentLoop(client, registry, ctx, maxSteps);
        if (listener != null) subLoop.setListener(listener);
        return subLoop.run(task);
    }
}
