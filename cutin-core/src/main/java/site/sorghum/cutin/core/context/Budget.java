package site.sorghum.cutin.core.context;

import java.util.Objects;

/**
 * 循环预算，约束一轮执行可以消耗的步骤、token、费用、时长、重入次数与子代理数量。
 *
 * <p>预算在构造时绑定 {@link BudgetLimit}，之后所有消耗都必须先通过对应的
 * {@code canXxx()} 检查，超出限制会抛出 {@link IllegalStateException}。
 * 支持快照复制，以便循环暂停、恢复或重入时恢复同一套预算状态。</p>
 */
public final class Budget {

    /** 预算上限定义。 */
    private final BudgetLimit limit;
    /** 预算开始计时的时间戳（毫秒），用于判断是否超时。 */
    private final long startedAtMillis;
    /** 已执行的步骤数。 */
    private int steps;
    /** 已消耗的 token 总数。 */
    private long tokens;
    /** 已消耗的费用（以微分为单位）。 */
    private long costMicros;
    /** 已发生的重入次数。 */
    private int reentries;
    /** 已创建的子代理数量。 */
    private int subagents;

    /** 使用指定限制创建预算，开始时间取当前系统时间。 */
    public Budget(BudgetLimit limit) {
        this(limit, System.currentTimeMillis());
    }

    /** 内部构造：允许从快照恢复时保留原始开始时间。 */
    private Budget(BudgetLimit limit, long startedAtMillis) {
        this.limit = Objects.requireNonNull(limit, "limit");
        this.startedAtMillis = startedAtMillis;
    }

    /** 创建一个不受任何限制的预算。 */
    public static Budget unlimited() {
        return new Budget(BudgetLimit.unlimited());
    }

    /** 返回本预算的上限定义。 */
    public BudgetLimit limit() {
        return limit;
    }

    /** 是否还能继续执行一个步骤（未超步数且未超时）。 */
    public boolean canStep() {
        return steps < limit.maxSteps() && !expired();
    }

    /** 记录执行了一个步骤；若已超限则抛出异常。 */
    public void step() {
        if (!canStep()) {
            throw new IllegalStateException("step budget exceeded");
        }
        steps++;
    }

    /** 判断当前累计 token 与费用加上本次用量后是否仍在预算内。 */
    public boolean canSpend(Usage usage) {
        return tokens + usage.totalTokens() <= limit.maxTokens()
            && costMicros + usage.costMicros() <= limit.maxCostMicros()
            && !expired();
    }

    /** 消耗一次用量；超出预算时抛出异常。 */
    public void spend(Usage usage) {
        if (!canSpend(usage)) {
            throw new IllegalStateException("token or cost budget exceeded");
        }
        tokens += usage.totalTokens();
        costMicros += usage.costMicros();
    }

    /** 是否还能执行一次重入。 */
    public boolean canReenter() {
        return reentries < limit.maxReentries() && !expired();
    }

    /** 记录一次重入；超出限制时抛出异常。 */
    public void reenter() {
        if (!canReenter()) {
            throw new IllegalStateException("reentry budget exceeded");
        }
        reentries++;
    }

    /** 是否还能创建子代理。 */
    public boolean canSpawnSubagent() {
        return subagents < limit.maxSubagents() && !expired();
    }

    /** 记录创建了一个子代理；超出限制时抛出异常。 */
    public void spawnSubagent() {
        if (!canSpawnSubagent()) {
            throw new IllegalStateException("subagent budget exceeded");
        }
        subagents++;
    }

    /** 是否已经超过最大执行时长（maxDurationMillis 为 0 表示不限时）。 */
    public boolean expired() {
        return limit.maxDurationMillis() > 0
            && System.currentTimeMillis() - startedAtMillis > limit.maxDurationMillis();
    }

    /** 复制一份当前预算，保留相同的限制与开始时间，避免共享可变状态。 */
    public Budget copy() {
        Budget copy = new Budget(limit, startedAtMillis);
        copy.steps = steps;
        copy.tokens = tokens;
        copy.costMicros = costMicros;
        copy.reentries = reentries;
        copy.subagents = subagents;
        return copy;
    }

    /** 生成不可变快照，用于持久化或比较。 */
    public BudgetSnapshot snapshot() {
        return new BudgetSnapshot(
            limit,
            startedAtMillis,
            steps,
            tokens,
            costMicros,
            reentries,
            subagents
        );
    }

    /** 从快照恢复预算，开始时间与各项计数都还原为快照时的值。 */
    public static Budget fromSnapshot(BudgetSnapshot snapshot) {
        Budget budget = new Budget(snapshot.limit(), snapshot.startedAtMillis());
        budget.steps = snapshot.steps();
        budget.tokens = snapshot.tokens();
        budget.costMicros = snapshot.costMicros();
        budget.reentries = snapshot.reentries();
        budget.subagents = snapshot.subagents();
        return budget;
    }

    /** 基于快照比较两个预算是否完全一致。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Budget budget)) {
            return false;
        }
        return snapshot().equals(budget.snapshot());
    }

    /** 基于快照生成哈希值。 */
    @Override
    public int hashCode() {
        return Objects.hash(snapshot());
    }

    /** 预算的不可变快照，用于持久化与比较。 */
    public record BudgetSnapshot(
        BudgetLimit limit,
        long startedAtMillis,
        int steps,
        long tokens,
        long costMicros,
        int reentries,
        int subagents
    ) {
    }
}
