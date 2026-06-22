package site.sorghum.agent4j.bin.workflow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.bin.agent.core.Agent4jAgent;
import site.sorghum.agent4j.bin.agent.model.UserMessage;
import site.sorghum.agent4j.bin.command.ChatCommandContext;
import site.sorghum.agent4j.tool.LogLevel;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * WorkflowEngine — 工作流执行引擎。
 * <p>
 * 负责工作流创建（LLM 拆解节点）、状态查询、暂停/恢复、节点执行。
 * 支持有向无环图（DAG）结构，节点间可有多依赖关系。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowEngine {

    /**
     * 创建新工作流：调用 LLM 拆解节点。
     */
    public Workflow createWorkflow(String description, ChatCommandContext ctx) {
        Agent4jAgent agent = ctx.getAgent();
        String sessionId = agent.getSessionStore() != null ? agent.getSessionStore().currentName() : null;
        if (sessionId == null) {
            throw new IllegalStateException("会话未初始化，无法创建工作流");
        }
        String workspaceHash = agent.getWorkspaceManager().getCurrentWorkspaceHash();
        if (workspaceHash == null) {
            throw new IllegalStateException("工作区未初始化，请先初始化工作区");
        }

        String breakdownPrompt = """
                请将以下目标拆解为 3-8 个具体的、可执行的步骤，每个步骤应是一个独立可验证的任务。
                请以 JSON 数组格式返回，每个元素包含 "description" 字段。
                不要包含任何其他文本，只返回 JSON 数组。
                
                目标：%s
                """.formatted(description);

        String llmResponse;
        try {
            llmResponse = agent.chat(UserMessage.of(breakdownPrompt));
        } catch (Exception e) {
            log.error("[workflow] LLM 拆解失败", e);
            llmResponse = """
                    [{"description": "%s"}]
                    """.formatted(description);
        }

        List<WorkflowNode> nodes = parseNodes(llmResponse, description);
        List<WorkflowEdge> edges = createLinearEdges(nodes);

        Workflow workflow = Workflow.builder()
                .id(UUID.randomUUID().toString().substring(0, 8))
                .sessionId(sessionId)
                .workspaceHash(workspaceHash)
                .title(description.length() > 50 ? description.substring(0, 50) + "..." : description)
                .description(description)
                .status(WorkflowStatus.DRAFT)
                .maxRetries(3)
                .nodes(nodes)
                .edges(edges)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return workflow;
    }

    /**
     * 持久化工作流并输出通知。
     */
    public void activateWorkflow(Workflow workflow, ChatCommandContext ctx) {
        try {
            WorkflowStore store = ctx.getAgent().getWorkspaceManager().getWorkflowStore();
            store.save(workflow);
            log.info("[workflow] 工作流已保存: {} - {}", workflow.getId(), workflow.getTitle());

            String nodesText = IntStream.range(0, workflow.getNodes().size())
                    .mapToObj(i -> {
                        WorkflowNode n = workflow.getNodes().get(i);
                        return "  [" + n.getId() + "] " + n.getDescription() + " (" + n.getType() + ", " + n.getStatus() + ")";
                    })
                    .collect(Collectors.joining("\n"));

            ctx.getAgent().getOutput().onLog(LogLevel.INFO,
                    "🔄 工作流已创建: " + workflow.getTitle() + "\n节点:\n" + nodesText);
        } catch (Exception e) {
            log.error("[workflow] 激活工作流失败: {}", e.getMessage());
            ctx.getAgent().getOutput().onLog(LogLevel.ERROR,
                    "❌ 工作流创建失败: " + e.getMessage());
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
        // 返回第一个就绪节点
        return readyNodes.get(0);
    }

    /**
     * 执行工作流。
     */
    public void executeWorkflow(Workflow workflow, ChatCommandContext ctx) {
        try {
            workflow.setStatus(WorkflowStatus.ACTIVE);
            workflow.setUpdatedAt(Instant.now());
            ctx.getAgent().getWorkspaceManager().getWorkflowStore().save(workflow);

            // 生成工作流结构文本
            String structureText = generateStructureText(workflow);

            String prompt = """
                    当前会话已设定工作流：「%s」
                    
                    工作流结构：
                    %s
                    
                    请按照工作流结构执行节点。
                    每完成一个节点，**必须**调用 workflow_mark_node 工具通知系统，参数传入节点ID。
                    如果有节点失败，系统会自动重试（最多 %d 次）。
                    全部完成后总结汇报。
                    """
                    .formatted(workflow.getTitle(), structureText, workflow.getMaxRetries());

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
    private String generateStructureText(Workflow workflow) {
        StringBuilder sb = new StringBuilder();
        sb.append("节点列表:\n");
        for (WorkflowNode node : workflow.getNodes()) {
            sb.append("  ").append(node.getId()).append(". ").append(node.getDescription())
                    .append(" (").append(node.getType()).append(")\n");
        }
        sb.append("\n依赖关系:\n");
        for (WorkflowEdge edge : workflow.getEdges()) {
            sb.append("  ").append(edge.getFrom()).append(" -> ").append(edge.getTo());
            if (edge.getCondition() != null) {
                sb.append(" [条件: ").append(edge.getCondition()).append("]");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private List<WorkflowNode> parseNodes(String llmResponse, String fallbackDescription) {
        try {
            String json = llmResponse;
            int startIdx = json.indexOf('[');
            int endIdx = json.lastIndexOf(']');
            if (startIdx >= 0 && endIdx > startIdx) {
                json = json.substring(startIdx, endIdx + 1);
            }

            org.noear.snack4.ONode node = org.noear.snack4.ONode.ofJson(json);
            if (node.isArray()) {
                List<WorkflowNode> nodes = new ArrayList<>();
                // 添加开始节点
                nodes.add(WorkflowNode.builder()
                        .id("start")
                        .description("开始")
                        .type(NodeType.START)
                        .status(NodeStatus.DONE)
                        .retryCount(0)
                        .build());

                for (int i = 0; i < node.size(); i++) {
                    String desc = node.get(i).get("description").getString();
                    if (desc != null && !desc.isEmpty()) {
                        nodes.add(WorkflowNode.builder()
                                .id("n" + (i + 1))
                                .description(desc)
                                .type(NodeType.ACTION)
                                .status(NodeStatus.PENDING)
                                .retryCount(0)
                                .build());
                    }
                }

                // 添加结束节点
                nodes.add(WorkflowNode.builder()
                        .id("end")
                        .description("结束")
                        .type(NodeType.END)
                        .status(NodeStatus.PENDING)
                        .retryCount(0)
                        .build());

                if (nodes.size() > 2) return nodes;
            }
        } catch (Exception e) {
            log.warn("[workflow] 解析 LLM 节点失败，使用 fallback", e);
        }

        // Fallback: 创建简单的线性工作流
        List<WorkflowNode> nodes = new ArrayList<>();
        nodes.add(WorkflowNode.builder()
                .id("start")
                .description("开始")
                .type(NodeType.START)
                .status(NodeStatus.DONE)
                .retryCount(0)
                .build());
        nodes.add(WorkflowNode.builder()
                .id("n1")
                .description(fallbackDescription)
                .type(NodeType.ACTION)
                .status(NodeStatus.PENDING)
                .retryCount(0)
                .build());
        nodes.add(WorkflowNode.builder()
                .id("end")
                .description("结束")
                .type(NodeType.END)
                .status(NodeStatus.PENDING)
                .retryCount(0)
                .build());
        return nodes;
    }

    private List<WorkflowEdge> createLinearEdges(List<WorkflowNode> nodes) {
        List<WorkflowEdge> edges = new ArrayList<>();
        for (int i = 0; i < nodes.size() - 1; i++) {
            edges.add(WorkflowEdge.builder()
                    .id("e" + (i + 1))
                    .from(nodes.get(i).getId())
                    .to(nodes.get(i + 1).getId())
                    .type(EdgeType.NORMAL)
                    .build());
        }
        return edges;
    }
}