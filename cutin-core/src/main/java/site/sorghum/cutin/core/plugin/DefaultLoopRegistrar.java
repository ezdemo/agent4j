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

/**
 * {@link LoopRegistrar} 的默认实现，持有全部注册表与扩展集合。
 *
 * <p>插件注册的拦截器、工具、模型、事件处理器与 Hook
 * 都存放在这里，并由引擎或插件系统读取。</p>
 */
public final class DefaultLoopRegistrar implements LoopRegistrar {

    /** 拦截器注册表。 */
    private final InterceptorRegistry interceptors;
    /** 工具注册表。 */
    private final ToolRegistry tools;
    /** 模型注册表。 */
    private final ModelRegistry models;
    /** 事件总线。 */
    private final EventBus events;
    /** Hook 注册表，已挂到事件总线上。 */
    private final HookRegistry hooks = new HookRegistry();
    /** 使用全新的注册表、工具表、模型表与事件总线创建注册中心。 */
    public DefaultLoopRegistrar() {
        this(new InterceptorRegistry(), new DefaultToolRegistry(), new ModelRegistry(), new EventBus());
    }

    /** 使用外部组件装配注册中心，并把 HookRegistry 接入事件总线。 */
    public DefaultLoopRegistrar(
        InterceptorRegistry interceptors,
        ToolRegistry tools,
        ModelRegistry models,
        EventBus events
    ) {
        this.interceptors = interceptors;
        this.tools = tools;
        this.models = models;
        this.events = events;
        events.addHandler(hooks);
    }

    /** {@inheritDoc} */
    @Override
    public void addInterceptor(InterceptPoint point, int order, LoopInterceptor interceptor) {
        interceptors.add(point, order, interceptor);
    }

    /** {@inheritDoc} */
    @Override
    public void addTool(Tool tool) {
        tools.register(tool);
    }

    /** {@inheritDoc} */
    @Override
    public void addToolProvider(ToolProvider provider) {
        provider.tools().forEach(tools::register);
    }

    /** {@inheritDoc} */
    @Override
    public void addModelProvider(ModelProvider provider) {
        models.register(provider);
    }

    /** {@inheritDoc} */
    @Override
    public void addEventHandler(EventHandler handler) {
        events.addHandler(handler);
    }

    /** {@inheritDoc} */
    public void addHook(Hook hook) {
        hooks.add(hook);
    }

    /** 拦截器注册表。 */
    public InterceptorRegistry interceptors() {
        return interceptors;
    }

    /** 工具注册表。 */
    public ToolRegistry tools() {
        return tools;
    }

    /** 模型注册表。 */
    public ModelRegistry models() {
        return models;
    }

    /** 事件总线。 */
    public EventBus events() {
        return events;
    }

    /** Hook 注册表。 */
    public HookRegistry hooks() {
        return hooks;
    }

}
