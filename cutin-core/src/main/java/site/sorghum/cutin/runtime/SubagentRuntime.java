package site.sorghum.cutin.runtime;

import site.sorghum.cutin.core.loop.DefaultLoopEngine;
import site.sorghum.cutin.core.loop.LoopHandle;
import site.sorghum.cutin.core.loop.LoopResult;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 子代理运行时：在同一个引擎上启动独立的循环作为子任务。
 */
public final class SubagentRuntime {

    /** 底层循环引擎。 */
    private final DefaultLoopEngine engine;

    /** 绑定引擎创建子代理运行时。 */
    public SubagentRuntime(DefaultLoopEngine engine) {
        this.engine = engine;
    }

    /** 启动一个子代理，任务描述会作为 {@code task} 变量注入上下文。 */
    public LoopHandle spawn(SubagentRequest request) {
        Map<String, Object> variables = new HashMap<>(request.variables());
        variables.put("task", request.task());
        return engine.run(request.program(), variables);
    }

    /** 等待子代理完成并返回结果。 */
    public CompletableFuture<LoopResult> await(LoopHandle handle) {
        return handle.result();
    }
}
