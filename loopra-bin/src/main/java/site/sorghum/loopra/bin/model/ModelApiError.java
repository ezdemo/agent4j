package site.sorghum.loopra.bin.model;

import java.util.Locale;

/**
 * Detects context-window overflow errors returned by OpenAI-compatible gateways.
 */
public final class ModelApiError {

    private static final String CONTEXT_OVERFLOW_MARKER = "[context_length_exceeded] ";

    private ModelApiError() {
    }

    /**
     * Recognizes the standard error code and common gateway message variants.
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
     * Adds a stable marker while preserving the provider's original error payload.
     */
    public static String annotate(String error) {
        if (!isContextLengthExceeded(error) || error.startsWith(CONTEXT_OVERFLOW_MARKER)) {
            return error;
        }
        return CONTEXT_OVERFLOW_MARKER + error;
    }
}
