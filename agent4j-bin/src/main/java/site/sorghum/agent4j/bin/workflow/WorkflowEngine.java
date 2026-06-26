package site.sorghum.agent4j.bin.workflow;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Init;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.bin.command.ChatCommandContext;
import site.sorghum.agent4j.bin.workflow.executor.NodeExecutor;
import site.sorghum.agent4j.tool.LogLevel;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * WorkflowEngine — 工作流执行引擎。
 * <p>
 * 负责工作流状态查询、暂停/恢复、节点执行等操作。
 * 通过 {@link NodeExecutor} 为不同节点类型生成执行提示词，引导 LLM 使用 task 等工具完成节点。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
@Component
public class WorkflowEngine {

    /** 所有节点执行器（由 Solon 自动注入） */
    @Inject
    private List<NodeExecutor> executors;

    /** 节点执行器注册表（按 NodeType 索引） */
    private Map<NodeType, NodeExecutor> executorMap = Collections.emptyMap();

    /**
     * DI 注入完成后，按 supportedType 建立索引。
     */
    @Init
    public void init() {
        if (executors != null && !executors.isEmpty()) {
            this.executorMap = executors.stream()
                    .collect(Collectors.toMap(NodeExecutor::supportedType, e -> e));
            log.info("[workflow] 已注册 {} 个节点执行器: {}", executorMap.size(), executorMap.keySet());
        }
    }

