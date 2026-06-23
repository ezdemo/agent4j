package site.sorghum.agent4j.bin.workflow.executor;

import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.bin.workflow.NodeType;
import site.sorghum.agent4j.bin.workflow.Workflow;
import site.sorghum.agent4j.bin.workflow.WorkflowNode;

import java.util.List;
import java.util.StringJoiner;

/**
 * ParallelNodeExecutor — 并行分支提示词生成器。
 * <p>
 * 先通过 ask_choice 让用户选择执行方式（子代理并发 / 直接串行），
 * 然后按用户选择的方式执行所有分支，最后调用 workflow_mark_node 报告结果。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class ParallelNodeExecutor implements NodeExecutor {

    @Override
    public NodeType supportedType() {
        return NodeType.PARALLEL;
    }

    @Override
    public String buildExecutionPrompt(Workflow workflow, WorkflowNode node) {
        List<String> branches = node.getParallelBranches();
        if (branches == null || branches.isEmpty()) {
            return "❌ PARALLEL 节点 " + node.getId() + " 没有定义并行分支 (parallelBranches 为空)，跳过。\n"
                    + "请调用 workflow_mark_node(nodeId=\"" + node.getId() + "\", result=\"无分支，自动跳过\")";
        }

        // 收集上下文
        String context = ActionNodeExecutor.collectPredecessorResults(workflow, node);

        // 构建分支详情
        StringJoiner branchJoiner = new StringJoiner("\n");
        for (String branchId : branches) {
            WorkflowNode branchNode = workflow.findNode(branchId);
            if (branchNode != null) {
                branchJoiner.add("- **" + branchId + "**: " + branchNode.getDescription());
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("请执行并行工作流节点：\n\n");
        sb.append("## 并行分支 (").append(branches.size()).append(" 个)\n");
        sb.append(branchJoiner).append("\n\n");

        if (!context.isBlank()) {
            sb.append("## 上下文（前序步骤结果）\n").append(context).append("\n\n");
        }

        sb.append("## 第一步：选择执行方式\n");
        sb.append("请先调用 `ask_choice` 工具询问用户选择执行方式：\n");
        sb.append("- question: \"并行节点 ").append(node.getId()).append(" 有 ").append(branches.size());
        sb.append(" 个分支，选择执行方式\"\n");
        sb.append("- options: [\n");
        sb.append("    {\"title\": \"子代理并发执行\", \"summary\": \"每个分支创建独立子代理，真正并发。隔离性好，token 消耗较大\"},\n");
        sb.append("    {\"title\": \"直接串行执行\", \"summary\": \"你依次执行每个分支任务，不用子代理。简单省 token，但不是真正并发\"}\n");
        sb.append("  ]\n\n");

        sb.append("## 第二步：按选择执行\n\n");
        sb.append("如果用户选择「子代理并发执行」：\n");
        sb.append("- 为每个分支调用 `task` 工具创建子代理\n");
        for (String branchId : branches) {
            WorkflowNode branchNode = workflow.findNode(branchId);
            if (branchNode != null) {
                sb.append("  - 分支 ").append(branchId).append(": \"").append(branchNode.getDescription()).append("\"\n");
            }
        }
        sb.append("\n如果用户选择「直接串行执行」：\n");
        sb.append("- 你依次执行每个分支的任务，直接完成（不用 task 工具）\n");
        sb.append("- 按顺序：").append(String.join(" → ", branches)).append("\n");

        sb.append("\n## 第三步：报告结果\n");
        sb.append("所有分支完成后，调用 `workflow_mark_node` 工具：\n");
        sb.append("- nodeId: `").append(node.getId()).append("`\n");
        sb.append("- result: 所有分支的执行结果汇总（每个分支一行）\n");

        return sb.toString();
    }
}
