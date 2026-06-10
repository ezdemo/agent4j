package site.sorghum.agent4j.bin.lsp;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * LSP 服务器配置实体，用于内存存储和 JSON 持久化。
 * <p>
 * 与 {@link LspServerDTO} 的区别：
 * <ul>
 *   <li>command 是 {@code List<String>}，已经过解析，可直接用于进程启动</li>
 *   <li>包含 transient 字段 {@code installed}，不参与持久化</li>
 *   <li>提供 {@code matchesExtension()} 用于根据文件扩展名匹配合适的 LSP 服务器</li>
 * </ul>
 * </p>
 *
 * @author Sorghum
 */
@Data
public class LspServerConfig {

    /** 服务器唯一名称（字母数字下划线连字符） */
    private String name;

    /** 启动命令（已解析为列表，如 {@code ["typescript-language-server", "--stdio"]}） */
    private List<String> command;

    /** 关联的文件扩展名列表（如 {@code [".ts", ".tsx"]}） */
    private List<String> extensions;

    /** 是否启用 */
    private boolean enabled;

    /** 作用域（固定为 "user"，即全局作用域） */
    private String scope;

    /** 环境变量键值对 */
    private Map<String, String> env;

    /** LSP 初始化选项（initialize params） */
    private Map<String, Object> initializationOptions;

    /** 命令是否已安装（不持久化，运行时检测） */
    private transient boolean installed;

    public LspServerConfig() {
    }

    /**
     * 判断该 LSP 服务器是否匹配给定的文件路径（基于扩展名）。
     *
     * @param filePath 文件路径，如 {@code /src/main/java/Foo.java}
     * @return 如果扩展名在 {@link #extensions} 列表中则返回 true
     */
    public boolean matchesExtension(String filePath) {
        if (filePath == null || extensions == null || extensions.isEmpty()) {
            return false;
        }
        String lowerPath = filePath.toLowerCase();
        for (String ext : extensions) {
            if (lowerPath.endsWith(ext.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
