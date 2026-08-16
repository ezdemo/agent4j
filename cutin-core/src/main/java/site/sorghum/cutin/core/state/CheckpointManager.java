package site.sorghum.cutin.core.state;

import site.sorghum.cutin.core.context.LoopContext;
import site.sorghum.cutin.core.event.EventBus;
import site.sorghum.cutin.core.event.LoopEvent;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 检查点管理器：在每个节点执行前把当前上下文写成快照。
 *
 * <p>检查点用于崩溃恢复、重入定位与结果诊断；每次写入还会发布
 * {@code CHECKPOINT} 事件。</p>
 */
public final class CheckpointManager {

    /** 底层状态存储。 */
    private final StateStore stateStore;
    /** 事件总线。 */
    private final EventBus eventBus;

    /** 绑定状态存储与事件总线。 */
    public CheckpointManager(StateStore stateStore, EventBus eventBus) {
        this.stateStore = stateStore;
        this.eventBus = eventBus;
    }

    /** 写入一次检查点并返回快照，同时发布 CHECKPOINT 事件。 */
    public LoopSnapshot checkpoint(String loopId, LoopContext context, String nodeId) {
        LoopSnapshot base = context.snapshot();
        LoopSnapshot snapshot = new LoopSnapshot(
            loopId,
            base.stateVersion(),
            nodeId,
            base.messages(),
            base.variables(),
            base.artifacts(),
            base.usage(),
            base.budget()
        );
        stateStore.save(snapshot);
        eventBus.emit(new LoopEvent(
            "CHECKPOINT",
            loopId,
            nodeId,
            Map.of("stateVersion", snapshot.stateVersion())
        ));
        return snapshot;
    }

    /** 读取最新检查点。 */
    public Optional<LoopSnapshot> latest(String loopId) {
        return stateStore.latest(loopId);
    }

    /** 读取指定版本的检查点。 */
    public Optional<LoopSnapshot> version(String loopId, long stateVersion) {
        return stateStore.version(loopId, stateVersion);
    }

    /** 读取全部检查点历史。 */
    public List<LoopSnapshot> history(String loopId) {
        return stateStore.history(loopId);
    }
}
