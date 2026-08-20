package site.sorghum.cutin.core.model;

import site.sorghum.cutin.core.context.Message;
import site.sorghum.cutin.core.context.Usage;

import java.util.Map;

/**
 * 一次完整模型响应：最终消息、用量与是否结束标志。
 *
 * @param raw provider 返回的原始响应体 JSON 字符串（同步为完整响应体，
 *             流式为全部 SSE 数据行按到达顺序拼接），未启用时为空字符串
 * @param request 实际发送到 Provider 的原始请求体与请求头
 */
public record ModelResponse(
    Message message,
    Usage usage,
    boolean finished,
    String raw,
    ModelHttpExchange request
) {

    public ModelResponse {
        raw = raw == null ? "" : raw;
    }

    public ModelResponse(Message message, Usage usage, boolean finished) {
        this(message, usage, finished, "", null);
    }

    public ModelResponse(Message message, Usage usage, boolean finished, String raw) {
        this(message, usage, finished, raw, null);
    }

    /** 快捷构造一个已结束、无原始响应体的模型响应。 */
    public static ModelResponse of(Message message, Usage usage) {
        return new ModelResponse(message, usage, true, "", null);
    }

    public String requestBody() {
        return request == null || request.body() == null ? "" : request.body();
    }

    public Map<String, String> requestHeaders() {
        return request == null || request.headers() == null ? Map.of() : request.headers();
    }

    public String requestEndpoint() {
        return request == null || request.endpoint() == null ? "" : request.endpoint();
    }
}
