package site.sorghum.loopra.bin.config;

import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Init;
import site.sorghum.loopra.bin.workspace.WorkspaceManager;

import java.io.IOException;
import java.util.*;

/**
 * 配置服务 —— 统一管理 config.json 的读写。
 * <p>
 * 封装 {@link LoopraConfig}，提供运行时配置查询和更新能力，
 * 消除各组件直接操作 config.json 的重复代码。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
@Data
@Component
public class ConfigService {

    @Getter
    private static volatile LoopraConfig config;

    @Init
    public void init() {
        ConfigService.config = loadAndInitializeWorkspace();
    }

    /**
     * 加载配置并注册当前工作区，使工作区可被项目列表发现。
     */
    private static LoopraConfig loadAndInitializeWorkspace() {
        LoopraConfig loadedConfig = LoopraConfig.load();
        if (loadedConfig.workspaceDir() == null) {
            return loadedConfig;
        }

        String workspacePath = loadedConfig.workspaceDir().toAbsolutePath().normalize().toString();
        try {
            WorkspaceManager.getOrCreate(workspacePath);
        } catch (Exception e) {
            log.warn("[workspace] 注册当前工作区失败: {}", e.getMessage());
        }
        return loadedConfig;
    }

    // ==================== 读取 ====================

    /**
     * 获取被禁用的全局工具列表（扁平集合）。
     * 同时支持 LOOPRA_DISABLED_TOOLS 环境变量覆盖。
     */
    public static Set<String> getDisabledTools() {
        return config.disabledTools();
    }

    /**
     * 重新从磁盘加载配置。
     */
    public static synchronized void reload() {
        ConfigService.config = loadAndInitializeWorkspace();
        log.debug("[config] 已重新加载配置");
    }

    // ==================== 写入 ====================

    /**
     * 全量替换禁用工具列表并持久化。
     */
    public static synchronized void setDisabledTools(Collection<String> toolNames) {
        try {
            config.updateAndSave(Map.of("disabledTools",
                    toolNames != null ? new ArrayList<>(toolNames) : Collections.emptyList()));
            ConfigService.config = loadAndInitializeWorkspace();
            log.info("[config] 已更新禁用工具列表，共 {} 个工具", toolNames != null ? toolNames.size() : 0);
        } catch (IOException e) {
            log.error("[config] 更新禁用工具列表失败", e);
        }
    }

    /**
     * 向禁用工具列表中添加工具（合并）并持久化。
     */
    public static synchronized void addDisabledTools(Collection<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) return;
        Set<String> current = new LinkedHashSet<>(config.disabledTools());
        current.addAll(toolNames);
        setDisabledTools(current);
    }

    /**
     * 从禁用工具列表中移除工具并持久化。
     */
    public static synchronized void removeDisabledTools(Collection<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) return;
        Set<String> current = new LinkedHashSet<>(config.disabledTools());
        current.removeAll(toolNames);
        setDisabledTools(current);
    }

    // ==================== 工具只读分类覆盖 ====================

    public static Map<String, Boolean> getToolReadOnlyOverrides() {
        return config.toolReadOnlyOverrides();
    }

    /**
     * 设置单个工具的只读分类覆盖。readOnly 为 null 时恢复工具默认分类。
     */
    public static synchronized void setToolReadOnlyOverride(String toolName, Boolean readOnly) {
        if (toolName == null || toolName.isBlank()) return;
        Map<String, Boolean> current = new LinkedHashMap<>(config.toolReadOnlyOverrides());
        if (readOnly == null) {
            current.remove(toolName);
        } else {
            current.put(toolName, readOnly);
        }
        try {
            config.updateAndSave(Map.of("toolReadOnlyOverrides", current));
            ConfigService.config = loadAndInitializeWorkspace();
            log.info("[config] 已更新工具只读分类: tool={}, readOnly={}", toolName, readOnly);
        } catch (IOException e) {
            log.error("[config] 更新工具只读分类失败: tool={}", toolName, e);
        }
    }

    // ==================== 自动放行工具白名单 ====================

    /**
     * 获取自动放行工具白名单。
     */
    public static List<String> getAutoWhitelist() {
        return config.autoWhitelist();
    }

    /**
     * 全量替换自动放行白名单并持久化。
     */
    public static synchronized void setAutoWhitelist(Collection<String> toolNames) {
        try {
            config.updateAndSave(Map.of("autoWhitelist",
                    toolNames != null ? new ArrayList<>(toolNames) : Collections.emptyList()));
            ConfigService.config = loadAndInitializeWorkspace();
            log.info("[config] 已更新自动放行白名单，共 {} 个工具", toolNames != null ? toolNames.size() : 0);
        } catch (IOException e) {
            log.error("[config] 更新自动放行白名单失败", e);
        }
    }

    /**
     * 向自动放行白名单中添加工具（合并）并持久化。
     */
    public static synchronized void addAutoWhitelist(Collection<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) return;
        Set<String> current = new LinkedHashSet<>(config.autoWhitelist());
        current.addAll(toolNames);
        setAutoWhitelist(current);
    }

    /**
     * 从自动放行白名单中移除工具并持久化。
     */
    public static synchronized void removeAutoWhitelist(Collection<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) return;
        Set<String> current = new LinkedHashSet<>(config.autoWhitelist());
        current.removeAll(toolNames);
        setAutoWhitelist(current);
    }

    // ==================== 通用配置更新 ====================

    /**
     * 合并更新配置项（只更新非空字段）并持久化到 config.json。
     */
    public static synchronized void updateConfig(Map<String, Object> updates) {
        try {
            config.updateAndSave(updates);
            ConfigService.config = loadAndInitializeWorkspace();
            log.debug("[config] 已更新配置项: {}", updates.keySet());
        } catch (IOException e) {
            log.error("[config] 更新配置失败", e);
        }
    }

    /**
     * 移除指定配置项并持久化到 config.json。
     *
     * @param key 要移除的配置键
     */
    public static synchronized void removeConfigKey(String key) {
        try {
            config.removeAndSave(key);
            ConfigService.config = loadAndInitializeWorkspace();
            log.debug("[config] 已移除配置项: {}", key);
        } catch (IOException e) {
            log.error("[config] 移除配置项失败: {}", key, e);
        }
    }
}
