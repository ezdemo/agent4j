package site.sorghum.cutin.core.plugin;

/**
 * 插件上下文：插件在 configure 阶段按类型或名字获取依赖 Bean。
 */
public interface PluginContext {

    /** 按类型获取 Bean，不存在时抛出异常。 */
    <T> T getBean(Class<T> type);

    /** 按名字获取 Bean，不存在时抛出异常。 */
    <T> T getBean(String name);
}
