package site.sorghum.agent4j.bin.workflow.executor;

import site.sorghum.agent4j.bin.workflow.NodeType;
import site.sorghum.agent4j.bin.workflow.Workflow;
import site.sorghum.agent4j.bin.workflow.WorkflowNode;

/**
 * NodeExecutor — 节点执行提示词生成器。
 * <p>
 * 不同 {@link NodeType} 注册不同的执行器实现。
 * 执行器不直接创建子代理，而是生成 prompt 引导 LLM 使用 task 等工具执行节点。
 * </p>
 *
 * @author Sorghum
 */
public interface NodeExecutor {

    /**
     * 为节点生成执行提示词，引导 LLM 使用合适的工具完成节点任务。
     *
     * @param workflow 所属工作流
     * @param node     待执行节点
     * @return 引导 LLM 执行的提示词（null 表示节点可自动完成，无需 LLM 介入）
     */
    String buildExecutionPrompt(Workflow workflow, WorkflowNode node);

    /**
     * 该执行器支持的节点类型。
     */
    NodeType supportedType();
}
