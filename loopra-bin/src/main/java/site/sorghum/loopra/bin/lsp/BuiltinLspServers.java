package site.sorghum.loopra.bin.lsp;

import org.noear.solon.ai.talents.lsp.LspManager;
import org.noear.solon.ai.talents.lsp.LspServerParameters;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 内置多语言 LSP 服务器配置 —— 数据源自 Solon 的 {@link LspManager#buildLspServers()}。
 * <p>
 * 所有内置服务器默认 {@code enabled = false}，用户需手动启用。
 * 系统内置服务器不会被持久化覆盖——仅在持久化文件无同名配置时才注册。
 * </p>
 * <p>
 * Solon 内置支持 15 种语言：java, typescript, go, python, rust, c-cpp, csharp,
 * ruby, php, bash, lua, dart, swift, kotlin, yaml
 * </p>
 *
 * @author Sorghum
 */
public final class BuiltinLspServers {

    private BuiltinLspServers() {
    }

    /**
     * 创建所有内置 LSP 服务器配置（默认禁用）。
     * <p>数据直接从 Solon 的 {@link LspManager#buildLspServers()} 获取，保持与上游同步。</p>
     *
     * @return 内置服务器配置列表
     */
    public static List<LspServerConfig> createBuiltinServers() {
        List<LspServerConfig> servers = new ArrayList<>();
        Map<String, LspServerParameters> solonServers = LspManager.buildLspServers();

        for (Map.Entry<String, LspServerParameters> entry : solonServers.entrySet()) {
            String name = entry.getKey();
            LspServerParameters sp = entry.getValue();

            LspServerConfig config = new LspServerConfig();
            config.setName(name);
            config.setCommand(sp.getCommand());
            config.setExtensions(sp.getExtensions());
            config.setEnabled(false);                                 // 默认禁用
            config.setScope("user");                                  // 统一使用 "user" 表示全局
            if (!sp.getInitialization().isEmpty()) {
                config.setInitializationOptions(sp.getInitialization());
            }
            if (!sp.getEnv().isEmpty()) {
                config.setEnv(sp.getEnv());
            }
            servers.add(config);
        }

        return servers;
    }
}
