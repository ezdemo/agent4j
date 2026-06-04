package site.sorghum.agent4j.bin.builtin;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.bin.agent.AgentOutput;
import site.sorghum.agent4j.bin.agent.SubAgent;
import site.sorghum.agent4j.bin.model.ModelClient;
import site.sorghum.agent4j.bin.tool.ToolRegistry;
import site.sorghum.agent4j.bin.tool.ToolDef;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Task 工具 —— 创建隔离子代理处理复杂多步任务。
 * <p>
 * 使用 {@link SubAgent} 继承父工具集，排除递归 spawn 和用户交互工具。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class TaskTool extends AgentTool {

    // ==================== 父 AgentOutput 传播 ====================

    /**
     * 线程局部变量 — 持有父 Agent 的 AgentOutput 引用。
     * AgentLoop.executeToolCalls() 在异步分发前设置，TaskTool.execute() 在创建 SubAgent 时读取。
     * 使得子代理的流式输出能通过父代理的输出通道（Console/SseEmitter）实时推送给用户。
     */
    private static final ThreadLocal<AgentOutput> PARENT_OUTPUT_TL = new ThreadLocal<>();

    /**
     * 在当前工作线程上设置父 AgentOutput（异步 Future 内部调用）
     */
    public static void setCurrentOutput(AgentOutput output) {
        if (output != null) {
            PARENT_OUTPUT_TL.set(output);
        }
    }

    /**
     * 获取当前线程的父 AgentOutput
     */
    public static AgentOutput getCurrentOutput() {
        return PARENT_OUTPUT_TL.get();
    }

    /**
     * 清除当前线程的父 AgentOutput（finally 中调用）
     */
    public static void clearCurrentOutput() {
        PARENT_OUTPUT_TL.remove();
    }

    // ==================== 子代理用量收集器 ====================

    /**
     * 全局用量收集队列，AgentLoop 在 dispatch 前清空、dispatch 后读取
     */
    private static final ConcurrentLinkedQueue<UsageRecord> subAgentUsageCollector = new ConcurrentLinkedQueue<>();
    @Inject
    private ModelClient modelClient;

    /**
     * 清空收集器（在并行 dispatch 前调用）
     */
    public static void clearUsageCollector() {
        subAgentUsageCollector.clear();
    }

    /**
     * 获取收集器并清空（在并行 dispatch 完成后调用）
     */
    public static ConcurrentLinkedQueue<UsageRecord> drainUsageCollector() {
        ConcurrentLinkedQueue<UsageRecord> drained = new ConcurrentLinkedQueue<>();
        UsageRecord ur;
        while ((ur = subAgentUsageCollector.poll()) != null) {
            drained.add(ur);
        }
        return drained;
    }

    @Override
    public String getName() {
        return "task";
    }

    @Override
    public String getDescription() {
        return "Spawn an isolated sub-agent to handle a complex, multi-step task autonomously. "
                + "The sub-agent inherits most tools and returns a single result. "
                + "Use when a task requires deep investigation across many files.";
    }

    @Override
    public String toToolSpec() {
        return """
                ### task
                
                描述：创建隔离子代理处理复杂多步任务。子代理有独立上下文，完成后返回结果给主代理。
                适用于深入调查多个文件或独立功能实现。
                参数: name(必填), arguments(可选), systemPrompt(可选)。可写。
                注意：子代理不可再创建子代理（task 工具对子代理不可用）。
                """;
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Arrays.asList(
                new ToolParameter("name", "string", true, "技能/任务名称，用于标识子代理的任务类型"),
                new ToolParameter("arguments", "string", false, "技能参数描述/任务详情，作为子代理的初始指令"),
                new ToolParameter("systemPrompt", "string", false, "可选的子代理系统提示词覆盖，为空时自动生成")
        );
    }

    @Override
    public ToolResult execute(ToolContext ctx) {
        String name = ctx.getString("name");
        String arguments = ctx.getString("arguments");
        String customSystemPrompt = ctx.getString("systemPrompt");
        if (arguments == null) arguments = name;
        try {
            ToolRegistry registry = ctx.getToolRegistry();

            // 构建子代理的 system prompt
            // 优先使用调用方传入的 systemPrompt，否则自动组合：任务描述 + 工具规范 + 使用指引
            String systemPrompt;
            if (customSystemPrompt != null && !customSystemPrompt.isEmpty()) {
                systemPrompt = customSystemPrompt;
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append("你是一个子代理，专注于完成以下任务：").append(arguments).append("\n\n");
                sb.append("## 可用工具规范\n\n");
                // 收集并附加工具规范（与 SubAgent 构造函数保持一致的过滤逻辑）
                for (ToolDef def : registry.all().values()) {
                    if (!SubAgent.SUB_AGENT_DENY.contains(def.name())) {
                        String spec = def.toolSpec();
                        if (spec != null && !spec.isEmpty()) {
                            sb.append(spec).append("\n\n---\n\n");
                        }
                    }
                }
                // 注入工具使用指引（同父 Agent 的 buildToolInstructions）
                sb.append("""
                        编辑文件时使用 edit_file（SEARCH/REPLACE，search 必须唯一）。
                        多文件批量编辑使用 multi_edit。
                        不确定文件位置时用 glob/grep 搜索，需要构建/测试时用 run_command。
                        """.stripIndent().trim());
                systemPrompt = sb.toString();
            }

            SubAgent sub = new SubAgent(modelClient, registry, systemPrompt);

            // 将父 Agent 的 AgentOutput 传递给子代理，使其流式输出能实时推送给用户
            AgentOutput parentOutput = getCurrentOutput();
            if (parentOutput != null) {
                sub.setOutput(parentOutput);
            }

            String result = sub.run(arguments, new SubAgentListener());

            // 将子代理的 token 用量报告给父会话
            if (sub.hasUsage()) {
                Map<String, long[]> usage = sub.getModelUsage();
                for (Map.Entry<String, long[]> e : usage.entrySet()) {
                    long[] u = e.getValue();
                    subAgentUsageCollector.add(new UsageRecord(
                            e.getKey(), u[0], u[1], u[2], u[3]));
                }
            }

            return ToolResult.ok(result);
        } catch (IOException e) {
            return ToolResult.fail("IO_ERROR", e.getMessage());
        }
    }

    /**
     * 用量记录（线程安全，用于跨 Future 收集）
     */
    public record UsageRecord(String model, long prompt, long completion, long cacheHit, long cacheMiss) {
    }
}
