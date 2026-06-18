package site.sorghum.agent4j.web.model.meta;

/**
 * 模型的上下文窗口和输出长度限制。
 * <p>
 * JSON 结构示例：
 * <pre>
 * {
 *   "context": 256000,
 *   "output": 64000
 * }
 * </pre>
 * </p>
 *
 * @param context 上下文窗口大小（token 数量）
 * @param output  最大输出长度（token 数量）
 */
public record Limit(
        long context,
        long output
) {
    /**
     * 获取上下文窗口大小（以千 token 为单位）。
     *
     * @return 上下文窗口大小（K tokens）
     */
    public double contextInThousands() {
        return context / 1000.0;
    }

    /**
     * 获取最大输出长度（以千 token 为单位）。
     *
     * @return 最大输出长度（K tokens）
     */
    public double outputInThousands() {
        return output / 1000.0;
    }

    /**
     * 检查上下文窗口是否超过指定 token 数量。
     *
     * @param tokens token 数量
     * @return true 表示超过指定数量
     */
    public boolean contextExceeds(long tokens) {
        return context > tokens;
    }

    /**
     * 检查输出长度是否超过指定 token 数量。
     *
     * @param tokens token 数量
     * @return true 表示超过指定数量
     */
    public boolean outputExceeds(long tokens) {
        return output > tokens;
    }
}