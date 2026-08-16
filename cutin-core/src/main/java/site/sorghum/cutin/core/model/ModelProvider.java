package site.sorghum.cutin.core.model;

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
}
