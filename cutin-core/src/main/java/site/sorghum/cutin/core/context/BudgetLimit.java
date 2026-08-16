package site.sorghum.cutin.core.context;

/**
 * 预算上限的不可变定义。
 *
 * <p>各上限取值：
 * {@code maxSteps} 最大步骤数、{@code maxTokens} 最大 token 数、
 * {@code maxCostMicros} 最大费用（微分）、{@code maxDurationMillis} 最大执行时长
 * （0 表示不限时）、{@code maxReentries} 最大重入次数、{@code maxSubagents} 最大子代理数。</p>
 */
public record BudgetLimit(
    int maxSteps,
    long maxTokens,
    long maxCostMicros,
    long maxDurationMillis,
    int maxReentries,
    int maxSubagents
) {

    /** 一个完全不受限制的预算上限。 */
    public static BudgetLimit unlimited() {
        return new BudgetLimit(Integer.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    /** 只限制步骤数、其余维度不限的预算上限，常用于简单循环。 */
    public static BudgetLimit steps(int maxSteps) {
        return new BudgetLimit(maxSteps, Long.MAX_VALUE, Long.MAX_VALUE, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }
}
