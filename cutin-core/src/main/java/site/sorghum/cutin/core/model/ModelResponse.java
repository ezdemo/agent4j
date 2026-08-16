package site.sorghum.cutin.core.model;

import site.sorghum.cutin.core.context.Message;
import site.sorghum.cutin.core.context.Usage;

/**
 * 一次完整模型响应：最终消息、用量与是否结束标志。
 */
public record ModelResponse(
    Message message,
    Usage usage,
    boolean finished
) {

    /** 快捷构造一个已结束的模型响应。 */
    public static ModelResponse of(Message message, Usage usage) {
        return new ModelResponse(message, usage, true);
    }
}
