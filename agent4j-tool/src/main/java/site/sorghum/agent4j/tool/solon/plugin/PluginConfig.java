package site.sorghum.agent4j.tool.solon.plugin;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.Map;

/**
 * 插件配置模型——定义从 tool.json 读取的字段。
 * <p>
 * 支持单工具模式（toolName+description）和多工具模式（name+tools映射）。
 *
 * @author Sorghum
 */
@Data
public class PluginConfig {

    /** 插件名称（多工具时用作 toolName 前缀） */
    private String name;
    /** 工具名称——单工具模式时的工具名 */
    private String toolName;
    /** 工具/插件描述——未单独配置的 skill 会使用此描述 */
    private String description;
    /** 多工具映射：skill 子目录名 → 工具配置 */
    private Map<String, ToolConfig> tools;

    public Map<String, ToolConfig> getTools() {
        return tools != null ? tools : Collections.emptyMap();
    }

    public boolean isMultiToolMode() {
        return tools != null && !tools.isEmpty();
    }

    @Data
    public static class ToolConfig {
        private String toolName;
        private String description;
    }
}