    /**
     * 获取当前会话的工作流。
     */
    public Workflow getCurrentWorkflow(ChatCommandContext ctx) {
        try {
            String sessionId = ctx.getAgent().getSessionStore().currentName();
            if (sessionId == null) return null;
            WorkflowStore store = ctx.getAgent().getWorkspaceManager().getWorkflowStore();
            return store.findBySession(sessionId);
        } catch (Exception e) {
            log.warn("[workflow] 获取当前工作流失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 暂停工作流。
     */
    public void pause(Workflow workflow, ChatCommandContext ctx) {
        try {
            workflow.setStatus(WorkflowStatus.PAUSED);
            workflow.setUpdatedAt(Instant.now());
            ctx.getAgent().getWorkspaceManager().getWorkflowStore().save(workflow);
        } catch (Exception e) {
            log.warn("[workflow] 暂停工作流失败: {}", e.getMessage());
        }
    }

    /**
     * 恢复工作流。
     */
    public void resume(Workflow workflow, ChatCommandContext ctx) {
        try {
            workflow.setStatus(WorkflowStatus.ACTIVE);
            workflow.setUpdatedAt(Instant.now());
            ctx.getAgent().getWorkspaceManager().getWorkflowStore().save(workflow);
        } catch (Exception e) {
            log.warn("[workflow] 恢复工作流失败: {}", e.getMessage());
        }
    }

    /**
     * 标记某节点已完成。
     */
    public void markNodeDone(Workflow workflow, String nodeId, String result, ChatCommandContext ctx) {
        try {
            WorkflowNode node = workflow.findNode(nodeId);
            if (node == null) return;
            node.setStatus(NodeStatus.DONE);
            node.setResult(result);
            node.setCompletedAt(Instant.now());
            workflow.setUpdatedAt(Instant.now());

            // END 节点自动完成
            autoCompleteEndNodes(workflow);

            if (workflow.isAllDone()) {
                workflow.setStatus(WorkflowStatus.COMPLETED);
                workflow.setCompletedAt(Instant.now());
            }

            ctx.getAgent().getWorkspaceManager().getWorkflowStore().save(workflow);
        } catch (Exception e) {
            log.warn("[workflow] 标记节点完成失败: {}", e.getMessage());
        }
    }

    /**
     * 标记某节点失败。
     */
    public void markNodeFailed(Workflow workflow, String nodeId, String error, ChatCommandContext ctx) {
        try {
            WorkflowNode node = workflow.findNode(nodeId);
            if (node == null) return;
            node.setStatus(NodeStatus.FAILED);
            node.setLastError(error);
            node.setRetryCount(node.getRetryCount() + 1);
            workflow.setUpdatedAt(Instant.now());

            if (workflow.hasFailed()) {
                workflow.setStatus(WorkflowStatus.FAILED);
            }

            ctx.getAgent().getWorkspaceManager().getWorkflowStore().save(workflow);
        } catch (Exception e) {
            log.warn("[workflow] 标记节点失败失败: {}", e.getMessage());
        }
    }

    /**
     * 重试某节点。
     */
    public void retryNode(Workflow workflow, String nodeId, ChatCommandContext ctx) {
        try {
            WorkflowNode node = workflow.findNode(nodeId);
            if (node == null) return;
            node.setStatus(NodeStatus.PENDING);
            node.setRetryCount(0);
            node.setLastError(null);
            node.setCompletedAt(null);
            workflow.setUpdatedAt(Instant.now());
            ctx.getAgent().getWorkspaceManager().getWorkflowStore().save(workflow);
        } catch (Exception e) {
            log.warn("[workflow] 重试节点失败: {}", e.getMessage());
        }
    }

    /**
     * 跳过某节点。
     */
    public void skipNode(Workflow workflow, String nodeId, ChatCommandContext ctx) {
        try {
            WorkflowNode node = workflow.findNode(nodeId);
            if (node == null) return;
            node.setStatus(NodeStatus.SKIPPED);
            node.setCompletedAt(Instant.now());
            workflow.setUpdatedAt(Instant.now());

            // END 节点自动完成
            autoCompleteEndNodes(workflow);

            if (workflow.isAllDone()) {
                workflow.setStatus(WorkflowStatus.COMPLETED);
                workflow.setCompletedAt(Instant.now());
            }

            ctx.getAgent().getWorkspaceManager().getWorkflowStore().save(workflow);
        } catch (Exception e) {
            log.warn("[workflow] 跳过节点失败: {}", e.getMessage());
        }
    }

    /**
     * 添加节点。
     */
    public void addNode(Workflow workflow, String description, NodeType type, ChatCommandContext ctx) {
        try {
            String nodeId = workflow.generateNodeId();
            WorkflowNode node = WorkflowNode.builder()
                    .id(nodeId)
                    .description(description)
                    .type(type)
                    .status(NodeStatus.PENDING)
                    .retryCount(0)
                    .build();
            workflow.addNode(node);
            workflow.setUpdatedAt(Instant.now());
            ctx.getAgent().getWorkspaceManager().getWorkflowStore().save(workflow);
        } catch (Exception e) {
            log.warn("[workflow] 添加节点失败: {}", e.getMessage());
        }
    }

    /**
     * 移除节点。
     */
    public void removeNode(Workflow workflow, String nodeId, ChatCommandContext ctx) {
        try {
            workflow.removeNode(nodeId);
            workflow.setUpdatedAt(Instant.now());
            ctx.getAgent().getWorkspaceManager().getWorkflowStore().save(workflow);
        } catch (Exception e) {
            log.warn("[workflow] 移除节点失败: {}", e.getMessage());
        }
    }

    /**
     * 添加边。
     */
    public void addEdge(Workflow workflow, String from, String to, EdgeType type, String condition, ChatCommandContext ctx) {
        try {
            String edgeId = workflow.generateEdgeId();
            WorkflowEdge edge = WorkflowEdge.builder()
                    .id(edgeId)
                    .from(from)
                    .to(to)
                    .type(type)
                    .condition(condition)
                    .build();
            workflow.addEdge(edge);
            workflow.setUpdatedAt(Instant.now());
            ctx.getAgent().getWorkspaceManager().getWorkflowStore().save(workflow);
        } catch (Exception e) {
            log.warn("[workflow] 添加边失败: {}", e.getMessage());
        }
    }

    /**
     * 移除边。
     */
    public void removeEdge(Workflow workflow, String from, String to, ChatCommandContext ctx) {
        try {
            workflow.removeEdge(from, to);
            workflow.setUpdatedAt(Instant.now());
            ctx.getAgent().getWorkspaceManager().getWorkflowStore().save(workflow);
        } catch (Exception e) {
            log.warn("[workflow] 移除边失败: {}", e.getMessage());
        }
    }

    /**
     * 获取下一个要执行的节点。
     */
    public WorkflowNode getNextNode(Workflow workflow) {
        List<WorkflowNode> readyNodes = workflow.getReadyNodes();
        if (readyNodes.isEmpty()) {
            return null;
        }
        return readyNodes.get(0);
    }

    // ==================== 提示词驱动执行 ====================

    /**
     * 生成工作流执行提示词。
     * <p>
     * 根据当前就绪节点的类型，生成对应的执行提示词。
     * LLM 收到提示词后使用 task/workflow_mark_node 等工具完成节点。
     * </p>
     *
     * @param workflow 工作流
     * @return 执行提示词（null 表示没有就绪节点或工作流已完成）
     */
    public String buildExecutionPrompt(Workflow workflow) {
        // 工作流不在活跃状态
        if (workflow.getStatus() != WorkflowStatus.ACTIVE
                && workflow.getStatus() != WorkflowStatus.DRAFT) {
            if (workflow.getStatus() == WorkflowStatus.PAUSED) {
                return "⏸️ 工作流已暂停。使用 `/workflow approve <节点ID>` 或 `/workflow deny <节点ID>` 来审批。";
            }
            return null;
        }

        // 标记 START 节点完成
        for (WorkflowNode n : workflow.getNodes()) {
            if (n.getType() == NodeType.START && n.getStatus() != NodeStatus.DONE) {
                n.setStatus(NodeStatus.DONE);
                n.setCompletedAt(Instant.now());
            }
        }

        // 获取就绪节点
        List<WorkflowNode> readyNodes = workflow.getReadyNodes();
        if (readyNodes.isEmpty()) {
            // 检查是否全部完成
            if (workflow.isAllDone()) {
                return null; // 工作流已完成
            }
            return "⏸️ 工作流没有就绪节点。请检查是否有节点需要重试或跳过。";
        }

        // 优先处理 PARALLEL 节点（fork 整组分支）
        WorkflowNode targetNode = readyNodes.stream()
                .filter(n -> n.getType() == NodeType.PARALLEL)
                .findFirst()
                .orElse(readyNodes.get(0));

        // 查找执行器
        NodeExecutor executor = executorMap.get(targetNode.getType());

        if (executor == null) {
            // START/END 自动处理
            if (targetNode.getType() == NodeType.START) {
                targetNode.setStatus(NodeStatus.DONE);
                targetNode.setCompletedAt(Instant.now());
                return buildExecutionPrompt(workflow); // 递归获取下一个
            }
            if (targetNode.getType() == NodeType.END) {
                return null;
            }
            // 没有执行器的节点类型
            return "节点 " + targetNode.getId() + " (类型: " + targetNode.getType() + ") 没有自动执行器。\n"
                    + "请手动执行后调用 workflow_mark_node(nodeId=\"" + targetNode.getId() + "\")";
        }

        // HITL 节点：暂停工作流
        if (targetNode.getType() == NodeType.HITL) {
            return handleHitlNode(workflow, targetNode);
        }

        // 生成执行提示词
        String prompt = executor.buildExecutionPrompt(workflow, targetNode);

        // 标记节点为 RUNNING
        targetNode.setStatus(NodeStatus.RUNNING);

        // 包装工作流上下文
        StringBuilder sb = new StringBuilder();
        sb.append("🔄 **工作流「").append(workflow.getTitle()).append("」** — 进度: ")
                .append(workflow.progressText()).append("\n\n");
        sb.append(prompt);
        sb.append("\n\n---\n");
        sb.append("💡 完成后请立即调用 `workflow_mark_node` 工具报告结果，系统会自动推进到下一个节点。\n");
        sb.append("如果节点失败，调用时传入 result 参数说明失败原因。\n");

        return sb.toString();
    }

    /**
     * 处理 HITL 节点：暂停工作流，生成审批提示。
     */
    private String handleHitlNode(Workflow workflow, WorkflowNode node) {
        node.setStatus(NodeStatus.WAITING);
        workflow.setStatus(WorkflowStatus.PAUSED);
        workflow.setUpdatedAt(Instant.now());

        String approvalPrompt = node.getApprovalPrompt();
        if (approvalPrompt == null || approvalPrompt.isBlank()) {
            approvalPrompt = node.getDescription();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("⏸️ **工作流暂停 — 等待人工审批**\n\n");
        sb.append("节点: **").append(node.getId()).append("** — ").append(node.getDescription()).append("\n");
        sb.append("审批提示: ").append(approvalPrompt).append("\n\n");
        sb.append("请用户操作：\n");
        sb.append("- `/workflow approve ").append(node.getId()).append("` — 批准\n");
        sb.append("- `/workflow deny ").append(node.getId()).append("` — 拒绝\n");

        return sb.toString();
    }

    /**
     * 执行工作流（LLM 驱动模式，向后兼容）。
     * <p>
     * 生成工作流结构概览 + 当前就绪节点的执行提示词，注入到对话中引导 LLM 执行。
     * </p>
     */
    public void executeWorkflow(Workflow workflow, ChatCommandContext ctx) {
        try {
            workflow.setStatus(WorkflowStatus.ACTIVE);
            workflow.setUpdatedAt(Instant.now());
            ctx.getAgent().getWorkspaceManager().getWorkflowStore().save(workflow);

            String structureText = generateStructureText(workflow);
            String execPrompt = buildExecutionPrompt(workflow);

            String prompt = """
                    当前会话已设定工作流：「%s」

                    工作流结构：
                    %s

                    %s

                    每完成一个节点后，系统会自动推进到下一个节点。
                    """
                    .formatted(workflow.getTitle(), structureText,
                            execPrompt != null ? execPrompt : "工作流已完成。");

            ctx.getAgent().getOutput().onLog(LogLevel.INFO, prompt);
        } catch (Exception e) {
            log.error("[workflow] 执行工作流失败: {}", e.getMessage());
            ctx.getAgent().getOutput().onLog(LogLevel.ERROR,
                    "❌ 工作流执行失败: " + e.getMessage());
        }
    }

    /**
     * 生成工作流结构文本。
     */
    public String generateStructureText(Workflow workflow) {
        StringBuilder sb = new StringBuilder();
        sb.append("节点列表:\n");
        for (WorkflowNode node : workflow.getNodes()) {
            sb.append("  ").append(node.getId()).append(". ").append(node.getDescription())
                    .append(" (").append(node.getType()).append(")\n");
            if (node.getType() == NodeType.PARALLEL && node.getParallelBranches() != null) {
                sb.append("    并行分支: ").append(node.getParallelBranches()).append("\n");
            }
            if (node.getType() == NodeType.CONDITION && node.getCondition() != null) {
                sb.append("    条件: ").append(node.getCondition()).append("\n");
            }
            if (node.getType() == NodeType.LOOP) {
                sb.append("    循环体起始: ").append(node.getLoopTarget())
                        .append(", 最大迭代: ").append(node.getMaxIterations())
                        .append(", 退出条件: ").append(node.getBreakCondition()).append("\n");
            }
        }
        sb.append("\n依赖关系:\n");
        for (WorkflowEdge edge : workflow.getEdges()) {
            sb.append("  ").append(edge.getFrom()).append(" -> ").append(edge.getTo());
            sb.append(" [").append(edge.getType()).append("]");
            if (edge.getCondition() != null) {
                sb.append(" 条件: ").append(edge.getCondition());
            }
            if (edge.getLabel() != null) {
                sb.append(" 标签: ").append(edge.getLabel());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 自动完成 END 节点：如果所有前驱都已完成。
     */
    private void autoCompleteEndNodes(Workflow workflow) {
        for (WorkflowNode wn : workflow.getNodes()) {
            if (wn.getType() == NodeType.END && wn.getStatus() != NodeStatus.DONE) {
                List<String> preds = workflow.getPredecessorIds(wn.getId());
                if (!preds.isEmpty() && preds.stream().allMatch(predId -> {
                    WorkflowNode pred = workflow.findNode(predId);
                    return pred != null && (pred.getStatus() == NodeStatus.DONE
                            || pred.getStatus() == NodeStatus.SKIPPED);
                })) {
                    wn.setStatus(NodeStatus.DONE);
                    wn.setCompletedAt(Instant.now());
                }
            }
        }
    }
}
