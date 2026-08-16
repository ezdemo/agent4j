package site.sorghum.cutin.core.loop;

import site.sorghum.cutin.core.context.DefaultLoopContext;
import site.sorghum.cutin.core.event.LoopEvent;
import site.sorghum.cutin.core.state.LoopSnapshot;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link LoopHandle} 的默认实现。
 *
 * <p>每次执行都通过 {@link CompletableFuture} 异步进行；句柄维护最新快照、
 * 取消标记与当前 Future，支持恢复、重入与取消。</p>
 */
public final class DefaultLoopHandle implements LoopHandle {

    /** 循环唯一标识。 */
    private final String id;
    /** 所属引擎。 */
    private final DefaultLoopEngine engine;
    /** 正在执行的程序。 */
    private final LoopProgram program;
    /** 最新快照。 */
    private final AtomicReference<LoopSnapshot> latest = new AtomicReference<>();
    /** 是否已取消。 */
    private final AtomicBoolean cancelled = new AtomicBoolean();
    /** 当前执行结果 Future。 */
    private volatile CompletableFuture<LoopResult> currentFuture;

    /** 包内构造：由引擎创建句柄并绑定程序。 */
    DefaultLoopHandle(String id, DefaultLoopEngine engine, LoopProgram program) {
        this.id = id;
        this.engine = engine;
        this.program = program;
        this.currentFuture = new CompletableFuture<>();
    }

    /** 启动循环：以异步任务执行引擎主循环。 */
    void start(DefaultLoopContext context) {
        CompletableFuture<LoopResult> future = CompletableFuture.supplyAsync(
            () -> engine.execute(program, context, this, null, false)
        );
        currentFuture = future;
    }

    /** {@inheritDoc} */
    @Override
    public String id() {
        return id;
    }

    /** {@inheritDoc} */
    @Override
    public LoopSnapshot snapshot() {
        return latest.get();
    }

    /** {@inheritDoc} */
    @Override
    public CompletableFuture<LoopResult> result() {
        return currentFuture;
    }

    /**
     * 从快照恢复：要求上一轮已结束，从快照记录的节点继续执行。
     */
    @Override
    public CompletableFuture<LoopResult> resume(LoopSnapshot snapshot) {
        if (!currentFuture.isDone()) {
            throw new IllegalStateException("loop is still running");
        }
        DefaultLoopContext context = engine.restore(snapshot);
        String nodeId = snapshot.nodeId() == null ? program.startNodeId() : snapshot.nodeId();
        CompletableFuture<LoopResult> future = CompletableFuture.supplyAsync(
            () -> engine.execute(program, context, this, nodeId, true)
        );
        currentFuture = future;
        return future;
    }

    /**
     * 重入：要求上一轮已结束，从指定状态版本恢复上下文并回到指定节点。
     */
    @Override
    public CompletableFuture<LoopResult> reenter(ReentryRequest request) {
        if (!currentFuture.isDone()) {
            throw new IllegalStateException("loop is still running");
        }
        if (!program.contains(request.nodeId())) {
            throw new IllegalArgumentException("unknown reentry node: " + request.nodeId());
        }
        DefaultLoopContext context = engine.restore(id, request.baseStateVersion());
        context.applyOverrides(request.overrides(), request.artifactOverrides());
        CompletableFuture<LoopResult> future = CompletableFuture.supplyAsync(
            () -> engine.execute(program, context, this, request.nodeId(), true)
        );
        currentFuture = future;
        return future;
    }

    /** 取消当前循环：置取消标记、发布事件并尽快结束 Future。 */
    @Override
    public CompletableFuture<LoopResult> cancel(CancelReason reason) {
        cancelled.set(true);
        engine.events().emit(new LoopEvent(
            "ON_CANCEL",
            id,
            null,
            Map.of("reason", reason == null ? "cancelled" : reason.reason())
        ));
        if (currentFuture.isDone()) {
            return currentFuture;
        }
        currentFuture.complete(new LoopResult(id, LoopResult.Status.CANCELLED, null, reason.reason(), latest.get()));
        return currentFuture;
    }

    /** 包内更新最新快照。 */
    void updateSnapshot(LoopSnapshot snapshot) {
        latest.set(snapshot);
    }

    /** 包内查询是否已取消。 */
    boolean isCancelled() {
        return cancelled.get();
    }
}
