package site.sorghum.agent4j.bin.builtin;

import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.bin.agent.core.SubAgent;
import site.sorghum.agent4j.bin.model.ModelClient;
import site.sorghum.agent4j.bin.tool.ToolRegistry;
import site.sorghum.agent4j.tool.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Multi-Task 工具 —— 并行创建多个隔离子代理，各自独立执行复杂多步任务。
 * <p>
 * 与 {@link TaskTool} 的区别：
 * <ul>
 *   <li>{@code task} 创建 <b>单个</b> 子代理，适用于单一复杂任务</li>
 *   <li>{@code multi_task} 创建 <b>多个</b> 子代理 <b>并行执行</b>，适用于多个独立任务同时处理</li>
 * </ul>
 * 每个子代理拥有独立的 ConversationContext 和 AgentLoop，
 * 继承父工具集（排除递归 spawn 和用户交互工具）。
 * 全部完成后汇总结果返回。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class MultiTaskTool extends AgentTool {

    @Inject
    private ModelClient modelClient;

    @Override
    public String getName() {
        return "multi_task";
    }

    @Override
    public String getDescription() {
        return "Spawn multiple isolated sub-agents in parallel to handle multiple tasks simultaneously. "
                + "Each sub-agent runs independently and concurrently with its own conversation context. "
                + "Use when multiple independent tasks need deep investigation or implementation across many files. "
                + "Parameter 'tasks' is a JSON array of task objects, each with 'name'(required), "
                + "'arguments'(optional), 'systemPrompt'(optional).";
    }

    @Override
    public String toToolSpec() {
        return """
                ### multi_task
                
                描述：并行创建多个隔离子代理，各自独立执行复杂多步任务。每个子代理有独立上下文，并行执行后汇总结果返回。
                适用于同时需要处理多个独立任务的场景，如同时分析多个文件、并行执行多个独立的功能开发。
                参数: tasks(必填, JSON数组)，每个任务包含 name(必填), arguments(可选), systemPrompt(可选)。可写。
                注意：子代理不可再创建子代理（task/multi_task 工具对子代理不可用）。
                提示：子代理自动获得 workspace_write/workspace_read/workspace_list 工具，可通过共享工作区协作。
                """;
    }

    @Override
    public List<ToolParameter> getParameters() {
        return List.of(
                ToolParameter.arrayParam("tasks", true,
                        """
                                JSON array of task objects. Each task must have
                                 'name' (required), 
                                'arguments' (optional task description),
                                 'systemPrompt' (optional custom system prompt). 
                                Example: [{"name":"analyze",\
                                "arguments":"分析 src/main 下的所有 Java 文件"}, 
                                {"name":"test","arguments":"运行测试"}]""",
                        ToolParameter.objectParam("task", true, "A sub-agent task definition",
                                List.of(
                                        new ToolParameter("name", "string",
                                                true,
                                                "Task name / identifier for this sub-agent"),
                                        new ToolParameter("arguments", "string",
                                                false,
                                                "Task arguments / description,"
                                                        + " as initial instruction for the sub-agent"),
                                        new ToolParameter("systemPrompt", "string",
                                                false,
                                                "Optional custom system prompt"
                                                        + " for this sub-agent, empty to auto-generate")
                                )
                        )
                )
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolResult execute(ToolContext ctx) {
        // 1. 提取 tasks 参数
        Object tasksObj = ctx.getParams().get("tasks");
        if (tasksObj == null) {
            return ToolResult.fail("PARAM_MISSING", "Missing required parameter 'tasks'");
        }

        List<Map<String, Object>> taskList = extractTaskList(tasksObj);
        if (taskList == null) {
            return ToolResult.fail("PARAM_TYPE", "'tasks' must be a JSON array of task objects");
        }
        if (taskList.isEmpty()) {
            return ToolResult.fail("PARAM_EMPTY", "'tasks' array is empty, at least one task is required");
        }

        // 检查父级是否已请求中断
        if (ctx.getLoopController() != null && ctx.getLoopController().isAbortRequested()) {
            return ToolResult.ok("⏹️ 用户已中断，跳过并行任务执行");
        }

        ToolRegistry registry = ctx.getToolRegistry();
        // 捕获父 AgentOutput（通过 TaskTool 的 ThreadLocal 传播机制）
        AgentOutput parentOutput = TaskTool.getCurrentOutput();
        // 捕获父级 AgentLoopController，传播中断信号到子代理
        AgentLoopController parentController = ctx.getLoopController();

        // 2. 为每个 task 创建异步子代理任务
        List<CompletableFuture<SubAgentResult>> futures = new ArrayList<>(taskList.size());

        for (Map<String, Object> taskDef : taskList) {
            // 每次创建子代理前检查中断（用户可能在遍历过程中取消了）
            if (Thread.currentThread().isInterrupted()) {
                for (CompletableFuture<SubAgentResult> f : futures) {
                    if (!f.isDone()) f.cancel(true);
                }
                return ToolResult.ok("⏹️ 用户已中断，部分任务未执行");
            }

            String name = safeString(taskDef.get("name"), "unnamed_task");
            String arguments = safeString(taskDef.get("arguments"), name);
            String customSystemPrompt = safeString(taskDef.get("systemPrompt"), null);

            futures.add(CompletableFuture.supplyAsync(() ->
                    executeSingleSubAgent(name, arguments, customSystemPrompt, registry, parentOutput, parentController)
            ));
        }

        // 3. 等待全部完成（超时保护 10 分钟，且可被中断）
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(600, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // 用户中断：取消所有未完成的子代理
            for (CompletableFuture<SubAgentResult> f : futures) {
                if (!f.isDone()) f.cancel(true);
            }
        } catch (Exception e) {
            // 部分子代理可能超时或失败，收集已完成的
        }

        // 4. 收集所有结果
        List<SubAgentResult> results = new ArrayList<>(futures.size());
        for (CompletableFuture<SubAgentResult> future : futures) {
            try {
                results.add(future.get(1, TimeUnit.SECONDS));
            } catch (Exception e) {
                results.add(new SubAgentResult("unknown", false, null,
                        "TIMEOUT/CANCEL: " + e.getMessage(), Collections.emptyMap()));
            }
        }

        // 5. 聚合用量上报给父会话
        for (SubAgentResult r : results) {
            for (Map.Entry<String, long[]> e : r.usage.entrySet()) {
                long[] u = e.getValue();
                TaskTool.addUsageRecord(new TaskTool.UsageRecord(
                        e.getKey(), u[0], u[1], u[2], u[3]));
            }
        }

        // 6. 格式化输出
        return ToolResult.ok(formatResults(results));
    }

    // ==================== 内部方法 ====================

    /**
     * 执行单个子代理任务。
     */
    private SubAgentResult executeSingleSubAgent(String name, String arguments,
                                                  String customSystemPrompt,
                                                  ToolRegistry registry,
                                                  AgentOutput parentOutput,
                                                  AgentLoopController parentController) {
        try {
            String systemPrompt = buildSystemPrompt(name, arguments, customSystemPrompt, registry);

            SubAgent sub = new SubAgent(modelClient, registry, systemPrompt, parentController);

            // 传播父 AgentOutput 实现实时流式输出
            if (parentOutput != null) {
                sub.setOutput(parentOutput);
            }

            String result = sub.run(arguments, new SubAgentListener());

            // 收集用量
            Map<String, long[]> usage = sub.hasUsage()
                    ? new LinkedHashMap<>(sub.getModelUsage())
                    : Collections.emptyMap();

            return new SubAgentResult(name, true, result, null, usage);

        } catch (IOException e) {
            return new SubAgentResult(name, false, null, "IO_ERROR: " + e.getMessage(), Collections.emptyMap());
        } catch (Exception e) {
            return new SubAgentResult(name, false, null, "ERROR: " + e.getMessage(), Collections.emptyMap());
        }
    }

    /**
     * 构建子代理的 system prompt。
     */
    private String buildSystemPrompt(String name, String arguments,
                                      String customSystemPrompt, ToolRegistry registry) {
        if (customSystemPrompt != null && !customSystemPrompt.isEmpty()) {
            return customSystemPrompt;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("你是一个子代理（任务: ").append(name).append("），专注于完成以下任务：")
                .append(arguments).append("\n\n");
        sb.append("## 可用工具规范\n\n");

        // 附加工具规范（与 SubAgent 过滤逻辑一致）
        for (FunctionTool def : registry.all().values()) {
            if (!SubAgent.SUB_AGENT_DENY.contains(def.name())) {
                String spec = def.descriptionAndMeta();
                if (spec != null && !spec.isEmpty()) {
                    sb.append(spec).append("\n\n---\n\n");
                }
            }
        }

        sb.append("""
                编辑文件时使用 edit_file（SEARCH/REPLACE，search 必须唯一）。
                多文件批量编辑使用 multi_edit。
                不确定文件位置时用 glob/grep 搜索，需要构建/测试时用 run_command。
                """.stripIndent().trim());

        return sb.toString();
    }

    /**
     * 格式化所有子代理的结果为人类可读的文本。
     */
    private String formatResults(List<SubAgentResult> results) {
        StringBuilder out = new StringBuilder();
        out.append("完成 ").append(results.size()).append(" 个并行任务：\n\n");

        for (int i = 0; i < results.size(); i++) {
            SubAgentResult r = results.get(i);
            out.append("═══════════════════════════════════════\n");
            out.append("📋 任务 ").append(i + 1).append(": ").append(r.name).append("\n");
            out.append("状态: ").append(r.success ? "✅ 成功" : "❌ 失败").append("\n");
            if (r.error != null) {
                out.append("错误: ").append(r.error).append("\n");
            }
            out.append("─── 结果 ───\n");
            out.append(r.result != null ? r.result : "(无结果)").append("\n");
        }

        out.append("═══════════════════════════════════════\n");

        // 汇总统计
        long successCount = results.stream().filter(r -> r.success).count();
        long failCount = results.size() - successCount;
        out.append("📊 汇总: ").append(successCount).append(" 个成功");
        if (failCount > 0) {
            out.append(", ").append(failCount).append(" 个失败");
        }
        out.append("\n");

        return out.toString().stripTrailing();
    }

    /**
     * 将 tasks 参数值安全地提取为 List<Map<String, Object>>。
     * 支持已解析的 List 类型和原始 JSON 字符串类型。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractTaskList(Object tasksObj) {
        if (tasksObj instanceof List<?> rawList) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : rawList) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> task = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : map.entrySet()) {
                        task.put(String.valueOf(e.getKey()), e.getValue());
                    }
                    result.add(task);
                }
            }
            return result;
        }
        return null;
    }

    /**
     * 安全地从 Object 提取字符串。
     */
    private static String safeString(Object obj, String defaultValue) {
        if (obj == null) return defaultValue;
        String str = obj.toString();
        return str.isEmpty() ? defaultValue : str;
    }

    // ==================== 内部记录类 ====================

    /**
     * 单个子代理的执行结果。
     */
    private record SubAgentResult(String name, boolean success, String result,
                                  String error, Map<String, long[]> usage) {
    }
}
