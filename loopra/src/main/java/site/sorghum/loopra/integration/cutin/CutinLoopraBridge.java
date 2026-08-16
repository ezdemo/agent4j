package site.sorghum.loopra.integration.cutin;

import site.sorghum.cutin.core.loop.DefaultLoopEngine;
import site.sorghum.cutin.core.model.ModelProvider;
import site.sorghum.cutin.core.plugin.LoopPlugin;
import site.sorghum.loopra.bin.tool.ToolRegistry;

/**
  * 把 Loopra 的模型提供方与工具注册表组装进 cutin 引擎。
 * <p>
  * 这是“内部委托给 cutin”的具体接缝：Loopra 公共 API 保持不变，
  * 而 cutin 引擎把 Loopra 的工具与模型提供方当作普通 cutin 扩展看待。
 * </p>
 */
public final class CutinLoopraBridge {

    private CutinLoopraBridge() {
    }

    public static LoopraCutinRuntime newRuntime(
        ModelProvider modelProvider,
        ToolRegistry registry,
        LoopPlugin... extraPlugins
    ) {
        return LoopraCutinRuntime.create(modelProvider, registry, extraPlugins);
    }

    public static DefaultLoopEngine newEngine(ModelProvider modelProvider, ToolRegistry registry) {
        return newRuntime(modelProvider, registry).engine();
    }
}
