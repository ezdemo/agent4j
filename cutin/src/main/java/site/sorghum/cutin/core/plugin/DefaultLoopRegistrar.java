package site.sorghum.cutin.core.plugin;

import site.sorghum.cutin.core.event.EventBus;
import site.sorghum.cutin.core.event.EventHandler;
import site.sorghum.cutin.core.event.Hook;
import site.sorghum.cutin.core.event.HookRegistry;
import site.sorghum.cutin.core.loop.InterceptPoint;
import site.sorghum.cutin.core.loop.InterceptorRegistry;
import site.sorghum.cutin.core.loop.LoopInterceptor;
import site.sorghum.cutin.core.model.ModelProvider;
import site.sorghum.cutin.core.model.ModelRegistry;
import site.sorghum.cutin.core.tool.DefaultToolRegistry;
import site.sorghum.cutin.core.tool.Tool;
import site.sorghum.cutin.core.tool.ToolProvider;
import site.sorghum.cutin.core.tool.ToolRegistry;

/** {@link LoopRegistrar} 的默认实现。 */
public final class DefaultLoopRegistrar implements LoopRegistrar {
    private final InterceptorRegistry interceptors;
    private final ToolRegistry tools;
    private final ModelRegistry models;
    private final EventBus events;
    private final HookRegistry hooks = new HookRegistry();

    public DefaultLoopRegistrar() {
        this(new InterceptorRegistry(), new DefaultToolRegistry(), new ModelRegistry(), new EventBus());
    }

    public DefaultLoopRegistrar(InterceptorRegistry interceptors, ToolRegistry tools, ModelRegistry models, EventBus events) {
        this.interceptors = interceptors;
        this.tools = tools;
        this.models = models;
        this.events = events;
        events.addHandler(hooks);
    }

    @Override
    public void addInterceptor(InterceptPoint point, int order, LoopInterceptor interceptor) { registerInterceptor(point, order, interceptor); }

    @Override
    public Registration registerInterceptor(InterceptPoint point, int order, LoopInterceptor interceptor) {
        return interceptors.register(point, order, interceptor);
    }

    @Override
    public void addTool(Tool tool) { registerTool(tool); }

    @Override
    public Registration registerTool(Tool tool) {
        Tool previous = tools.find(tool.id()).orElse(null);
        tools.register(tool);
        return () -> {
            if (tools.unregister(tool.id(), tool) && previous != null) {
                tools.register(previous);
            }
        };
    }

    @Override
    public void addToolProvider(ToolProvider provider) { registerToolProvider(provider); }

    @Override
    public Registration registerToolProvider(ToolProvider provider) {
        java.util.List<Registration> registrations = provider.tools().stream().map(this::registerTool).toList();
        return Registration.composite(registrations);
    }

    @Override
    public void addModelProvider(ModelProvider provider) { registerModelProvider(provider); }

    @Override
    public Registration registerModelProvider(ModelProvider provider) {
        models.register(provider);
        return () -> models.unregister(provider);
    }

    @Override
    public void addEventHandler(EventHandler handler) { registerEventHandler(handler); }

    @Override
    public Registration registerEventHandler(EventHandler handler) {
        return events.registerHandler(handler);
    }

    @Override
    public void addHook(Hook hook) { registerHook(hook); }

    @Override
    public Registration registerHook(Hook hook) {
        return hooks.register(hook);
    }

    public InterceptorRegistry interceptors() { return interceptors; }
    public ToolRegistry tools() { return tools; }
    public ModelRegistry models() { return models; }
    public EventBus events() { return events; }
    public HookRegistry hooks() { return hooks; }
}
