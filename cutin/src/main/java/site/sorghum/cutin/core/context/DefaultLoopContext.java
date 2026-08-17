package site.sorghum.cutin.core.context;

import site.sorghum.cutin.core.model.ModelGateway;
import site.sorghum.cutin.core.state.LoopSnapshot;
import site.sorghum.cutin.core.tool.ToolRegistry;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link LoopContext} 的默认线程安全实现。
 *
 * <p>消息、变量、产物分别使用并发容器；用量通过原子引用更新；
 * 每次状态变更都会调用 {@link #touch()} 递增版本号，便于快照与重入。
 * 对外暴露的消息、变量、产物均是不可变视图。</p>
 */
public final class DefaultLoopContext implements LoopContext {

    /** 循环唯一标识。 */
    private final String id;
    /** 线程安全的消息列表。 */
    private final List<Message> messages = new CopyOnWriteArrayList<>();
    /** 线程安全的变量表。 */
    private final Map<String, Object> variables = new ConcurrentHashMap<>();
    /** 线程安全的产物表。 */
    private final Map<String, Object> artifacts = new ConcurrentHashMap<>();
    /** 当前累计用量。 */
    private final AtomicReference<Usage> usage = new AtomicReference<>(Usage.ZERO);
    /** 状态版本号，每次变更自增。 */
    private final AtomicLong stateVersion = new AtomicLong();
    /** 预算约束。 */
    private final Budget budget;
    /** 模型网关。 */
    private final ModelGateway models;
    /** 工具注册表。 */
    private final ToolRegistry tools;
    /** 当前循环的工作目录，工具相对路径的基准。 */
    private final Path workingDirectory;

    /**
     * 创建上下文并填充初始数据。
     *
     * @param initialMessages 初始消息列表，可为 null
     * @param initialVariables 初始变量，可为 null
     */
    public DefaultLoopContext(
        String id,
        List<Message> initialMessages,
        Map<String, Object> initialVariables,
        Budget budget,
        ModelGateway models,
        ToolRegistry tools
    ) {
        this(id, initialMessages, initialVariables, budget, models, tools, null);
    }

    /**
     * 创建上下文并填充初始数据。
     *
     * @param workingDirectory 工作目录，可为 null
     */
    public DefaultLoopContext(
        String id,
        List<Message> initialMessages,
        Map<String, Object> initialVariables,
        Budget budget,
        ModelGateway models,
        ToolRegistry tools,
        Path workingDirectory
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.messages.addAll(initialMessages == null ? List.of() : initialMessages);
        if (initialVariables != null) {
            this.variables.putAll(initialVariables);
        }
        this.budget = Objects.requireNonNull(budget, "budget");
        this.models = Objects.requireNonNull(models, "models");
        this.tools = Objects.requireNonNull(tools, "tools");
        this.workingDirectory = workingDirectory == null
            ? null
            : workingDirectory.toAbsolutePath().normalize();
    }

    /** 从快照恢复上下文，并还原状态版本号。 */
    public DefaultLoopContext(LoopSnapshot snapshot, ModelGateway models, ToolRegistry tools) {
        this(
            snapshot.loopId(),
            snapshot.messages(),
            snapshot.variables(),
            snapshot.budget(),
            models,
            tools,
            snapshot.workingDirectory()
        );
        this.stateVersion.set(snapshot.stateVersion());
    }

    /** {@inheritDoc} */
    @Override
    public String id() {
        return id;
    }

    /** {@inheritDoc} */
    @Override
    public long stateVersion() {
        return stateVersion.get();
    }

    /** {@inheritDoc} */
    @Override
    public List<Message> messages() {
        return Collections.unmodifiableList(messages);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> variables() {
        return Collections.unmodifiableMap(variables);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> artifacts() {
        return Collections.unmodifiableMap(artifacts);
    }

    /** {@inheritDoc} */
    @Override
    public Usage usage() {
        return usage.get();
    }

    /** {@inheritDoc} */
    @Override
    public Budget budget() {
        return budget;
    }

    /** {@inheritDoc} */
    @Override
    public ModelGateway models() {
        return models;
    }

    /** {@inheritDoc} */
    @Override
    public ToolRegistry tools() {
        return tools;
    }

    /** {@inheritDoc} */
    @Override
    public Path workingDirectory() {
        return workingDirectory;
    }

    /** 生成包含全部可变状态与预算副本的不可变快照。 */
    @Override
    public LoopSnapshot snapshot() {
        return new LoopSnapshot(
            id,
            stateVersion.get(),
            null,
            List.copyOf(messages),
            Map.copyOf(variables),
            Map.copyOf(artifacts),
            usage.get(),
            budget.copy(),
            workingDirectory
        );
    }

    /** {@inheritDoc} */
    public void appendMessage(Message message) {
        messages.add(message);
        touch();
    }

    /** {@inheritDoc} */
    @Override
    public void replaceMessages(List<Message> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
        touch();
    }

    /** {@inheritDoc} */
    public void putVariable(String key, Object value) {
        variables.put(key, value);
        touch();
    }

    /** {@inheritDoc} */
    public void putArtifact(String name, Object value) {
        artifacts.put(name, value);
        touch();
    }

    /** {@inheritDoc} */
    public void addUsage(Usage delta) {
        usage.updateAndGet(current -> current.add(delta));
        budget.spend(delta);
        touch();
    }

    /** 用变量覆盖表合并当前变量表，不覆盖产物。 */
    public void applyOverrides(Map<String, Object> overrides) {
        applyOverrides(overrides, Map.of());
    }

    /** 合并变量与产物覆盖表，通常用于重入时注入新数据。 */
    public void applyOverrides(Map<String, Object> overrides, Map<String, Object> artifactOverrides) {
        if (overrides != null) {
            variables.putAll(overrides);
        }
        if (artifactOverrides != null) {
            artifacts.putAll(artifactOverrides);
        }
        touch();
    }

    /** 状态发生变化时递增版本号。 */
    private void touch() {
        stateVersion.incrementAndGet();
    }
}
