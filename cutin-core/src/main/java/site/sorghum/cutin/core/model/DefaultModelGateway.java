package site.sorghum.cutin.core.model;

import site.sorghum.cutin.core.context.LoopContext;

import java.util.stream.Stream;

/**
 * 默认模型网关：按模型 id 遍历候选 Provider，实现最基础的故障切换。
 *
 * <p>先注册的 Provider 优先；某个 Provider 抛出 {@link RuntimeException}
 * 时继续尝试下一个，全部失败后抛出最后一个异常。</p>
 */
public final class DefaultModelGateway implements ModelGateway {

    /** Provider 注册表。 */
    private final ModelRegistry registry;

    /** 使用指定注册表创建网关。 */
    public DefaultModelGateway(ModelRegistry registry) {
        this.registry = registry;
    }

    /** 同步调用模型，支持在同一模型 id 的 Provider 间故障切换。 */
    @Override
    public ModelResponse call(ModelCallRequest request, LoopContext context) {
        RuntimeException lastFailure = null;
        for (ModelProvider provider : registry.providers(request.modelId())) {
            try {
                return provider.call(request);
            } catch (RuntimeException exception) {
                lastFailure = exception;
            }
        }
        if (lastFailure == null) {
            throw new IllegalArgumentException("no provider for model: " + request.modelId());
        }
        throw lastFailure;
    }

    /** 流式调用模型，支持在同一模型 id 的 Provider 间故障切换。 */
    @Override
    public Stream<StreamChunk> stream(ModelCallRequest request, LoopContext context) {
        RuntimeException lastFailure = null;
        for (ModelProvider provider : registry.providers(request.modelId())) {
            try {
                return provider.stream(request);
            } catch (RuntimeException exception) {
                lastFailure = exception;
            }
        }
        if (lastFailure == null) {
            throw new IllegalArgumentException("no provider for model: " + request.modelId());
        }
        throw lastFailure;
    }
}
