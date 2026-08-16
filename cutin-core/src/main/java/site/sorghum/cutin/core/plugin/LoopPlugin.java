package site.sorghum.cutin.core.plugin;

/**
 * 插件 SPI：cutin 的全部业务扩展都以插件形式接入。
 *
 * <p>生命周期为 创建 → configure 注入依赖 → register 注册扩展 → start 启动；
 * 停止时按注册逆序调用 stop。核心层只提供生命周期，不预置具体业务插件。</p>
 */
public interface LoopPlugin {

    /** 插件唯一标识。 */
    String id();

    /** 配置阶段：从插件上下文获取依赖 Bean。 */
    default void configure(PluginContext context) {
    }

    /** 注册阶段：把拦截器、工具、模型 Provider、Skill 等扩展登记到引擎。 */
    void register(LoopRegistrar registrar);

    /** 启动阶段：初始化插件自己的资源。 */
    default void start() {
    }

    /** 停止阶段：释放插件资源。 */
    default void stop() {
    }
}
