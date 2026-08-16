package site.sorghum.cutin.core.loop;

import site.sorghum.cutin.core.context.LoopContext;

/**
 * 单个循环节点的执行步骤，接收上下文并返回流转结果。
 */
@FunctionalInterface
public interface Step {

    /** 执行本步骤并决定下一步如何流转。 */
    StepResult execute(LoopContext context);
}
