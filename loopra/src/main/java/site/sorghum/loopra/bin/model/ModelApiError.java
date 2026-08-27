package site.sorghum.loopra.bin.model;

import org.noear.snack4.ONode;

import java.io.IOException;
import java.util.Locale;

/**
  * 识别 OpenAI 兼容网关返回的上下文窗口超限错误。
 */
public final class ModelApiError {

    private static final String CONTEXT_OVERFLOW_MARKER = "[context_length_exceeded] ";

    private ModelApiError() {
    }

    /**
      * 识别标准错误码与常见网关消息变体。
     */
    public static boolean isContextLengthExceeded(String error) {
        if (error == null || error.isBlank()) return false;
        String value = error.toLowerCase(Locale.ROOT);
        return value.contains("context_length_exceeded")
                || value.contains("input_too_long")
                || value.contains("context_window_exceeded")
                || value.contains("maximum context length")
                || value.contains("exceeds the context window")
                || value.contains("exceed the context window")
                || value.contains("input exceeds the context window")
                || value.contains("上下文长度")
                || value.contains("上下文超限")
                || value.contains("上下文窗口");
    }

    /**
      * 错误文本中是否出现 {@code invalid_request_error} 类型标记。
     * <p>
      * 注意这是全文本子串匹配：OpenAI 兼容网关可能在 429 配额错误的报文里
      * 嵌套一个 {@code type: invalid_request_error} 的内层错误，因此该判断
      * 不能单独决定"不可重试"——调用方应先确认 HTTP 状态（见
      * {@link #isTransientModelError(String)}）。
     * </p>
     */
    public static boolean isInvalidRequestError(String error) {
        return error != null && error.toLowerCase(Locale.ROOT).contains("invalid_request_error");
    }

    /**
      * 该错误是否应由 cutin 重试策略重试。
     * <p>
      * 判定优先级：上下文超限 > HTTP 429/5xx 状态 > 无效请求排除 > 其余瞬时信号。
      * HTTP 429/5xx 是权威信号：OpenAI 兼容网关常把配额/限流错误的内层
      * {@code type} 标成 {@code invalid_request_error}（例如 {@code insufficient_quota}
      * 的 429），此时报文里虽含 {@code invalid_request_error} 子串，仍必须按瞬时错误
      * 重试；上下文超限有独立的折叠恢复，因此永远优先排除。
     * </p>
     */
    public static boolean isTransientModelError(String error) {
        if (error == null || error.isBlank()) {
            return false;
        }
        if (isContextLengthExceeded(error)) {
            return false;
        }
        String value = error.toLowerCase(Locale.ROOT);
        if (isTransientStatusCode(error) || hasHttpTransientStatusMarker(value)) {
            return true;
        }
        if (isInvalidRequestError(error)) {
            return false;
        }
        return value.contains("rate limit")
            || value.contains("rate_limit")
            || value.contains("overloaded")
            || value.contains("overloaded_error")
            || value.contains("temporarily unavailable")
            || value.contains("service unavailable")
            || value.contains("bad gateway")
            || value.contains("internal server error")
            || value.contains("upstream")
            || value.contains("timeout")
            || value.contains("timed out")
            || value.contains("connection")
            || value.contains("stream closed")
            || value.contains("connection closed")
            || value.contains("channel closed")
            || value.contains("java.io.ioexception: closed")
            || value.contains("broken pipe")
            || value.contains("no route")
            || value.contains("socket")
            || value.contains("stream error")
            || value.contains("sse");
    }

    private static boolean isTransientStatusCode(String error) {
        try {
            ONode root = ONode.ofJson(error);
            ONode errorNode = root.get("error");
            ONode codeNode = errorNode == null ? root.get("code") : errorNode.get("code");
            if (codeNode == null || codeNode.isNull()) {
                return false;
            }
            int code = codeNode.getInt();
            return code == 429 || code >= 500 && code < 600;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /** 文本形式携带的 HTTP 429/5xx 状态标记（入参应为小写）。 */
    private static boolean hasHttpTransientStatusMarker(String value) {
        return value.contains("http 429")
            || value.contains("http 500")
            || value.contains("http 501")
            || value.contains("http 502")
            || value.contains("http 503")
            || value.contains("http 504")
            || value.contains("http 5");
    }

    /** 同时检查异常因果链，避免传输层只暴露简短的 closed 消息。 */
    public static boolean isTransientModelError(String error, Throwable cause) {
        if (isTransientModelError(error)) {
            return true;
        }
        for (Throwable current = cause; current != null; current = current.getCause()) {
            if (current instanceof IOException
                    && current.getMessage() != null
                    && current.getMessage().toLowerCase(Locale.ROOT).contains("closed")) {
                return true;
            }
        }
        return false;
    }

    /**
      * 在保留提供方原始错误载荷的同时追加稳定标记。
     */
    public static String annotate(String error) {
        if (!isContextLengthExceeded(error) || error.startsWith(CONTEXT_OVERFLOW_MARKER)) {
            return error;
        }
        return CONTEXT_OVERFLOW_MARKER + error;
    }
}
