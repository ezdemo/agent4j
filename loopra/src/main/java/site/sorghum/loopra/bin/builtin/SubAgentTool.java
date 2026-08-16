package site.sorghum.loopra.bin.builtin;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.ai.chat.tool.AbsToolProvider;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Param;
import site.sorghum.loopra.bin.agent.core.SubAgent;
import site.sorghum.loopra.bin.agent.output.ParentOutputHolder;
import site.sorghum.loopra.bin.agent.spi.SessionUsageSink;
import site.sorghum.loopra.bin.config.LoopraConfig;
import site.sorghum.loopra.bin.model.LoopraModelProvider;
import site.sorghum.loopra.bin.tool.ToolMetadata;
import site.sorghum.loopra.bin.tool.ToolRegistry;
import site.sorghum.loopra.tool.AgentLoopController;
import site.sorghum.loopra.tool.AgentOutput;
import site.sorghum.loopra.tool.SolonToTools;
import site.sorghum.loopra.tool.ToolContext;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * SubAgent 工具 —— 创建具有预设角色的隔离子代理。
 * <p>
 * 使用 {@link SubAgent} 继承父工具集，排除递归 spawn 和用户交互工具。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
@Component
public class SubAgentTool extends AbsToolProvider implements SolonToTools {

    // ==================== 父 AgentOutput 传播 ====================

    /**
     * 在当前工作线程上设置父 AgentOutput（异步 Future 内部调用）。
     * 实际存储在 {@link ParentOutputHolder}（loopra-model 内核），AgentLoop 分发工具时设置。
     */
    public static void setCurrentOutput(AgentOutput output) {
        ParentOutputHolder.set(output);
    }

    /**
     * 获取当前线程的父 AgentOutput
     */
    public static AgentOutput getCurrentOutput() {
        return ParentOutputHolder.get();
    }

    /**
     * 清除当前线程的父 AgentOutput（finally 中调用）
     */
    public static void clearCurrentOutput() {
        ParentOutputHolder.clear();
    }

    @Inject
    private LoopraModelProvider modelProvider;

    @Inject
    private SubAgentProfileStore profileStore;

    @Inject
    private LoopraConfig loopraConfig;

