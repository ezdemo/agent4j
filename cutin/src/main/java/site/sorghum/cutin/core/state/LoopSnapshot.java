package site.sorghum.cutin.core.state;

import site.sorghum.cutin.core.context.Budget;
import site.sorghum.cutin.core.context.Message;
import site.sorghum.cutin.core.context.Usage;

import java.util.List;
import java.util.Map;

/**
 * 循环状态的不可变快照：循环 id、版本、节点位置、消息、变量、产物、用量与预算。
 *
 * <p>快照是恢复、重入与持久化的基础；所有集合在构造时做不可变拷贝，
 * 预算也会复制一份，避免共享可变对象。</p>
 */
public record LoopSnapshot(
    String loopId,
    long stateVersion,
    String nodeId,
    List<Message> messages,
    Map<String, Object> variables,
    Map<String, Object> artifacts,
    Usage usage,
    Budget budget
) {

    /** 记录构造校验：对消息、变量、产物与预算做防御性拷贝。 */
    public LoopSnapshot {
        messages = List.copyOf(messages);
        variables = Map.copyOf(variables);
        artifacts = Map.copyOf(artifacts);
        budget = budget == null ? null : budget.copy();
    }
}
