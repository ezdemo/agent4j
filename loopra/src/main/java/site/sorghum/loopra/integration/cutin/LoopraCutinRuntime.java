package site.sorghum.loopra.integration.cutin;

import site.sorghum.cutin.core.loop.DefaultLoopEngine;
import site.sorghum.cutin.core.model.ModelProvider;
import site.sorghum.cutin.core.model.ModelRegistry;
import site.sorghum.cutin.core.plugin.LoopPlugin;
import site.sorghum.cutin.core.plugin.PluginBeanManager;
import site.sorghum.loopra.integration.cutin.plugin.bootstrap.LoopraCutinPlugin;
import site.sorghum.loopra.bin.tool.ToolRegistry;

import java.util.Objects;

/**
  * 为单个 Loopra Agent 宿主持有 cutin 引擎与插件 Bean 管理器。
 *
  * <p>Loopra 功能在这里以插件方式组装，而不是硬编码进 {@code AgentLoop}。
  * 该运行时是引擎与所有已注册 Loopra 插件的唯一生命周期所有者。</p>
 */
public final class LoopraCutinRuntime implements AutoCloseable {

    private final DefaultLoopEngine engine;
    private final PluginBeanManager plugins;
    private boolean started;

    public LoopraCutinRuntime(ModelProvider modelProvider, ToolRegistry registry, LoopPlugin... extraPlugins) {
        Objects.requireNonNull(modelProvider, "modelProvider");
        Objects.requireNonNull(registry, "registry");
        this.engine = new DefaultLoopEngine(registry.cutinRegistry(), new ModelRegistry());
        this.plugins = new PluginBeanManager(engine.registrar());
        plugins.registerPlugin(new LoopraCutinPlugin(modelProvider, registry));
        for (LoopPlugin plugin : extraPlugins) {
            if (plugin != null) {
                plugins.registerPlugin(plugin);
            }
        }
    }

    public static LoopraCutinRuntime create(
        ModelProvider modelProvider,
        ToolRegistry registry,
        LoopPlugin... extraPlugins
    ) {
        LoopraCutinRuntime runtime = new LoopraCutinRuntime(modelProvider, registry, extraPlugins);
        runtime.start();
        return runtime;
    }

    public void start() {
        if (started) {
            return;
        }
        plugins.startAll();
        started = true;
    }

    public void stop() {
        if (!started) {
            return;
        }
        plugins.stopAll();
        started = false;
    }

    public boolean started() {
        return started;
    }

    public DefaultLoopEngine engine() {
        return engine;
    }

    public PluginBeanManager plugins() {
        return plugins;
    }

    @Override
    public void close() {
        stop();
    }
}
