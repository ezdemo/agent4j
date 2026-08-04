package site.sorghum.loopra.bin.config;

import site.sorghum.loopra.bin.agent.spi.ToolPolicyProvider;

import java.util.Map;
import java.util.Set;

/**
 * ToolPolicyProvider 的 ConfigService 适配实现 —— 将内核 SPI 桥接到 harness 的配置服务。
 * <p>
 * 由上层在构造 ToolRegistry 后注入，作为"禁用工具 / 只读覆盖"的实时来源；
 * 生产路径通常走 {@code setDisabledTools} 快照模式，本提供者为实时读取的兜底。
 * </p>
 *
 * @author Sorghum
 */
public class ConfigServiceToolPolicyProvider implements ToolPolicyProvider {

    @Override
    public Set<String> disabledTools() {
        return ConfigService.getDisabledTools();
    }

    @Override
    public Map<String, Boolean> toolReadOnlyOverrides() {
        return ConfigService.getToolReadOnlyOverrides();
    }
}
