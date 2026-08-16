package site.sorghum.loopra.bin.model;

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

    public static boolean isInvalidRequestError(String error) {
        return error != null && error.toLowerCase(Locale.ROOT).contains("invalid_request_error");
    }

    /**
      * 该错误是否应由 cutin 重试策略重试。
     * <p>
      * 上下文超限与无效请求被有意排除：上下文超限有独立的折叠恢复，
      * 而无效请求重试只会重复同样的失败。
     * </p>
     */
    public static boolean isTransientModelError(String error) {
        if (error == null || error.isBlank()) {
            return false;
        }
        if (isContextLengthExceeded(error) || isInvalidRequestError(error)) {
            return false;
        }
        String value = error.toLowerCase(Locale.ROOT);
        return value.contains("http 429")
            || value.contains("http 500")
            || value.contains("http 501")
            || value.contains("http 502")
            || value.contains("http 503")
            || value.contains("http 504")
            || value.contains("http 5")
            || value.contains("rate limit")
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
            || value.contains("broken pipe")
            || value.contains("no route")
            || value.contains("socket")
            || value.contains("stream error")
            || value.contains("sse");
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
