package site.sorghum.agent4j.tool.solon.plugin;

import lombok.extern.slf4j.Slf4j;
import org.noear.snack4.ONode;
import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.solon.SolonToTools;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 插件工具提供者——自动扫描 ~/.agent4j/plugin/ 下的插件，
 * 读取 tool.json 配置，将技能子目录注册为 AgentTool。
 * <p>
 * 支持单工具模式（toolName+description）和多工具模式（name+tools映射，每个 skill 独立命名）。
 *
 * @author Sorghum
 */
@Slf4j
@Component
public class PluginToolProvider implements SolonToTools {

    private static final String CONFIG_FILE = "tool.json";

    private Path pluginRoot() {
        return Paths.get(System.getProperty("user.home", "."), ".agent4j", "plugin");
    }

    @Override
    public List<AgentTool> getTools() {
        Path root = pluginRoot();
        if (!Files.exists(root) || !Files.isDirectory(root)) return Collections.emptyList();

        List<AgentTool> tools = new ArrayList<>();
        try (DirectoryStream<Path> plugins = Files.newDirectoryStream(root)) {
            for (Path pluginDir : plugins) {
                if (!Files.isDirectory(pluginDir)) continue;
                PluginConfig config = readConfig(pluginDir);
                if (config == null) continue;

                int count = 0;
                try (DirectoryStream<Path> skills = Files.newDirectoryStream(pluginDir)) {
                    for (Path skillDir : skills) {
                        if (!Files.isDirectory(skillDir) || !isSkillDir(skillDir)) continue;
                        String dirName = skillDir.getFileName().toString();
                        NameDesc nd = resolveNameDesc(config, dirName);
                        if (nd == null) continue;
                        tools.add(new PluginAgentTool(nd.name, nd.desc, skillDir));
                        count++;
                        log.debug("注册插件工具: {} ({} -> {})", nd.name, pluginDir.getFileName(), dirName);
                    }
                }
                if (count == 0) log.warn("插件目录 {} 下未找到含 skill.md 的子目录", pluginDir);
            }
        } catch (IOException e) {
            log.error("扫描插件目录失败: {}", root, e);
        }
        log.info("插件系统加载完成，共注册 {} 个工具", tools.size());
        return tools;
    }

    private PluginConfig readConfig(Path pluginDir) {
        Path cfg = pluginDir.resolve(CONFIG_FILE);
        if (!Files.exists(cfg) || !Files.isRegularFile(cfg)) return null;
        try {
            return ONode.ofJson(Files.readString(cfg)).toBean(PluginConfig.class);
        } catch (Exception e) {
            log.warn("读取配置失败: {} - {}", cfg, e.getMessage());
            return null;
        }
    }

    private NameDesc resolveNameDesc(PluginConfig config, String dirName) {
        // 多工具模式：优先查 tools 映射
        if (config.isMultiToolMode()) {
            PluginConfig.ToolConfig tc = config.getTools().get(dirName);
            if (tc != null && tc.getToolName() != null && !tc.getToolName().isBlank()) {
                String desc = tc.getDescription();
                if (desc == null || desc.isBlank()) desc = fallbackDesc(config, dirName);
                return new NameDesc(tc.getToolName(), desc);
            }
            return null;
        }
        return null;
    }

    private static String autoName(String prefix, String dirName) {
        String clean = dirName.replaceAll("[\\s-]+", "_");
        return prefix.isEmpty() ? clean : prefix.replaceAll("[\\s-]+", "_") + "_" + clean;
    }

    private static String fallbackDesc(PluginConfig config, String dirName) {
        return config.getDescription() != null ? config.getDescription() : dirName;
    }

    private static boolean isSkillDir(Path dir) {
        return Files.exists(dir.resolve("skill.md")) || Files.exists(dir.resolve("SKILL.md"));
    }

    private record NameDesc(String name, String desc) {}
}
