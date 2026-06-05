package site.sorghum.agent4j.tool.solon.plugin;

import lombok.AllArgsConstructor;
import lombok.Getter;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * 插件技能工具——将 plugin 目录下的 skill 包装为 AgentTool。
 * <p>
 * 工具名称和描述来自插件目录下的 tool.json 配置。
 * 执行时读取 skill.md 内容并返回技能目录路径。
 *
 * @author Sorghum
 */
@Getter
@AllArgsConstructor
public class PluginAgentTool extends AgentTool {

    private final String name;
    private final String description;
    private final Path skillDir;

    @Override
    public List<ToolParameter> getParameters() {
        return Collections.emptyList();
    }

    @Override
    public ToolResult execute(ToolContext ctx) {
        try {
            Path skillMd = skillDir.resolve("skill.md");
            if (!Files.exists(skillMd)) {
                return ToolResult.fail("SKILL_NOT_FOUND",
                        "未在 " + skillDir.toAbsolutePath() + " 下找到 skill.md");
            }
            String content = Files.readString(skillMd, StandardCharsets.UTF_8);
            String dirPath = skillDir.toAbsolutePath().normalize().toString();
            return ToolResult.ok("=== 技能目录: " + dirPath + " ===\n\n" + content + "\n\n=== 文件目录 ===\n" + dirPath);
        } catch (Exception e) {
            return ToolResult.fail("PLUGIN_EXEC_ERROR",
                    "执行插件工具 [" + name + "] 失败: " + e.getMessage());
        }
    }
}
