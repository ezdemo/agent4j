package site.sorghum.cutin.runtime;

import site.sorghum.cutin.core.context.Message;
import site.sorghum.cutin.core.loop.LoopResult;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Agent 会话：维护跨轮对话的消息历史与执行结果历史。
 *
 * <p>会话消息只读暴露；每轮正常完成后会用最终快照的消息替换旧历史，
 * 从而保证后续轮次基于完整上下文继续。</p>
 */
public final class AgentSession {

    /** 会话唯一标识。 */
    private final String id;
    /** 所属运行时。 */
    private final AgentRuntime runtime;
    /** 跨轮对话消息。 */
    private final List<Message> messages = new CopyOnWriteArrayList<>();
    /** 历史执行结果。 */
    private final List<LoopResult> history = new CopyOnWriteArrayList<>();

    /** 包内构造：由运行时创建会话。 */
    AgentSession(String id, AgentRuntime runtime) {
        this.id = Objects.requireNonNull(id, "id");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    /** 会话唯一标识。 */
    public String id() {
        return id;
    }

    /** 只读的对话消息历史。 */
    public List<Message> messages() {
        return List.copyOf(messages);
    }

    /** 只读的历史执行结果。 */
    public List<LoopResult> history() {
        return List.copyOf(history);
    }

    /** 在当前会话上执行一轮输入。 */
    public CompletableFuture<LoopResult> run(String input) {
        return runtime.run(this, input);
    }

    /** 把一轮正常完成的结果同步回会话：刷新消息并追加历史。 */
    void append(LoopResult result) {
        if (result.finalSnapshot() != null) {
            messages.clear();
            messages.addAll(result.finalSnapshot().messages());
        }
        history.add(result);
    }
}
