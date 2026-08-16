package site.sorghum.loopra.integration.cutin.plugin.bootstrap;

import site.sorghum.cutin.core.model.ModelProvider;
import site.sorghum.cutin.core.plugin.AgentPlugin;
import site.sorghum.cutin.core.plugin.LoopPlugin;
import site.sorghum.cutin.core.plugin.LoopRegistrar;
import site.sorghum.loopra.bin.tool.ToolRegistry;

import java.util.Objects;

/**
 * Loopra 对 cutin 的插件化适配层。
 * <p>
 * 插件把 Loopra 的 {@link ModelProvider} 和 {@link ToolRegistry} 注册为 cutin 的
 * {@code ModelProvider}/{@code ToolProvider}，公共 Java/Web API 仍由 loopra 模块提供。
 * </p>
 */
@AgentPlugin(id = "loopra-cutin")
public final class LoopraCutinPlugin implements LoopPlugin {

    private final ModelProvider modelProvider;
    private final ToolRegistry toolRegistry;

    public LoopraCutinPlugin(ModelProvider modelProvider, ToolRegistry toolRegistry) {
        this.modelProvider = Objects.requireNonNull(modelProvider, "modelProvider");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
    }

    @Override
    public String id() {
        return "loopra-cutin";
    }

    @Override
    public void register(LoopRegistrar registrar) {
        registrar.addToolProvider(toolRegistry.cutinRegistry());
        registrar.addModelProvider(modelProvider);
    }
}
