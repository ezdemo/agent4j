package site.sorghum.loopra.integration.extpack;

import site.sorghum.cutin.core.loop.InterceptPoint;
import site.sorghum.cutin.core.loop.LoopInterceptor;
import site.sorghum.cutin.core.tool.Tool;

import java.util.List;

/**
 * 拓展包桥接 SPI：H-SPI 拓展包通过实现本接口，把 Agent 能力贡献给
 * 全部存活的 AgentLoop（工具、拦截器等）。
 *
 * <p>与 cutin 插件（{@code LoopPlugin}）的区别：本接口面向 Solon H-SPI
 * 拓展包（自带容器/配置/依赖的独立模块），桥接层在拓展包 start 后
 * 实例化实现类并注册进引擎，stop 时逆序注销。</p>
 *
 * <p>实现类须提供 public 无参构造（ServiceLoader 实例化），并在 jar 内
 * 以 {@code META-INF/services/site.sorghum.loopra.integration.extpack.LoopraExtPackBridge}
 * 声明。</p>
 */
public interface LoopraExtPackBridge {

    /** 桥接唯一标识（同一拓展包内须唯一；用于运行时注销）。 */
    String id();

    /** 面向使用者的展示名；缺省回退到 {@link #id()}。 */
    default String displayName() {
        return id();
    }

    /** 贡献给 AgentLoop 的工具列表。 */
    default List<Tool> tools() {
        return List.of();
    }

    /** 贡献给 AgentLoop 的拦截器列表。 */
    default List<RegisteredInterceptor> interceptors() {
        return List.of();
    }

    /** 拦截器注册条目：拦截点 + 顺序 + 拦截器。 */
    record RegisteredInterceptor(InterceptPoint point, int order, LoopInterceptor interceptor) {
    }
}
