package site.sorghum.cutin.core.loop;

/**
 * 生命周期拦截器函数式接口。
 *
 * <p>插件通过注册不同拦截点的拦截器，在循环的关键阶段插入自定义逻辑；
 * 返回值 {@link InterceptDecision} 决定链是否继续以及下一步如何流转。</p>
 */
@FunctionalInterface
public interface LoopInterceptor {

    /** 处理一次拦截，返回决策。 */
    InterceptDecision intercept(InterceptContext context);
}
