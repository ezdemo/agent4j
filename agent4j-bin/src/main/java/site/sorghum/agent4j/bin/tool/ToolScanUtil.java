package site.sorghum.agent4j.bin.tool;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.Solon;
import org.noear.solon.ai.chat.tool.FunctionTool;
import site.sorghum.agent4j.tool.solon.SolonToTools;
import site.sorghum.agent4j.tool.solon.common.Agent4JSkillProvider;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 工具扫描工具类 —— 统一管理工具扫描逻辑。
 * <p>
 * 封装 Solon IoC 容器 AgentTool Bean 发现 + Skill 文件系统扫描，
 * 供 {@link ToolSystemInitializer} 初始化和 {@link ToolRegistry#refresh()} 动态刷新共用，
 * 消除扫描逻辑的重复。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
public class ToolScanUtil {

    /**
     * 扫描并返回所有可用工具，已按类名排序。
     * <p>
     * 扫描来源：
     * <ol>
     *   <li>Solon IoC 容器中所有 {@code AgentTool} 接口的实现 Bean</li>
     *   <li>Skill 文件系统中注册的工具（通过 {@link Agent4JSkillProvider} 加载）</li>
     * </ol>
     * </p>
     *
     * @param workspace 工作区根目录，用于加载 Skill 工具。传 null 则跳过 Skill 扫描。
     * @return 已排序的 AgentTool 列表
     */
    public static List<FunctionTool> scanTools(Path workspace) {
        // 1. 通过 Solon IoC 获取所有 AgentTool Bean
        List<FunctionTool> agentTools = new ArrayList<>(Solon.context().getBeansOfType(FunctionTool.class));

        // 2. 加载 Skill 工具（从文件系统读取）
        if (workspace != null) {
            try {
                SolonToTools solonToTools = Agent4JSkillProvider.getOrCreate(
                        workspace.toAbsolutePath().normalize().toString());
                agentTools.addAll(solonToTools.getSolonTools());
            } catch (Exception e) {
                log.error("[tool-scan] Skill 工具扫描失败: " + e.getMessage());
            }
        }

        // 3. 加载SolonToSKill
        List<SolonToTools> solonToTools = Solon.context().getBeansOfType(SolonToTools.class);
        agentTools.addAll(solonToTools.stream().map(SolonToTools::getSolonTools).flatMap(Collection::stream).toList());

        // 4. 按类名排序，保证顺序稳定
        agentTools.sort(Comparator.comparing(it -> it.getClass().getName()));
        return Collections.unmodifiableList(agentTools);
    }

    public static String getSkillToolDescription(Path workspace){
        StringBuilder content = new StringBuilder();
        // 1. 加载 Skill 工具（从文件系统读取）
        if (workspace != null) {
            SolonToTools solonToTools = Agent4JSkillProvider.getOrCreate(
                    workspace.toAbsolutePath().normalize().toString());
            append(content, solonToTools.getSystemPrompt());
        }

        // 2. 加载SolonToSKill
        List<SolonToTools> solonToTools = Solon.context().getBeansOfType(SolonToTools.class);
        solonToTools.forEach(
                it -> append(content, it.getSystemPrompt())
        );
        return content.toString();
    }

    private static void append(StringBuilder builder, String content) {
        if (content == null || content.trim().isBlank()) {
            return;
        }
        content = content.trim();
        builder.append("\n").append(content);
    }
}
