package site.sorghum.cutin.core.loop;

import site.sorghum.cutin.core.context.LoopContext;

import java.util.Map;

/**
 * 循环引擎：把 {@link LoopProgram} 图按节点顺序推进执行。
 *
 * <p>引擎本身不包含业务策略；所有生命周期扩展点都通过
 * {@link LoopInterceptor} 暴露给插件。执行返回异步的 {@link LoopHandle}。</p>
 */
public interface LoopEngine {

    /** 使用显式初始上下文启动一个循环。 */
    LoopHandle run(LoopProgram program, LoopContext initialContext);

    /** 使用输入变量启动一个循环，引擎会创建新的上下文。 */
    LoopHandle run(LoopProgram program, Map<String, Object> input);
}