    @ToolMapping(name = "sub_agent", description = """
                 派生一个带预设角色的隔离子代理，完成后将结果返回给主代理。
                 可用角色: explore（只读项目探索）, implement（实现）, test（测试）, review（只读项目审查）, plan（只读项目方案）。
                 参数: profile(必填), task(必填), instructions(可选)。
                 注意：explore/review/plan 只能使用只读项目工具；`workspace_write` 仅用于主代理与子代理之间的协作通信；子代理不可再创建子代理。
                """)
    public String subAgent(@Param(name = "profile", description = "子代理角色: explore / implement / test / review / plan") String profile,
                           @Param(name = "task", description = "需要子代理完成的具体任务") String task,
                           @Param(name = "instructions", description = "可选的补充要求，不会覆盖角色约束", required = false) String instructions,
                           ToolContext ctx) {
        if (task == null || task.isBlank()) {
            return "INVALID_SUB_AGENT_TASK: task 不能为空";
        }
        final SubAgentProfileConfig selectedProfile;
        try {
            selectedProfile = profileStore.from(profile);
        } catch (IllegalArgumentException e) {
            return "INVALID_SUB_AGENT_PROFILE: " + e.getMessage();
        }
        try {
            // 检查父级是否已请求中断（通过 AgentLoopController 传播的 ThreadLocal）
            if (ctx.getLoopController() != null && ctx.getLoopController().isAbortRequested()) {
                return "⏹️ 用户已中断，跳过子代理执行";
            }

            ToolRegistry registry = ctx.getLoopController().getToolRegistry();
            AgentLoopController parentController = ctx.getLoopController();
            Set<String> allowedTools = selectedProfile.allowedTools(registry.all().values());

            // 计划模式继承：父代理处于计划模式时，子代理工具强制收敛为只读集合
            // （同时影响子代理系统提示词的工具规范列举与子循环的冻结工具列表）
            if (parentController != null && parentController.isPlanMode()) {
                Set<String> readOnlyNames = new LinkedHashSet<>();
                for (FunctionTool def : registry.all().values()) {
                    if (ToolMetadata.isReadOnly(def)) {
                        readOnlyNames.add(def.name());
                    }
                }
                if (allowedTools == null) {
                    allowedTools = readOnlyNames;
                } else {
                    allowedTools.retainAll(readOnlyNames);
                }
            }

            StringBuilder systemPromptBuilder = new StringBuilder(
                    selectedProfile.buildSystemPrompt(task, instructions));
            if (registry.getWorkspace() != null) {
                systemPromptBuilder.append("\n\n## 运行环境\n\n工作目录: `")
                        .append(registry.getWorkspace().toAbsolutePath().normalize())
                        .append('`');
            }
            systemPromptBuilder.append("\n\n## 可用工具规范\n\n");
            for (FunctionTool def : registry.all().values()) {
                if (!SubAgent.SUB_AGENT_DENY.contains(def.name())
                        && (allowedTools == null || allowedTools.contains(def.name()))) {
                    String spec = def.descriptionAndMeta();
                    if (spec != null && !spec.isEmpty()) {
                        systemPromptBuilder.append(spec).append("\n\n---\n\n");
                    }
                }
            }
            boolean terminateOnNoToolCall = parentController == null
                    || parentController.terminateOnNoToolCall();
            systemPromptBuilder.append(terminateOnNoToolCall
                    ? "无工具调用时，模型的纯文本回复会结束对话。"
                    : "结束对话必须调用 `finish`，纯文本回复不会退出循环。");
            String systemPrompt = systemPromptBuilder.toString();

            // 获取父级 AgentLoopController，传播中断信号到子代理
            LoopraModelProvider parentProvider = parentController != null ? parentController.getModelProvider() : null;
            LoopraModelProvider sourceProvider = parentProvider != null ? parentProvider : modelProvider;
            SubAgent sub = new SubAgent(resolveSubProvider(selectedProfile, sourceProvider), registry, systemPrompt, parentController);
            sub.setAllowedTools(allowedTools);

            if (parentController != null) {
                parentController.registerToolCancellation(sub::abort);
                if (parentController.getAgentConfig() != null) {
                    sub.setConfig(parentController.getAgentConfig());
                }
            }

            try {

                // 精确继承父代理的 HITL 三态设置，避免 auto 被降级成 approval。
                if (parentController != null) {
                    sub.setHitlMode(resolveInheritedHitlMode(parentController));
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
                SessionUsageSink parentSink = (parentController != null) ? parentController.getSessionUsageSink() : null;
                if (parentSink != null) {
                    sub.setSessionUsageSink(parentSink);
                }

                String result = sub.run(task, new SubAgentListener());

                // 子代理的 token 用量通过 SubAgent 的 capturingListener 累积到 SubAgent 字段中，
                // 此处将其上报到父会话的用量通道（累加到会话总用量）。
                if (sub.hasUsage() && parentSink != null) {
                    var usage = sub.getModelUsage();
                    for (var e : usage.entrySet()) {
                        long[] u = e.getValue();
                        parentSink.addUsage(e.getKey(), (int) u[0], (int) u[1], (int) u[2], (int) u[3]);
                    }
                }

                return result;
            } finally {
                if (parentController != null) {
                    parentController.clearToolCancellation();
                }
            }
        } catch (IOException e) {
            return "IO_ERROR: " + e.getMessage();
        }
    }

    /**
     * 解析子代理的模型 Provider：角色配置了独立渠道时按渠道创建，否则继承父级渠道。
     */
    private LoopraModelProvider resolveSubProvider(SubAgentProfileConfig profile, LoopraModelProvider fallback) {
        if (profile.modelChannel == null || profile.modelChannel.isBlank()) {
            return fallback.fork();
        }
        LoopraConfig.ModelChannel channel = loopraConfig.modelChannel(profile.modelChannel);
        if (channel == null) {
            log.warn("子代理 {} 配置的模型渠道不存在: {}，回退继承父级渠道", profile.id(), profile.modelChannel);
            return fallback.fork();
        }
        String model = profile.model != null && !profile.model.isBlank()
                ? profile.model
                : (channel.modelEntries().isEmpty() ? loopraConfig.model() : channel.modelEntries().get(0).name());
        // 注意：必须使用 apiUrl()（按协议补全 /chat/completions 或 /responses 后缀），
        // 直接传 baseUrl() 会把请求发到裸地址（如 POST /v1），网关会返回 404 Invalid URL。
        return new LoopraModelProvider(channel.apiUrl(), channel.apiKey(), model,
                loopraConfig.reasoningEffort(), channel.id(), channel.apiProtocol());
    }


    static String resolveInheritedHitlMode(AgentLoopController parentController) {
        return parentController != null ? parentController.getHitlMode() : "free";
    }

    @Override
    public Collection<FunctionTool> getSolonTools() {
        return this.getTools();
    }
}
