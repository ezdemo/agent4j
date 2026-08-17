package site.sorghum.loopra.bin.tool;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.ai.chat.tool.FunctionTool;
import site.sorghum.loopra.bin.agent.environment.SessionEnvironment;
import site.sorghum.loopra.bin.agent.prompt.EnvInfoUtil;
import site.sorghum.loopra.bin.agent.prompt.PromptPrefix;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * 工具系统初始化器 —— 抽取 LoopraAgent 与 AgentService 中的重复代码。
 * <p>
 * 负责：注册 AgentTool → ToolRegistry、收集 toolSpecs、加载项目文档、
 * 初始化 SkillStoreV2、构建 system prompt、创建 PromptPrefix。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
public final class ToolSystemInitializer {

    private ToolSystemInitializer() {
    }

    private static final int MAX_PROJECT_DOC_BYTES = 256 * 1024;

    private static final List<String> PROJECT_RULE_NAMES = List.of(
            "loopra.md",
            "claude.md",
            "agents.md",
            "agent.md"
    );

    /**
      * 使用单个会话环境执行完整的工具系统初始化。
     */
    public static ToolSystem initialize(SessionEnvironment environment, Set<String> disabledTools,
                                    String defaultSystemPrompt) {
        Path workspace = environment == null ? null : environment.executionRoot();
        final Set<String> effectiveDisabledTools = disabledTools != null ? disabledTools : Collections.emptySet();

        // 1. 创建 ToolRegistry 并设置禁用工具
        final ToolRegistry registry = new ToolRegistry();
        registry.setDisabledTools(effectiveDisabledTools);
        registry.setEnvironment(environment);
        if (!effectiveDisabledTools.isEmpty()) {
            log.info("[config] 已禁用工具: {}", String.join(", ", effectiveDisabledTools));
        }

        // 2. 使用 ToolScanUtil 统一扫描工具（Solon IoC + Skill 文件系统）
        List<FunctionTool> agentTools = ToolScanUtil.scanTools(workspace);

        // 3. 收集工具规范文本 & 注册工具
        StringBuilder toolSpecsBuilder = new StringBuilder();
        toolSpecsBuilder.append("\n\n## 可用工具规范\n\n");

        for (FunctionTool tool : agentTools) {
            String toolSpec = tool.descriptionAndMeta();
            if (toolSpec != null && !toolSpec.isEmpty()) {
                toolSpecsBuilder.append(toolSpec).append("\n\n---\n\n");
            }
        }

        // 4. 加载基准系统提示词（编码代理身份规则）
        String systemPrompt = defaultSystemPrompt;
        // 4.1 加载solon skill 基准提示词
        systemPrompt  = systemPrompt + "\n\n" + ToolScanUtil.getSkillToolDescription(workspace);
        // 5. 追加工具规范到 system prompt
        systemPrompt = systemPrompt + "\n\n";

        // 6. 注入环境信息（工作目录、平台、Shell、OS 版本、当前日期）——随项目而变
        systemPrompt = systemPrompt + "\n\n---\n\n" + EnvInfoUtil.buildEnvInfo(workspace);

        // 7. 项目文档后置到最底部 —— 最大化前缀缓存命中。
        //    稳定的 system prompt（身份/规则/工具定义/Skill 索引）保持在头部，
        //    项目特定的 loopra.md/CLAUDE.md 放在末尾，换项目时只需 discard 尾部缓存。
        //    计划模式等动态状态不进 system prompt，由 AgentLoop 按需注入工具约定尾部。
        String projectMd = loadProjectMd(workspace);
        if (!projectMd.isEmpty()) {
            systemPrompt = systemPrompt + "\n\n---\n\n" + projectMd;
        }

        // 8. 项目记忆不在此注入 —— 由 memory 工具按需检索，避免每轮占用上下文。
        //    DEFAULT_SYSTEM_PROMPT 中引导 AI 首次接入项目时主动调用 memory 工具检索已有记忆。

        // 9. 构建 PromptPrefix（缓存优先）
        PromptPrefix prefix = new PromptPrefix(systemPrompt, registry.toOpenAiTools());

        log.info("[init] 工具系统初始化完成 — 工具数: {}", agentTools.size());
        return new ToolSystem(registry, prefix);
    }

    /**
      * 从执行根加载项目规则文件。
      * <p>文件名大小写不敏感匹配，迁移时能保留诸如 {@code AGENTS.md}
      * 或 {@code CLAUDE.md} 的原始文件名。</p>
     */
    public static String loadProjectMd(Path workspace) {
        if (workspace == null || !Files.isDirectory(workspace)) {
            return "";
        }

        Map<String, Path> matched = new LinkedHashMap<>();
        try (Stream<Path> entries = Files.list(workspace)) {
            for (Path file : entries.toList()) {
                if (!Files.isRegularFile(file)) continue;
                Path fileName = file.getFileName();
                if (fileName == null) continue;
                String canonicalName = fileName.toString().toLowerCase();
                if (PROJECT_RULE_NAMES.contains(canonicalName)) {
                    matched.putIfAbsent(canonicalName, file);
                }
            }
        } catch (IOException e) {
            log.warn("[init] 读取项目根目录失败: {}", e.getMessage());
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (String name : PROJECT_RULE_NAMES) {
            Path file = matched.get(name);
            if (file == null) continue;
            try {
                if (Files.size(file) > MAX_PROJECT_DOC_BYTES) {
                    log.warn("[init] 跳过过大的项目规则文件: {} ({} bytes)", file, Files.size(file));
                    continue;
                }
                String content = Files.readString(file).trim();
                if (content.isEmpty()) continue;
                if (sb.length() > 0) {
                    sb.append("\n\n");
                }
                sb.append("## ").append(file.getFileName()).append("\n\n").append(content);
            } catch (IOException e) {
                log.warn("[init] 读取项目规则文件失败: {} - {}", file, e.getMessage());
            }
        }
        return sb.toString();
    }

    /**
     * 初始化结果，包含所有已初始化的组件。
     */
    public record ToolSystem(ToolRegistry toolRegistry, PromptPrefix promptPrefix) {
    }
}
