package site.sorghum.cutin.integrations.model;

import org.noear.snack4.ONode;
import site.sorghum.cutin.core.model.ModelCallRequest;
import site.sorghum.cutin.core.model.ModelProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * Provider 请求体拦截器：在协议请求体构建完成、尚未发送前切入。
 *
 * <p>通过 provider 构造器注入（例如
 * {@code new OpenAiChatCompletionsProvider(config, interceptor)}），
 * 同步与流式调用都会生效。拦截器可以就地修改传入的 body，也可以返回一个
 * 新的 {@link ONode} 整体替换最终请求体；返回 {@code null} 表示就地修改、
 * 不替换。抛出异常会直接中断本次模型调用（在网关层走 ON_MODEL_ERROR）。</p>
 *
 * <p>注意：body 是各协议私有的 JSON 结构（Chat Completions、Messages、
 * Responses 各不相同），拦截器需要按目标协议处理。</p>
 */
@FunctionalInterface
public interface ProviderInterceptor {

    /**
     * 拦截器
     */
    List<ProviderInterceptor> INTERCEPT_LIST = new ArrayList<>();

    /** 拦截已构建的协议请求体，返回非 null 则替换，返回 null 表示就地修改、不替换。 */
    ONode intercept(ProviderInterceptContext context);

    /** 按顺序执行整条拦截链，返回最终请求体。 */
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
