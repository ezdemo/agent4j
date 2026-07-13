package site.sorghum.agent4j.bin.builtin;

import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.ai.chat.tool.AbsToolProvider;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Param;
import site.sorghum.agent4j.bin.agent.core.SubAgent;
import site.sorghum.agent4j.bin.model.ModelClient;
import site.sorghum.agent4j.bin.session.SessionService;
import site.sorghum.agent4j.bin.tool.ToolRegistry;
import site.sorghum.agent4j.tool.AgentLoopController;
import site.sorghum.agent4j.tool.AgentOutput;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.solon.SolonToTools;

import java.io.IOException;
import java.util.Collection;

/**
 * Task 工具 —— 创建隔离子代理处理复杂多步任务。
 * <p>
 * 使用 {@link SubAgent} 继承父工具集，排除递归 spawn 和用户交互工具。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class TaskTool extends AbsToolProvider implements SolonToTools {

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

    @Inject
    private ModelClient modelClient;

    @ToolMapping(name = "task", description = """
                 创建隔离子代理处理复杂多步任务。子代理有独立上下文，完成后返回结果给主代理。
                 适用于深入调查多个文件或独立功能实现。
                 参数: name(必填), arguments(可选), systemPrompt(可选)。可写。
                 注意：子代理不可再创建子代理（task 工具对子代理不可用）。
                """)
    public String task(@Param(name = "name", description = "技能/任务名称，用于标识子代理的任务类型") String name,
                       @Param(name = "arguments", description = "技能参数描述/任务详情，作为子代理的初始指令", required = false) String arguments,
                       @Param(name = "systemPrompt", description = "可选的子代理系统提示词覆盖，为空时自动生成", required = false) String customSystemPrompt,
                       ToolContext ctx) {
        if (arguments == null) arguments = name;
        try {
            // 检查父级是否已请求中断（通过 AgentLoopController 传播的 ThreadLocal）
            if (ctx.getLoopController() != null && ctx.getLoopController().isAbortRequested()) {
                return "⏹️ 用户已中断，跳过子代理执行";
            }

            ToolRegistry registry = ctx.getLoopController().getToolRegistry();

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
                for (FunctionTool def : registry.all().values()) {
                    if (!SubAgent.SUB_AGENT_DENY.contains(def.name())) {
                        String spec = def.descriptionAndMeta();
                        if (spec != null && !spec.isEmpty()) {
                            sb.append(spec).append("\n\n---\n\n");
                        }
                    }
                }
                // 注入工具使用指引（同父 Agent 的 buildToolInstructions）
                sb.append("""
                        编辑文件用 `edit`（SEARCH/REPLACE，search 必须唯一，先 `read` 确认内容）。
                        批量编辑用 `edit`（单次调用多 edits）。
                        不确定文件位置时用 `glob`/`grep`。
                        需要构建/测试时用 `bash`。
                        结束对话**必须**调用 `finish`，纯文本回复不会退出循环。
                        """.stripIndent().trim());
                systemPrompt = sb.toString();
            }

            // 获取父级 AgentLoopController，传播中断信号到子代理
            AgentLoopController parentController = ctx.getLoopController();

            SubAgent sub = new SubAgent(modelClient, registry, systemPrompt, parentController);

            // 继承父代理的 HITL 模式：主代理开启 HITL 时子代理自动同步
            if (parentController != null && parentController.isHitlMode()) {
                sub.setHitlMode("approval");
            }

            // 将父 Agent 的 AgentOutput 传递给子代理，使其流式输出能实时推送给用户
            AgentOutput parentOutput = getCurrentOutput();
            if (parentOutput != null) {
                sub.setOutput(parentOutput);
            }

            // 继承父级 sessionId 和 sessionService，使子代理的 tools 有正确的会话上下文
            // 且子代理的 token 用量可直接上报，无需经过 static collector
            String parentSessionId = ctx.getSessionId();
            if (parentSessionId != null) {
                sub.setSessionId(parentSessionId);
            }
            SessionService parentSessionService = (parentController != null) ? parentController.getSessionService() : null;
            if (parentSessionService != null) {
                sub.setSessionService(parentSessionService);
            }

            String result = sub.run(arguments, new SubAgentListener());

            // 子代理的 token 用量通过 SubAgent 的 capturingListener 累积到 SubAgent 字段中，
            // 此处将其上报到父会话的 sessionService（累加到会话总用量）。
            // sessionService.updateLastPromptTokens 已在子 AgentLoop 的 streamLLM 中调用。
            // 注意：streamLLM 中调用的是 updateLastPromptTokens（更新最新 prompt tokens），
            // 而非 addUsage（累加总量），所以这里需要用 SubAgent 累积的字段进行 addUsage。
            if (sub.hasUsage() && parentSessionService != null) {
                var usage = sub.getModelUsage();
                for (var e : usage.entrySet()) {
                    long[] u = e.getValue();
                    parentSessionService.addUsage(e.getKey(), (int) u[0], (int) u[1], (int) u[2], (int) u[3]);
                }
            }

            return result;
        } catch (IOException e) {
            return "IO_ERROR: " + e.getMessage();
        }
    }

    @Override
    public Collection<FunctionTool> getSolonTools() {
        return this.getTools();
    }

    @Override
    public String getSystemPrompt() {
        return """
                ## Task 工具
                
                创建隔离子代理处理复杂多步任务。子代理有独立上下文，完成后返回结果给主代理。
                适用于深入调查多个文件或独立功能实现。
                参数: name(必填), arguments(可选), systemPrompt(可选)。可写。
                注意：子代理不可再创建子代理（task 工具对子代理不可用）。
                """;
    }
}
