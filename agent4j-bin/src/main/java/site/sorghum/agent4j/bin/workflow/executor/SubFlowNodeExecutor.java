package site.sorghum.agent4j.bin.workflow.executor;

import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.bin.workflow.NodeType;
import site.sorghum.agent4j.bin.workflow.Workflow;
import site.sorghum.agent4j.bin.workflow.WorkflowNode;

/**
 * SubFlowNodeExecutor — 子代理执行提示词生成器。
 * <p>
 * 引导 LLM 使用 task 工具创建深度推理子代理来完成复杂任务。
 * 与 ACTION 不同，SUBFLOW 的 prompt 强调"深度分析、多步工具调用"。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class SubFlowNodeExecutor implements NodeExecutor {

    @Override
    public NodeType supportedType() {
        return NodeType.SUBFLOW;
    }

    @Override
    public String buildExecutionPrompt(Workflow workflow, WorkflowNode node) {
        String context = ActionNodeExecutor.collectPredecessorResults(workflow, node);

        StringBuilder sb = new StringBuilder();
        sb.append("请执行工作流子代理任务（需要深度推理和多步工具调用）：\n\n");
        sb.append("## 任务\n");
        sb.append("**").append(node.getId()).append("**: ").append(node.getDescription()).append("\n\n");

        if (!context.isBlank()) {
            sb.append("## 前序步骤结果\n").append(context).append("\n\n");
        }

        sb.append("## 工作流信息\n");
        sb.append("- 工作流: ").append(workflow.getTitle()).append("\n");
        sb.append("- 当前节点: ").append(node.getId()).append("\n\n");

        sb.append("## 指令\n");
        sb.append("1. 使用 `task` 工具创建一个隔离子代理来完成上述任务\n");
        sb.append("   - arguments 中请详细描述任务要求，包括上下文信息\n");
        sb.append("   - 子代理可以使用所有可用工具（读写文件、运行命令、搜索代码等）\n");
        sb.append("2. 子代理完成后，调用 `workflow_mark_node` 工具，传入参数：\n");
        sb.append("   - nodeId: `").append(node.getId()).append("`\n");
        sb.append("   - result: 子代理的执行结果摘要\n");

        return sb.toString();
    }
}
