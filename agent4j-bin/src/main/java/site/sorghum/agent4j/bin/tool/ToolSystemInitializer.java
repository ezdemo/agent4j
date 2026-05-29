package site.sorghum.agent4j.bin.tool;

import org.noear.solon.Solon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import site.sorghum.agent4j.bin.agent.PromptPrefix;
import site.sorghum.agent4j.bin.skill.SkillStoreV2;
import site.sorghum.agent4j.bin.skill.SkillV2;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 工具系统初始化器 —— 抽取 Agent4jAgent 与 AgentService 中的重复代码。
 * <p>
 * 负责：注册 AgentTool → ToolRegistry、收集 toolSpecs、加载项目文档、
 * 初始化 SkillStoreV2、构建 system prompt、创建 PromptPrefix。
 * </p>
 *
 * @author Sorghum
 */
public class ToolSystemInitializer {

    private static final Logger log = LoggerFactory.getLogger(ToolSystemInitializer.class);

    /**
     * 初始化结果，包含所有已初始化的组件。
     */
    public static class Result {
        public final ToolRegistry toolRegistry;
        public final PromptPrefix promptPrefix;
        public final SkillStoreV2 skillStore;
        public final String systemPrompt;

        Result(ToolRegistry toolRegistry, PromptPrefix promptPrefix,
               SkillStoreV2 skillStore, String systemPrompt) {
            this.toolRegistry = toolRegistry;
            this.promptPrefix = promptPrefix;
            this.skillStore = skillStore;
            this.systemPrompt = systemPrompt;
        }
    }

    /**
     * 执行完整的工具系统初始化。
     *
     * @param workspace        工作区根目录
     * @param apiUrl           LLM API URL
     * @param apiKey           LLM API Key
     * @param disabledTools    禁用的工具名称集合（可为 null）
     * @param blockedPaths     屏蔽的目录列表（可为 null）
     * @param defaultSystemPrompt 默认系统提示词（当 ~/.agent4j/agent4j.md 不存在时使用）
     * @return 初始化后的 Result，包含 ToolRegistry / PromptPrefix / SkillStoreV2 / systemPrompt
     */
    public static Result initialize(Path workspace, String apiUrl, String apiKey,
                                    Set<String> disabledTools, List<String> blockedPaths,
                                    String defaultSystemPrompt) {
        final Set<String> effectiveDisabledTools = disabledTools != null ? disabledTools : Collections.<String>emptySet();
        final List<String> effectiveBlockedPaths = blockedPaths != null ? blockedPaths : Collections.<String>emptyList();

        // 1. 创建 ToolRegistry 并设置禁用工具
        final ToolRegistry registry = new ToolRegistry();
        registry.setDisabledTools(effectiveDisabledTools);
        if (!effectiveDisabledTools.isEmpty()) {
            log.info("[config] 已禁用工具: {}", String.join(", ", effectiveDisabledTools));
        }
        if (!effectiveBlockedPaths.isEmpty()) {
            log.info("[config] 已屏蔽目录: {}", String.join(", ", effectiveBlockedPaths));
        }

        // 2. 收集工具规范文本
        StringBuilder toolSpecsBuilder = new StringBuilder();
        toolSpecsBuilder.append("\n\n## 可用工具规范\n\n");

        // 通过 Solon IoC 获取所有 AgentTool Bean
        List<AgentTool> agentTools = new ArrayList<>(Solon.context().getBeansOfType(AgentTool.class));
        agentTools.sort(Comparator.comparing(it -> it.getClass().getName()));
        for (AgentTool tool : agentTools) {
            String toolSpec = tool.toToolSpec();
            registry.register(new ToolDef(
                    tool.getName(),
                    tool.getDescription(),
                    ToolDefHelper.toParamDefs(tool.getParameters()),
                    args -> {
                        String sessionId = args != null ? (String) args.remove("__sessionId__") : null;
                        return ToolDefHelper.formatResult(tool.execute(
                                new ToolContext(args, workspace, apiUrl, apiKey, registry, effectiveBlockedPaths, sessionId)));
                    },
                    tool.isReadOnly(),
                    tool.isStormExempt(),
                    toolSpec));
            if (toolSpec != null && !toolSpec.isEmpty()) {
                toolSpecsBuilder.append(toolSpec).append("\n\n---\n\n");
            }
        }

        // 3. 加载项目文档（agent4j.md / CLAUDE.md）
        String systemPrompt = loadDefaultSystemPrompt(defaultSystemPrompt);
        String projectMd = loadProjectMd(workspace);
        if (!projectMd.isEmpty()) {
            systemPrompt = projectMd + "\n\n---\n\n" + systemPrompt;
        }

        // 4. 追加工具规范到 system prompt
        systemPrompt = systemPrompt + "\n\n" + toolSpecsBuilder.toString().trim();

        // 5. 初始化 SkillStoreV2 并加载 skill 索引
        SkillStoreV2 skillStore = new SkillStoreV2(workspace,
                Paths.get(System.getProperty("user.home")),
                Collections.emptyList());
        String skillsIndex = skillStore.buildSkillsIndex();
        if (!skillsIndex.isEmpty()) {
            systemPrompt = systemPrompt + "\n\n" + skillsIndex;
            log.info("[skill] 已加载 skill 索引，共 {} 个 skill", skillStore.list().size());
        }

        // 6. 注册 SkillStoreV2 到 Solon 容器
        Solon.context().wrapAndPut(SkillStoreV2.class, skillStore);

        // 7. 构建 PromptPrefix（缓存优先）
        PromptPrefix prefix = new PromptPrefix(systemPrompt, registry.toOpenAiTools());

        log.info("[init] 工具系统初始化完成 — 工具数: {}", agentTools.size());
        return new Result(registry, prefix, skillStore, systemPrompt);
    }

    /**
     * 加载默认系统提示词。
     * 优先级：~/.agent4j/agent4j.md > 传入的默认值
     */
    private static String loadDefaultSystemPrompt(String fallback) {
        Path homePrompt = Paths.get(System.getProperty("user.home"), ".agent4j", "agent4j.md");
        if (java.nio.file.Files.exists(homePrompt)) {
            try {
                String content = new String(java.nio.file.Files.readAllBytes(homePrompt), StandardCharsets.UTF_8);
                if (content != null && !content.trim().isEmpty()) {
                    log.info("[prompt] 从 ~/.agent4j/agent4j.md 加载默认系统提示词（{} 字符）", content.length());
                    return content.trim();
                }
            } catch (IOException e) {
                log.error("[prompt] 读取 ~/.agent4j/agent4j.md 失败: {}", e.getMessage());
            }
        }
        return fallback;
    }

    /**
     * 加载工作区根目录下的 agent4j.md 和 CLAUDE.md。
     */
    private static String loadProjectMd(Path workspace) {
        StringBuilder sb = new StringBuilder();
        for (String name : new String[]{"agent4j.md", "CLAUDE.md"}) {
            Path file = workspace.resolve(name);
            if (java.nio.file.Files.exists(file)) {
                try {
                    String content = new String(java.nio.file.Files.readAllBytes(file), StandardCharsets.UTF_8);
                    if (sb.length() > 0) sb.append("\n\n");
                    sb.append("[来自 ").append(name).append(" 的项目上下文]\n");
                    sb.append(content.trim());
                } catch (IOException ignored) {
                }
            }
        }
        return sb.toString();
    }
}
