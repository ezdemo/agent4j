package site.sorghum.cutin.core.tool;

/** 工具不存在时抛出的异常。 */
public final class ToolNotFoundException extends RuntimeException {

    /** 创建异常并携带说明信息。 */
    public ToolNotFoundException(String message) {
        super(message);
    }
}
