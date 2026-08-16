package site.sorghum.cutin.integrations.model;

/** 模型 Provider 调用失败（HTTP 错误、流中断等）时抛出的异常。 */
public final class ModelProviderException extends RuntimeException {

    /** 创建异常并携带错误信息。 */
    public ModelProviderException(String message) {
        super(message);
    }
}
