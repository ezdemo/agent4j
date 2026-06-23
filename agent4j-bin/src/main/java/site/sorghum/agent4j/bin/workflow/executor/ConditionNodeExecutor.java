package site.sorghum.agent4j.bin.workflow.executor;

import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.bin.workflow.NodeType;
import site.sorghum.agent4j.bin.workflow.Workflow;
import site.sorghum.agent4j.bin.workflow.WorkflowEdge;
import site.sorghum.agent4j.bin.workflow.WorkflowNode;

import java.util.List;
import java.util.StringJoiner;

/**
 * ConditionNodeExecutor — 条件分支提示词生成器。
 * <p>
 * 引导 LLM 根据条件描述和前驱结果评估条件，
 * 然后调用 workflow_mark_node 传入 conditionResult（选中的后继节点ID）。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class ConditionNodeExecutor implements NodeExecutor {

    @Override
    public NodeType supportedType() {
        return NodeType.CONDITION;
    }

    @Override
    public String buildExecutionPrompt(Workflow workflow, WorkflowNode node) {
        String context = ActionNodeExecutor.collectPredecessorResults(workflow, node);

        // 收集后继分支信息
        List<String> successors = workflow.getSuccessorIds(node.getId());
        StringJoiner optionsJoiner = new StringJoiner("\n");
        for (String succId : successors) {
            WorkflowNode succ = workflow.findNode(succId);
            String label = "";
            WorkflowEdge edge = workflow.findEdge(node.getId(), succId);
            if (edge != null && edge.getLabel() != null && !edge.getLabel().isBlank()) {
                label = " — " + edge.getLabel();
            }
            if (succ != null) {
                optionsJoiner.add("- **" + succId + "**: " + succ.getDescription() + label);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("请评估工作流条件分支：\n\n");
        sb.append("## 条件\n");
        sb.append(node.getCondition() != null ? node.getCondition() : node.getDescription()).append("\n\n");

        if (!context.isBlank()) {
            sb.append("## 上下文（前序步骤结果）\n").append(context).append("\n\n");
        }

        sb.append("## 可选分支\n").append(optionsJoiner).append("\n\n");

        sb.append("## 指令\n");
        sb.append("1. 根据条件描述和上下文分析，选择最合适的分支\n");
        sb.append("2. 调用 `workflow_mark_node` 工具，传入参数：\n");
        sb.append("   - nodeId: `").append(node.getId()).append("`\n");
        sb.append("   - result: 你的判断理由\n");
        sb.append("   - conditionResult: 选中的分支节点ID（如 `").append(successors.isEmpty() ? "?" : successors.get(0)).append("`）\n");
        sb.append("\n⚠️ conditionResult 必须是上面可选分支中的某个节点ID，系统会自动跳过其他分支。\n");

        return sb.toString();
    }
}
