package site.sorghum.loopra.bin.config;

/**
 * 配置变更事件 —— 通过 DamiBus 广播，由监听者自行处理。
 * <p>
 * 用于替代"Controller → Service → Agent → Loop → Client"的穿透式调用链。
 * 新增配置项时只需在监听器的 switch 中添加一个 case。
 * </p>
 *
 * @param key   配置键（如 "model"、"reasoningEffort"、"hitl"）
 * @param value 配置值
 */
public record ConfigChangedEvent(String key, Object value) {
}
