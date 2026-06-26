package site.sorghum.agent4j.bin.workflow.executor;

import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.bin.workflow.NodeType;
import site.sorghum.agent4j.bin.workflow.Workflow;
import site.sorghum.agent4j.bin.workflow.WorkflowNode;

/**
 * LoopNodeExecutor — 循环控制节点提示词生成器。
 * <p>
 * 引导 LLM 评估循环条件：继续迭代还是退出循环。
 * 每次迭代完成后，系统重置循环体节点，LOOP 节点重新被评估。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class LoopNodeExecutor implements NodeExecutor {

    @Override
    public NodeType supportedType() {
        return NodeType.LOOP;
    }

    @Override
    public String buildExecutionPrompt(Workflow workflow, WorkflowNode node) {
        String context = ActionNodeExecutor.collectPredecessorResults(workflow, node);

        StringBuilder sb = new StringBuilder();
        sb.append("请评估工作流循环节点：\n\n");
        sb.append("## 循环描述\n");
        sb.append("**").append(node.getId()).append("**: ").append(node.getDescription()).append("\n\n");

        sb.append("## 迭代状态\n");
        sb.append("- 当前第 **").append(node.getIterationCount() + 1).append("** 轮迭代\n");
        sb.append("- 最大迭代次数：**").append(node.getMaxIterations()).append("** 轮\n");

        if (node.getBreakCondition() != null && !node.getBreakCondition().isBlank()) {
            sb.append("- 退出条件：").append(node.getBreakCondition()).append("\n");
        }

        sb.append("\n");

        if (!context.isBlank()) {
            sb.append("## 上下文（前序步骤结果）\n").append(context).append("\n\n");
        }

        // 检查是否达到最大迭代次数
        if (node.getIterationCount() >= node.getMaxIterations()) {
            sb.append("⚠️ **已达到最大迭代次数（").append(node.getMaxIterations()).append(" 轮），必须退出循环。**\n\n");
            sb.append("## 指令\n");
            sb.append("调用 `workflow_mark_node` 工具，传入参数：\n");
            sb.append("- nodeId: `").append(node.getId()).append("`\n");
            sb.append("- result: \"已达最大迭代次数，退出循环\"\n");
            sb.append("- loopResult: \"break\"\n");
        } else {
            sb.append("## 指令\n");
            sb.append("根据上下文和退出条件，判断是否应继续循环：\n\n");
            sb.append("调用 `workflow_mark_node` 工具，传入参数：\n");
            sb.append("- nodeId: `").append(node.getId()).append("`\n");
            sb.append("- result: 你的判断理由\n");
            sb.append("- loopResult: `\"continue\"`（继续下一轮循环）或 `\"break\"`（退出循环，继续后续节点）\n");
        }

        return sb.toString();
    }
}
