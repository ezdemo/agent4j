package site.sorghum.agent4j.tool.solon.lsp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * LSP 语言服务器配置参数——描述一个 Language Server 进程的启动信息。
 * <p>
 * 每个实例对应一个 Language Server（如 gopls、rust-analyzer、typescript-language-server），
 * 包含启动命令、关联文件扩展名、环境变量和初始化选项。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * LspServerParameters params = LspServerParameters.builder()
 *     .name("gopls")
 *     .command(List.of("gopls", "serve"))
 *     .extensions(List.of(".go"))
 *     .enabled(true)
 *     .build();
 *
 * if (params.matchesExtension("/path/to/main.go")) {
 *     String[] cmd = params.getCommandArray();
 * }
 * }</pre>
 *
 * @author Sorghum
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LspServerParameters {

    /**
     * Language Server 名称（唯一标识，如 "gopls"、"typescript"）
     */
    private String name;

    /**
     * 启动命令（如 ["gopls", "serve"] 或 ["typescript-language-server", "--stdio"]）
     */
    private List<String> command;

    /**
     * 关联的文件扩展名列表（如 [".go"]、[".ts", ".tsx", ".js", ".jsx"]）
     */
    private List<String> extensions;

    /**
     * 是否启用该 Language Server
     */
    private boolean enabled;

    /**
     * 进程环境变量（可选）
     */
    private Map<String, String> env;

    /**
     * LSP initialize 请求中的 initializationOptions（可选，Language Server 特定配置）
     */
    private Map<String, Object> initializationOptions;

    // ==================== 便捷方法 ====================

    /**
     * 判断指定文件路径是否匹配本 Language Server 的扩展名。
     * <p>
     * 匹配规则：检查文件路径是否以任一注册的扩展名结尾（忽略大小写）。
     * 如果 {@code extensions} 为 null 或为空，返回 {@code false}。
     * </p>
     *
     * @param filePath 文件绝对路径（如 "/home/user/project/main.go"）
     * @return 如果文件扩展名匹配返回 true
     */
    public boolean matchesExtension(String filePath) {
        if (filePath == null || extensions == null || extensions.isEmpty()) {
            return false;
        }
        String lowerPath = filePath.toLowerCase();
        for (String ext : extensions) {
            if (ext != null && lowerPath.endsWith(ext.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将命令列表转换为字符串数组（供 {@link ProcessBuilder} 使用）。
     * <p>
     * 如果 {@code command} 为 null，返回空数组。
     * </p>
     *
     * @return 命令数组（第一个元素为可执行文件，后续为参数）
     */
    public String[] getCommandArray() {
        if (command == null || command.isEmpty()) {
            return new String[0];
        }
        return command.toArray(new String[0]);
    }

    /**
     * 安全获取环境变量 Map（从未设置时返回空 Map）。
     */
    public Map<String, String> getEnv() {
        return env != null ? env : Collections.emptyMap();
    }

    /**
     * 安全获取初始化选项 Map（从未设置时返回空 Map）。
     */
    public Map<String, Object> getInitializationOptions() {
        return initializationOptions != null ? initializationOptions : Collections.emptyMap();
    }
}
