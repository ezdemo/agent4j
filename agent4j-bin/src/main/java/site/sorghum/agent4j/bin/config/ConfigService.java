package site.sorghum.agent4j.bin.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Init;
import site.sorghum.agent4j.bin.config.Agent4jConfig;

import java.io.IOException;
import java.util.*;

/**
 * 配置服务 —— 统一管理 config.json 的读写。
 * <p>
 * 封装 {@link Agent4jConfig}，提供运行时配置查询和更新能力，
 * 消除各组件直接操作 config.json 的重复代码。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
@Data
@Component
public class ConfigService {

    private volatile Agent4jConfig config;

    @Init
    public void init() {
        try {
            this.config = Agent4jConfig.load();
        } catch (IOException e) {
            log.error("[config] 初始化配置失败", e);
        }
    }

    // ==================== 读取 ====================

    /**
     * 获取被禁用的全局工具列表（扁平集合）。
     * 同时支持 AGENT4J_DISABLED_TOOLS 环境变量覆盖。
     */
    public Set<String> getDisabledTools() {
        return config.disabledTools();
    }

    /**
     * 重新从磁盘加载配置。
     */
    public synchronized void reload() {
        try {
            this.config = Agent4jConfig.load();
            log.debug("[config] 已重新加载配置");
        } catch (IOException e) {
            log.error("[config] 重新加载配置失败", e);
        }
    }

    // ==================== 写入 ====================

    /**
     * 全量替换禁用工具列表并持久化。
     */
    public synchronized void setDisabledTools(Collection<String> toolNames) {
        try {
            config.updateAndSave(Map.of("disabledTools",
                    toolNames != null ? new ArrayList<>(toolNames) : Collections.emptyList()));
            this.config = Agent4jConfig.load();
            log.info("[config] 已更新禁用工具列表，共 {} 个工具", toolNames != null ? toolNames.size() : 0);
        } catch (IOException e) {
            log.error("[config] 更新禁用工具列表失败", e);
        }
    }

    /**
     * 向禁用工具列表中添加工具（合并）并持久化。
     */
    public synchronized void addDisabledTools(Collection<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) return;
        Set<String> current = new LinkedHashSet<>(config.disabledTools());
        current.addAll(toolNames);
        setDisabledTools(current);
    }

    /**
     * 从禁用工具列表中移除工具并持久化。
     */
    public synchronized void removeDisabledTools(Collection<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) return;
        Set<String> current = new LinkedHashSet<>(config.disabledTools());
        current.removeAll(toolNames);
        setDisabledTools(current);
    }

    // ==================== 通用配置更新 ====================

    /**
     * 合并更新配置项（只更新非空字段）并持久化到 config.json。
     */
    public synchronized void updateConfig(Map<String, Object> updates) {
        try {
            config.updateAndSave(updates);
            this.config = Agent4jConfig.load();
            log.debug("[config] 已更新配置项: {}", updates.keySet());
        } catch (IOException e) {
            log.error("[config] 更新配置失败", e);
        }
    }
}
