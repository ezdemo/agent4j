package site.sorghum.cutin.core.model;

import site.sorghum.cutin.core.context.LoopContext;

import java.util.stream.Stream;

/**
 * 模型网关：对引擎屏蔽 Provider 路由与拦截细节。
 *
 * <p>引擎只与网关交互；网关负责把请求路由到合适的 Provider，
 * 并在模型调用前后执行 {@code BEFORE_MODEL}、{@code AFTER_MODEL}、
 * {@code ON_MODEL_STREAM}、{@code ON_MODEL_ERROR} 等拦截点。</p>
 */
public interface ModelGateway {

    /** 同步模型调用，额外携带循环上下文以执行拦截链。 */
    ModelResponse call(ModelCallRequest request, LoopContext context);

    /** 流式模型调用，额外携带循环上下文以执行拦截链。 */
    Stream<StreamChunk> stream(ModelCallRequest request, LoopContext context);
}
