package site.sorghum.cutin.core.tool;

/**
 * 工具执行结果，统一表达成功与失败两种形态。
 */
public record ToolResult(
    String toolCallId,
    boolean ok,
    Object content,
    String error
) {

    /** 构造一个成功结果。 */
    public static ToolResult success(String toolCallId, Object content) {
        return new ToolResult(toolCallId, true, content, null);
    }

    /** 构造一个失败结果。 */
    public static ToolResult failure(String toolCallId, String error) {
        return new ToolResult(toolCallId, false, null, error);
    }
}
