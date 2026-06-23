package site.sorghum.agent4j.bin.workflow.executor;

import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.bin.workflow.NodeType;
import site.sorghum.agent4j.bin.workflow.Workflow;
import site.sorghum.agent4j.bin.workflow.WorkflowNode;

import java.util.StringJoiner;

/**
 * ActionNodeExecutor — 普通动作节点提示词生成器。
 * <p>
 * 直接执行任务，仅在任务复杂时才建议使用 task 子代理。
 * 完成后调用 workflow_mark_node 报告结果。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class ActionNodeExecutor implements NodeExecutor {

    @Override
    public NodeType supportedType() {
        return NodeType.ACTION;
    }

    @Override
    public String buildExecutionPrompt(Workflow workflow, WorkflowNode node) {
        String context = collectPredecessorResults(workflow, node);

        StringBuilder sb = new StringBuilder();
        sb.append("请执行工作流节点任务：\n\n");
        sb.append("## 任务\n");
        sb.append("**").append(node.getId()).append("**: ").append(node.getDescription()).append("\n\n");

        if (!context.isBlank()) {
            sb.append("## 前序步骤结果\n").append(context).append("\n\n");
        }

        sb.append("## 指令\n");
        sb.append("直接执行上述任务。\n");
        sb.append("如果任务足够简单（如创建文件、运行命令），直接做。\n");
        sb.append("如果任务复杂（如需要深度分析、多步推理），可以使用 `task` 工具创建子代理。\n\n");
        sb.append("完成后调用 `workflow_mark_node` 工具：\n");
        sb.append("- nodeId: `").append(node.getId()).append("`\n");
        sb.append("- result: 执行结果摘要（不超过200字）\n");

        return sb.toString();
    }

    /**
     * 收集所有前驱节点的执行结果，作为上下文传递。
     */
    static String collectPredecessorResults(Workflow workflow, WorkflowNode node) {
        StringJoiner joiner = new StringJoiner("\n");
        for (String predId : workflow.getPredecessorIds(node.getId())) {
            WorkflowNode pred = workflow.findNode(predId);
            if (pred != null && pred.getResult() != null && !pred.getResult().isBlank()) {
                joiner.add("- [" + pred.getId() + "] " + pred.getDescription() + ": " + pred.getResult());
            }
        }
        return joiner.toString();
    }
}
