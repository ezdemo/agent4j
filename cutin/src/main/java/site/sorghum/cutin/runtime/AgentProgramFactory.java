package site.sorghum.cutin.runtime;

import site.sorghum.cutin.core.loop.LoopProgram;

/**
 * Agent 程序工厂函数式接口：根据会话生成本次执行要运行的循环程序。
 */
@FunctionalInterface
public interface AgentProgramFactory {

    /** 为指定会话创建循环程序。 */
    LoopProgram create(AgentSession session);
}
