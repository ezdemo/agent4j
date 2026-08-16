package site.sorghum.cutin.core.loop;

import site.sorghum.cutin.core.state.LoopSnapshot;

import java.util.concurrent.CompletableFuture;

/**
 * 已启动循环的控制句柄，用于读取快照、等待结果、恢复、重入与取消。
 */
public interface LoopHandle {

    /** 循环唯一标识。 */
    String id();

    /** 当前最新的状态快照。 */
    LoopSnapshot snapshot();

    /** 本次执行的结果 Future。 */
    CompletableFuture<LoopResult> result();

    /** 从指定快照恢复执行。 */
    CompletableFuture<LoopResult> resume(LoopSnapshot snapshot);

    /** 基于指定状态版本重入到某个节点。 */
    CompletableFuture<LoopResult> reenter(ReentryRequest request);

    /** 取消当前循环并给出取消原因。 */
    CompletableFuture<LoopResult> cancel(CancelReason reason);
}
