package site.sorghum.loopra.bin.command.impl;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;
import site.sorghum.loopra.bin.command.ChatCommand;
import site.sorghum.loopra.bin.command.ChatCommandContext;
import site.sorghum.loopra.bin.command.MessageWrapper;
import site.sorghum.loopra.integration.cutin.plugin.external.ExternalPluginStore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * /plugin — 管理外置插件。
 * <p>
 * 支持通过 JAR 直链一行命令安装插件，安装后立即热注册到全部存活 AgentLoop，
 * 并持久化到 ~/.loopra/plugins/installed.json，重启后自动加载：
 * <pre>
 * /plugin add https://example.com/my-plugin-1.0.0.jar   安装（重复执行同 id 视为更新）
 * /plugin list                                          列出已安装外置插件
 * /plugin remove my-plugin                              卸载
 * /plugin disable my-plugin                             停用（不删除文件）
 * /plugin enable my-plugin                              启用
 * </pre>
 * 注意：外置插件为任意代码，请仅安装可信来源的 JAR。
 * </p>
 *
 * @author Sorghum
 */
@Component
@Slf4j
public class PluginCommand implements ChatCommand {

    /** 外置插件仓库（无状态，按需创建）。 */
    private final ExternalPluginStore store = new ExternalPluginStore();

    @Override
    public String getCommand() {
        return "plugin";
    }

    @Override
    public boolean matches(String input) {
        if (input == null) {
            return false;
        }
        return input.trim().toLowerCase(Locale.ROOT).startsWith("/plugin");
    }

    @Override
    public String getDescription() {
        return "/plugin      外置插件管理：add <jar直链> / list / remove <id> / enable|disable <id>";
    }

    @Override
    public String getArgHint() {
        return "add <jar直链> | list | remove <id> | enable|disable <id>";
    }

    @Override
    public CommandResult execute(MessageWrapper input, ChatCommandContext context) {
        String body = input.getMessage() == null ? "" : input.getMessage().trim();
        // 去掉 "/plugin" 前缀与空白
        String rest = body.substring("/plugin".length()).trim();
        String[] parts = rest.isEmpty() ? new String[0] : rest.split("\\s+", 2);
        String sub = parts.length == 0 ? "list" : parts[0].toLowerCase(Locale.ROOT);
        String arg = parts.length == 2 ? parts[1].trim() : null;

        try {
            switch (sub) {
                case "add", "install" -> doInstall(arg);
                case "list", "ls" -> doList();
                case "remove", "uninstall", "rm" -> doRemove(arg);
                case "enable" -> doToggle(arg, true);
                case "disable" -> doToggle(arg, false);
                default -> log.info("未知子命令: {}。用法: /plugin add <jar直链> | list | remove <id> | enable|disable <id>", sub);
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            log.warn("[plugin] 操作失败: {}", exception.getMessage());
        }
        return CommandResult.CONTINUE;
    }

    /** 安装外置插件。 */
    private void doInstall(String url) {
        if (url == null || url.isBlank()) {
            log.info("用法: /plugin add https://host/path/my-plugin-1.0.0.jar");
            return;
        }
        log.info("[plugin] 正在下载并安装: {} ...", url);
        ExternalPluginStore.InstalledPlugin plugin = store.install(url);
        log.info("[plugin] ✅ 安装完成: {} v{} (sha256={}...)", plugin.id(), plugin.version(),
            plugin.sha256().substring(0, Math.min(12, plugin.sha256().length())));
        log.info("[plugin] 插件已热注册到当前会话，重启后也会自动加载");
    }

    /** 列出已安装外置插件。 */
    private void doList() {
        List<ExternalPluginStore.InstalledPlugin> plugins = store.installed();
        if (plugins.isEmpty()) {
            log.info("(无已安装的外置插件，使用 /plugin add <jar直链> 安装)");
            return;
        }
        log.info("外置插件列表：");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        for (ExternalPluginStore.InstalledPlugin p : plugins) {
            log.info("  {} v{} [{}] {} <- {}", p.id(), p.version(),
                p.enabled() ? "启用" : "停用", sdf.format(new Date(p.installedAt())), p.sourceUrl());
        }
    }

    /** 卸载外置插件。 */
    private void doRemove(String id) {
        if (id == null || id.isBlank()) {
            log.info("用法: /plugin remove <id>（id 可通过 /plugin list 查看）");
            return;
        }
        store.uninstall(id);
        log.info("[plugin] ✅ 已卸载: {}", id);
    }

    /** 启用/停用外置插件。 */
    private void doToggle(String id, boolean enabled) {
        if (id == null || id.isBlank()) {
            log.info("用法: /plugin {} <id>", enabled ? "enable" : "disable");
            return;
        }
        store.setEnabled(id, enabled);
        log.info("[plugin] ✅ 已{}: {}", enabled ? "启用" : "停用", id);
    }
}
