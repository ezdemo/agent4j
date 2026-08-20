package site.sorghum.loopra.integration.cutin.plugin.prompt;

import site.sorghum.cutin.core.context.LoopContext;

/**
 * 提示词切片提供者 SPI。
 * <p>
 * 任何 {@link site.sorghum.cutin.core.plugin.LoopPlugin} 都可以实现此接口
 * 并通过 {@link PromptRegistry} 注册，实现提示词的插件化贡献。
 * </p>
 */
public interface PromptSliceProvider {

    /**
     * 为当前循环生成一个切片。
     * 返回 null 或空内容表示该提供者在本轮不贡献。
     */
    PromptSlice slice(LoopContext context);
}
