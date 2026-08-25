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
     * 构建协议请求体（已通过 Provider 拦截器处理后的最终请求体）。
     *
     * <p>同步与流式调用都会先经过该方法再发送。body 是各协议私有的 JSON
     * 结构（如 Chat Completions、Messages、Responses），由各 Provider
     * 自行实现。</p>
     */
    ONode buildBody(ModelCallRequest request, boolean stream);

    /**
     * 构建协议请求体（已通过 Provider 拦截器处理后的最终请求体）。
     *
     * <p>同步与流式调用都会先经过该方法再发送。body 是各协议私有的 JSON
     * 结构（如 Chat Completions、Messages、Responses），由各 Provider
     * 自行实现。</p>
     */
    default ONode _buildBody(ModelCallRequest request, boolean stream){
        return ProviderInterceptor.run(ProviderInterceptor.INTERCEPT_LIST, this, id(), request, buildBody(request, stream));
    }
}
