package site.sorghum.cutin.core.event;

/**
 * Hook SPI：带 id、顺序与事件匹配条件的事件处理器。
 *
 * <p>Hook 与 {@link EventHandler} 的区别是拥有稳定的排序和按事件类型
 * 过滤的能力，适合承载跨插件复用的观察与响应逻辑。</p>
 */
public interface Hook {

    /** Hook 唯一标识。 */
    String id();

    /** 执行顺序，数值越小越先执行。 */
    default int order() {
        return 0;
    }

    /** 是否匹配该事件；不匹配时跳过执行。 */
    default boolean matches(LoopEvent event) {
        return true;
    }

    /** 执行 Hook 逻辑。 */
    void run(LoopEvent event);
}
