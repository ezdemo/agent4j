package site.sorghum.agent4j.bin.workflow.executor;

import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.bin.workflow.NodeType;
import site.sorghum.agent4j.bin.workflow.Workflow;
import site.sorghum.agent4j.bin.workflow.WorkflowNode;

/**
 * HitlNodeExecutor — 人工审批提示词生成器。
 * <p>
 * 返回 null 表示该节点需要人工介入，不能自动执行。
 * 由 WorkflowEngine 将工作流设为 PAUSED，通知用户审批。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class HitlNodeExecutor implements NodeExecutor {

    @Override
    public NodeType supportedType() {
        return NodeType.HITL;
    }

    @Override
    public String buildExecutionPrompt(Workflow workflow, WorkflowNode node) {
        // HITL 不生成 prompt，返回 null 让引擎特殊处理
        return null;
    }
}
