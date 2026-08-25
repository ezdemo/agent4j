package site.sorghum.cutin.integrations.model;

import org.noear.snack4.ONode;
import site.sorghum.cutin.core.model.ModelCallRequest;
import site.sorghum.cutin.core.model.ModelProvider;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Provider 请求体拦截器：在协议请求体构建完成、尚未发送前切入。
 *
 * <p>通过 {@link #register(ProviderInterceptor)} 注册到全局拦截链，
 * 同步与流式调用都会生效（所有 {@link ModelProvider} 的 {@code _buildBody}
 * 都会先执行拦截链再发送）。拦截器可以就地修改传入的 body，也可以返回
 * 一个新的 {@link ONode} 整体替换最终请求体；返回 {@code null} 表示就地
 * 修改、不替换。抛出异常会直接中断本次模型调用（在网关层走 ON_MODEL_ERROR）。</p>
 *
 * <p>注册的拦截器对所有 Provider、所有协议生效，因此拦截器需要自行按
 * {@code context.modelId()} 与 {@code context.provider()} 过滤目标；
 * body 是各协议私有的 JSON 结构（Chat Completions、Messages、Responses
 * 各不相同），需按目标协议处理。</p>
 */
@FunctionalInterface
public interface ProviderInterceptor {

    /** 全局拦截链（线程安全，按注册顺序执行）。 */
    List<ProviderInterceptor> INTERCEPT_LIST = new CopyOnWriteArrayList<>();

    /** 拦截已构建的协议请求体，返回非 null 则替换，返回 null 表示就地修改、不替换。 */
    ONode intercept(ProviderInterceptContext context);

    /** 注册拦截器到全局链；重复注册同一实例会被忽略。 */
    static void register(ProviderInterceptor interceptor) {
        if (interceptor == null) {
            return;
        }
        if (!INTERCEPT_LIST.contains(interceptor)) {
            INTERCEPT_LIST.add(interceptor);
        }
    }

    /** 从全局链移除拦截器，返回是否确实移除了某个实例。 */
    static boolean unregister(ProviderInterceptor interceptor) {
        return INTERCEPT_LIST.remove(interceptor);
    }

    /** 按注册顺序执行全局拦截链，返回最终请求体。 */
    static ONode run(
        ModelProvider provider,
        String modelId,
        ModelCallRequest request,
        ONode body
    ) {
        return run(INTERCEPT_LIST, provider, modelId, request, body);
    }

    /** 按顺序执行指定拦截链，返回最终请求体。 */
    static ONode run(
        List<ProviderInterceptor> interceptors,
        ModelProvider provider,
        String modelId,
        ModelCallRequest request,
        ONode body
    ) {
        ONode current = body;
        if (interceptors == null || interceptors.isEmpty()) {
            return current;
        }
        for (ProviderInterceptor interceptor : interceptors) {
            if (interceptor == null) {
                continue;
            }
            ONode next = interceptor.intercept(new ProviderInterceptContext(
                provider,
                provider.id(),
                modelId,
                request,
                current
            ));
            if (next != null) {
                current = next;
            }
        }
        return current;
    }
}
