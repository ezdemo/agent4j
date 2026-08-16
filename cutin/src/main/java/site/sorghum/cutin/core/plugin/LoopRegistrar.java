package site.sorghum.cutin.core.plugin;

import site.sorghum.cutin.core.event.EventHandler;
import site.sorghum.cutin.core.event.Hook;
import site.sorghum.cutin.core.loop.InterceptPoint;
import site.sorghum.cutin.core.loop.LoopInterceptor;
import site.sorghum.cutin.core.model.ModelProvider;
import site.sorghum.cutin.core.tool.Tool;
import site.sorghum.cutin.core.tool.ToolProvider;

/**
 * 插件注册中心 SPI：插件在此登记自己的全部扩展点。
 */
public interface LoopRegistrar {

    /** 注册生命周期拦截器。 */
    void addInterceptor(InterceptPoint point, int order, LoopInterceptor interceptor);

    /** 注册单个工具。 */
    void addTool(Tool tool);

    /** 注册工具提供方，提供方内的全部工具会被批量注册。 */
    void addToolProvider(ToolProvider provider);

    /** 注册模型 Provider。 */
    void addModelProvider(ModelProvider provider);

    /** 注册事件处理器。 */
    void addEventHandler(EventHandler handler);

    /** 注册 Hook。 */
    void addHook(Hook hook);
}
