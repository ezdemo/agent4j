package site.sorghum.cutin.core.model;

import org.noear.snack4.ONode;
import site.sorghum.cutin.integrations.model.ProviderInterceptor;

import java.util.stream.Stream;

/**
 * 模型 Provider SPI：负责对接一种具体的模型协议（OpenAI、Anthropic 等）。
 *
 * <p>Provider 需要声明自己支持的模型 id、是否支持流式与工具调用，
 * 并实现同步调用与非阻塞流式调用。</p>
 */
public interface ModelProvider {

    /** Provider 唯一标识。 */
    String id();

    /** 同步调用模型并返回完整响应。 */
    ModelResponse call(ModelCallRequest request);

    /** 流式调用模型，按到达顺序产出增量块。 */
    Stream<StreamChunk> stream(ModelCallRequest request);

    /** 返回该 Provider 的能力声明。 */
    ModelCapabilities capabilities();

    /**
     * 构建原始协议请求体（未经拦截器处理）。
     *
     * <p>body 是各协议私有的 JSON 结构（如 Chat Completions、Messages、
     * Responses），由各 Provider 自行实现。同步与流式调用都会先经过
     * {@link #_buildBody(ModelCallRequest, boolean)} 跑完拦截器链后再发送，
     * 一般不需要直接调用本方法。</p>
     */
    ONode buildBody(ModelCallRequest request, boolean stream);

    /**
     * 构建经 Provider 拦截器处理后的最终请求体。
     *
     * <p>默认实现：先调用 {@link #buildBody(ModelCallRequest, boolean)}
     * 得到原始体，再按注册顺序执行全局拦截器链，返回最终请求体。同步与
     * 流式调用都会先经过该方法再发送。</p>
     */
    default ONode _buildBody(ModelCallRequest request, boolean stream) {
        String modelId = request.modelId();
        if (modelId == null || modelId.isBlank()) {
            // 请求未指定模型时回退到 Provider 声明的默认模型
            modelId = capabilities().models().stream().findFirst().orElse(null);
        }
        return ProviderInterceptor.run(this, modelId, request, buildBody(request, stream));
    }
}
