package site.sorghum.agent4j.bin.lsp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 内置 13 种编程语言的 LSP 服务器配置。
 * <p>
 * 所有内置服务器默认 {@code enabled = false}，用户需手动启用。
 * 系统内置服务器不会被持久化覆盖——仅在持久化文件无同名配置时才注册。
 * </p>
 *
 * @author Sorghum
 */
public final class BuiltinLspServers {

    private BuiltinLspServers() {
    }

    /**
     * 创建所有内置 LSP 服务器配置（默认禁用）。
     *
     * @return 内置服务器配置列表
     */
    public static List<LspServerConfig> createBuiltinServers() {
        List<LspServerConfig> servers = new ArrayList<>();

        // ---- Java ----
        servers.add(build("java",
                Arrays.asList("jdtls"),
                Arrays.asList(".java", ".jav"),
                "Java (Eclipse JDT.LS)"));

        // ---- TypeScript / JavaScript ----
        servers.add(build("typescript",
                Arrays.asList("typescript-language-server", "--stdio"),
                Arrays.asList(".ts", ".tsx", ".js", ".jsx", ".mjs", ".cjs", ".mts", ".cts"),
                "TypeScript / JavaScript"));

        // ---- Go ----
        servers.add(build("go",
                Arrays.asList("gopls"),
                Arrays.asList(".go"),
                "Go (gopls)"));

        // ---- Python ----
        servers.add(build("python",
                Arrays.asList("pyright-langserver", "--stdio"),
                Arrays.asList(".py", ".pyi", ".pyx", ".pxd"),
                "Python (Pyright)"));

        // ---- Rust ----
        servers.add(build("rust",
                Arrays.asList("rust-analyzer"),
                Arrays.asList(".rs"),
                "Rust (rust-analyzer)"));

        // ---- C / C++ ----
        servers.add(build("c-cpp",
                Arrays.asList("clangd"),
                Arrays.asList(".c", ".h", ".cpp", ".hpp", ".cc", ".cxx", ".hxx", ".c++", ".h++"),
                "C / C++ (clangd)"));

        // ---- C# ----
        servers.add(build("csharp",
                Arrays.asList("OmniSharp", "--stdio"),
                Arrays.asList(".cs", ".csx"),
                "C# (OmniSharp)"));

        // ---- Ruby ----
        servers.add(build("ruby",
                Arrays.asList("solargraph", "stdio"),
                Arrays.asList(".rb", ".rake", ".gemspec"),
                "Ruby (Solargraph)"));

        // ---- PHP ----
        servers.add(build("php",
                Arrays.asList("intelephense", "--stdio"),
                Arrays.asList(".php", ".phtml"),
                "PHP (Intelephense)"));

        // ---- Bash ----
        servers.add(build("bash",
                Arrays.asList("bash-language-server", "start"),
                Arrays.asList(".sh", ".bash", ".bashrc", ".bash_profile", ".zsh", ".zshrc"),
                "Bash / Shell"));

        // ---- Lua ----
        servers.add(build("lua",
                Arrays.asList("lua-language-server"),
                Arrays.asList(".lua"),
                "Lua"));

        // ---- Dart ----
        servers.add(build("dart",
                Arrays.asList("dart", "language-server", "--stdio"),
                Arrays.asList(".dart"),
                "Dart"));

        // ---- Swift ----
        servers.add(build("swift",
                Arrays.asList("sourcekit-lsp"),
                Arrays.asList(".swift"),
                "Swift (SourceKit-LSP)"));

        return servers;
    }

    /**
     * 便捷构造方法。
     *
     * @param name       服务器名称
     * @param command    启动命令（列表格式）
     * @param extensions 关联文件扩展名
     * @param description 描述（存入初始化选项的 description 字段）
     * @return 配置对象（默认禁用）
     */
    private static LspServerConfig build(String name,
                                         List<String> command,
                                         List<String> extensions,
                                         String description) {
        LspServerConfig config = new LspServerConfig();
        config.setName(name);
        config.setCommand(command);
        config.setExtensions(extensions);
        config.setEnabled(false);
        config.setScope("global");
        config.setInitializationOptions(Map.of("description", description));
        return config;
    }
}
