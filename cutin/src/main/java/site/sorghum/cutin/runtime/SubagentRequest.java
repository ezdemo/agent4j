package site.sorghum.cutin.runtime;

import site.sorghum.cutin.core.context.Budget;
import site.sorghum.cutin.core.loop.LoopProgram;

import java.util.Map;

/**
 * 子代理请求：任务描述、执行程序、初始变量与预算。
 */
public record SubagentRequest(
    String task,
    LoopProgram program,
    Map<String, Object> variables,
    Budget budget
) {

    /** 记录构造校验：变量不可变拷贝，预算为空时使用不限预算。 */
    public SubagentRequest {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        budget = budget == null ? Budget.unlimited() : budget;
    }
}
