package site.sorghum.loopra.bin.lsp;

import lombok.Data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * LSP 服务器 API 传输对象。
 * <p>
 * 前端以单个字符串传递 command（支持 JSON 数组格式或空格分隔格式），
 * 通过 {@link #toConfig()} 转换为后端使用的 {@link LspServerConfig}。
 * </p>
 *
 * @author Sorghum
 */
@Data
public class LspServerDTO {

    /** 服务器唯一名称 */
    private String name;

    /**
     * 启动命令（前端传递字符串格式）。
     * <p>支持两种格式：
     * <ul>
     *   <li>JSON 数组：{@code ["typescript-language-server", "--stdio"]}</li>
     *   <li>空格分隔：{@code typescript-language-server --stdio}</li>
     * </ul>
     * </p>
     */
    private String command;

    /** 关联的文件扩展名列表 */
    private List<String> extensions;

    /** 是否启用 */
    private boolean enabled = true;

    /** @deprecated 作用域已简化为全局，该字段保留仅用于向后兼容 */
    @Deprecated
    private String scope;

    /** 环境变量键值对 */
    private Map<String, String> env;

    /** LSP 初始化选项 */
    private Map<String, Object> initializationOptions;

    /** 命令是否已安装（只读，来自运行时检测） */
    private boolean installed;

    public LspServerDTO() {
    }

    /**
     * 将 DTO 转换为持久化/运行时使用的 {@link LspServerConfig}。
     * <p>command 字段会被解析为 {@code List<String>}。</p>
     */
    public LspServerConfig toConfig() {
        LspServerConfig config = new LspServerConfig();
        config.setName(this.name);
        config.setCommand(parseCommand(this.command));
        config.setExtensions(this.extensions);
        config.setEnabled(this.enabled);
        config.setScope("user");  // 强制全局作用域，忽略前端传入值
        config.setEnv(this.env);
        config.setInitializationOptions(this.initializationOptions);
        return config;
    }

    /**
     * 从持久化实体 {@link LspServerConfig} 构建 DTO。
     *
     * @param config 持久化配置
     * @return API 传输对象
     */
    public static LspServerDTO fromConfig(LspServerConfig config) {
        LspServerDTO dto = new LspServerDTO();
        dto.setName(config.getName());
        // 将 List<String> 还原为 JSON 数组字符串，方便前端编辑
        dto.setCommand(listToJsonString(config.getCommand()));
        dto.setExtensions(config.getExtensions());
        dto.setEnabled(config.isEnabled());
        dto.setScope(config.getScope());
        dto.setEnv(config.getEnv());
        dto.setInitializationOptions(config.getInitializationOptions());
        dto.setInstalled(config.isInstalled());
        return dto;
    }

    /**
     * 解析前端传递的命令字符串。
     * <p>支持两种格式：
     * <ul>
     *   <li>JSON 数组：{@code ["cmd","arg1","arg2"]} —— 直接反序列化</li>
     *   <li>空格分隔：{@code cmd arg1 arg2} —— 按空格拆分（支持引号包裹含空格的参数）</li>
     * </ul>
     * </p>
     *
     * @param raw 前端传递的原始命令字符串
     * @return 解析后的命令列表
     */
    private static List<String> parseCommand(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }

        String trimmed = raw.trim();

        // 1. 尝试 JSON 数组格式
        if (trimmed.startsWith("[")) {
            try {
                String[] arr = org.noear.snack4.ONode.ofJson(trimmed).toBean(String[].class);
                return new ArrayList<>(Arrays.asList(arr));
            } catch (Exception ignored) {
                // 不是合法的 JSON 数组，回退到空格分隔
            }
        }

        // 2. 空格分隔（支持双引号包裹含空格的参数）
        return shellSplit(trimmed);
    }

    /**
     * 简易 Shell 风格字符串拆分：按空格切分，双引号内的内容视为一个整体。
     */
    private static List<String> shellSplit(String raw) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch == '"') {
                inQuotes = !inQuotes;
            } else if (ch == ' ' && !inQuotes) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(ch);
            }
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }

    /**
     * 将命令列表序列化为 JSON 数组字符串，方便前端展示和编辑。
     */
    private static String listToJsonString(List<String> command) {
        if (command == null || command.isEmpty()) {
            return "";
        }
        return org.noear.snack4.ONode.serialize(command);
    }
}
