package site.sorghum.cutin.core.tool;

import java.util.Map;

/**
 * 一次工具调用，由模型发起，包含调用 id、目标工具 id 与参数。
 *
 * <p>{@code idempotencyKey} 用于幂等去重，默认与调用 id 相同；
 * 参数不可变，防止工具执行过程中被意外修改。</p>
 */
public record ToolCall(
    String id,
    String toolId,
    Map<String, Object> arguments,
    String idempotencyKey
) {

    /** 记录构造校验：对参数做不可变拷贝。 */
    public ToolCall {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
