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
 *
 * <p>保留 {@code void addXxx} 作为兼容入口；需要可热卸载的插件使用
 * {@code registerXxx} 并持有返回的注册句柄。</p>
 */
public interface LoopRegistrar {
    void addInterceptor(InterceptPoint point, int order, LoopInterceptor interceptor);
    void addTool(Tool tool);
    void addToolProvider(ToolProvider provider);
    void addModelProvider(ModelProvider provider);
    void addEventHandler(EventHandler handler);
    void addHook(Hook hook);

    Registration registerInterceptor(InterceptPoint point, int order, LoopInterceptor interceptor);
    Registration registerTool(Tool tool);
    Registration registerToolProvider(ToolProvider provider);
    Registration registerModelProvider(ModelProvider provider);
    Registration registerEventHandler(EventHandler handler);
    Registration registerHook(Hook hook);
}
