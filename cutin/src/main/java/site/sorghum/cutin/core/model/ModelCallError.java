package site.sorghum.cutin.core.model;

/**
 * {@code ON_MODEL_ERROR} 拦截点的载荷，携带模型调用失败的错误信息。
 */
public record ModelCallError(
    String message,
    Throwable cause
) {
}
