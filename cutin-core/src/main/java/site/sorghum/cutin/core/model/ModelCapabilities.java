package site.sorghum.cutin.core.model;

import java.util.Set;

/**
 * 模型 Provider 的能力声明：支持的模型集合、是否支持流式与工具调用。
 */
public record ModelCapabilities(
    Set<String> models,
    boolean streaming,
    boolean toolCalling
) {
}
