package site.sorghum.agent4j.bin.workflow;

import lombok.extern.slf4j.Slf4j;
import org.noear.snack4.ONode;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JsonlWorkflowStore — JSONL 格式的工作流持久化实现。
 * <p>
 * 存储路径：workspace/{hash}/workflows/{sessionId}.jsonl
 * 每个会话一个文件，单行 JSON。
 * 使用与 {@code JsonlGoalStore} 一致的手动序列化模式。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
public class JsonlWorkflowStore implements WorkflowStore {

    private final Path workflowsDir;

    public JsonlWorkflowStore(Path workspaceDir) {
        this.workflowsDir = workspaceDir.resolve("workflows");
    }

    private static String sanitize(String name) {
        if (name == null) return "unknown";
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    @Override
    public void save(Workflow workflow) throws IOException {
        Files.createDirectories(workflowsDir);
        Path file = workflowsDir.resolve(sanitize(workflow.getSessionId()) + ".jsonl");
        String json = serializeWorkflow(workflow);
        Files.writeString(file, json + "\n", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        log.debug("[workflow] 已保存工作流 {} -> {}", workflow.getId(), file);
    }

    @Override
    public Workflow findBySession(String sessionId) throws IOException {
        Path file = workflowsDir.resolve(sanitize(sessionId) + ".jsonl");
        try {
            String json = Files.readString(file).trim();
            if (json.isEmpty()) return null;
            return deserializeWorkflow(json);
        } catch (NoSuchFileException e) {
            return null;
        }
    }

    @Override
    public List<Workflow> findActiveByWorkspace(String workspaceHash) throws IOException {
        List<Workflow> active = new ArrayList<>();
        if (!Files.isDirectory(workflowsDir)) return active;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(workflowsDir, "*.jsonl")) {
            for (Path file : ds) {
                try {
                    String json = Files.readString(file).trim();
                    if (json.isEmpty()) continue;
                    Workflow workflow = deserializeWorkflow(json);
                    // 如果传入了 workspaceHash，校验匹配
                    if (workspaceHash != null && !workspaceHash.isEmpty()
                            && !workspaceHash.equals(workflow.getWorkspaceHash())) {
                        continue;
                    }
                    if (workflow.getStatus() == WorkflowStatus.ACTIVE
                            || workflow.getStatus() == WorkflowStatus.PAUSED) {
                        active.add(workflow);
                    }
                } catch (Exception e) {
                    log.warn("[workflow] 读取工作流文件失败: {} - {}", file, e.getMessage());
                }
            }
        }
        return active;
    }

    @Override
    public boolean delete(String sessionId) throws IOException {
        Path file = workflowsDir.resolve(sanitize(sessionId) + ".jsonl");
        return Files.deleteIfExists(file);
    }

    // ========== 手动序列化/反序列化（与 JsonlGoalStore 一致） ==========

    /**
     * 将 Workflow 序列化为 JSON 字符串。
     */
    static String serializeWorkflow(Workflow workflow) {
        ONode node = ONode.ofJson("{}");
        node.set("id", workflow.getId());
        node.set("sessionId", workflow.getSessionId());
        node.set("workspaceHash", workflow.getWorkspaceHash());
        node.set("title", workflow.getTitle());
        node.set("description", workflow.getDescription());
        node.set("status", workflow.getStatus() != null ? workflow.getStatus().name() : null);
        node.set("maxRetries", workflow.getMaxRetries());
        // 时间戳使用 epoch millis
        if (workflow.getCreatedAt() != null) {
            node.set("createdAt", workflow.getCreatedAt().toEpochMilli());
        }
        if (workflow.getUpdatedAt() != null) {
            node.set("updatedAt", workflow.getUpdatedAt().toEpochMilli());
        }
        if (workflow.getCompletedAt() != null) {
            node.set("completedAt", workflow.getCompletedAt().toEpochMilli());
        }
        // 节点列表
        if (workflow.getNodes() != null) {
            ONode nodesArr = node.getOrNew("nodes").asArray();
            for (WorkflowNode n : workflow.getNodes()) {
                ONode nodeObj = nodesArr.addNew();
                nodeObj.set("id", n.getId());
                nodeObj.set("description", n.getDescription());
                nodeObj.set("type", n.getType() != null ? n.getType().name() : null);
                nodeObj.set("status", n.getStatus() != null ? n.getStatus().name() : null);
                nodeObj.set("retryCount", n.getRetryCount());
                if (n.getLastError() != null) {
                    nodeObj.set("lastError", n.getLastError());
                }
                if (n.getResult() != null) {
                    nodeObj.set("result", n.getResult());
                }
                if (n.getCompletedAt() != null) {
                    nodeObj.set("completedAt", n.getCompletedAt().toEpochMilli());
                }
                if (n.getCondition() != null) {
                    nodeObj.set("condition", n.getCondition());
                }
                if (n.getParallelBranches() != null && !n.getParallelBranches().isEmpty()) {
                    ONode branchesArr = nodeObj.getOrNew("parallelBranches").asArray();
                    for (String branch : n.getParallelBranches()) {
                        branchesArr.addNew().fill(branch);
                    }
                }
                if (n.getApprovalPrompt() != null) {
                    nodeObj.set("approvalPrompt", n.getApprovalPrompt());
                }
                if (n.getApprovalResult() != null) {
                    nodeObj.set("approvalResult", n.getApprovalResult());
                }
                if (n.getSubWorkflowId() != null) {
                    nodeObj.set("subWorkflowId", n.getSubWorkflowId());
                }
            }
        }
        // 边列表
        if (workflow.getEdges() != null) {
            ONode edgesArr = node.getOrNew("edges").asArray();
            for (WorkflowEdge e : workflow.getEdges()) {
                ONode edgeObj = edgesArr.addNew();
                edgeObj.set("id", e.getId());
                edgeObj.set("from", e.getFrom());
                edgeObj.set("to", e.getTo());
                if (e.getCondition() != null) {
                    edgeObj.set("condition", e.getCondition());
                }
                edgeObj.set("type", e.getType() != null ? e.getType().name() : null);
            }
        }
        return node.toJson();
    }

    /**
     * 从 JSON 字符串反序列化为 Workflow。
     */
    static Workflow deserializeWorkflow(String json) {
        ONode node = ONode.ofJson(json);
        Workflow.WorkflowBuilder builder = Workflow.builder()
                .id(node.get("id").getString())
                .sessionId(node.get("sessionId").getString())
                .workspaceHash(node.get("workspaceHash").getString())
                .title(node.get("title").getString())
                .description(node.get("description").getString())
                .status(parseWorkflowStatus(node.get("status").getString()))
                .maxRetries(node.get("maxRetries").getInt());

        // 时间戳
        if (!node.get("createdAt").isNull()) {
            builder.createdAt(Instant.ofEpochMilli(node.get("createdAt").getLong()));
        }
        if (!node.get("updatedAt").isNull()) {
            builder.updatedAt(Instant.ofEpochMilli(node.get("updatedAt").getLong()));
        }
        if (!node.get("completedAt").isNull()) {
            builder.completedAt(Instant.ofEpochMilli(node.get("completedAt").getLong()));
        }

        // 节点列表
        List<WorkflowNode> nodes = new ArrayList<>();
        ONode nodesNode = node.get("nodes");
        if (nodesNode.isArray()) {
            for (ONode n : nodesNode.getArray()) {
                nodes.add(deserializeNode(n));
            }
        }
        builder.nodes(nodes);

        // 边列表
        List<WorkflowEdge> edges = new ArrayList<>();
        ONode edgesNode = node.get("edges");
        if (edgesNode.isArray()) {
            for (ONode e : edgesNode.getArray()) {
                edges.add(deserializeEdge(e));
            }
        }
        builder.edges(edges);

        return builder.build();
    }

    /**
     * 从 ONode 反序列化为 WorkflowNode。
     */
    private static WorkflowNode deserializeNode(ONode node) {
        WorkflowNode.WorkflowNodeBuilder builder = WorkflowNode.builder()
                .id(node.get("id").getString())
                .description(node.get("description").getString())
                .type(parseNodeType(node.get("type").getString()))
                .status(parseNodeStatus(node.get("status").getString()))
                .retryCount(node.get("retryCount").getInt())
                .lastError(node.get("lastError").getString())
                .result(node.get("result").getString())
                .condition(node.get("condition").getString())
                .approvalPrompt(node.get("approvalPrompt").getString())
                .approvalResult(node.get("approvalResult").getString())
                .subWorkflowId(node.get("subWorkflowId").getString());

        if (!node.get("completedAt").isNull()) {
            builder.completedAt(Instant.ofEpochMilli(node.get("completedAt").getLong()));
        }

        // 并行分支
        List<String> branches = new ArrayList<>();
        ONode branchesNode = node.get("parallelBranches");
        if (branchesNode.isArray()) {
            for (ONode b : branchesNode.getArray()) {
                branches.add(b.getString());
            }
        }
        builder.parallelBranches(branches);

        return builder.build();
    }

    /**
     * 从 ONode 反序列化为 WorkflowEdge。
     */
    private static WorkflowEdge deserializeEdge(ONode node) {
        return WorkflowEdge.builder()
                .id(node.get("id").getString())
                .from(node.get("from").getString())
                .to(node.get("to").getString())
                .condition(node.get("condition").getString())
                .type(parseEdgeType(node.get("type").getString()))
                .build();
    }

    private static WorkflowStatus parseWorkflowStatus(String name) {
        if (name == null) return WorkflowStatus.DRAFT;
        try {
            return WorkflowStatus.valueOf(name);
        } catch (IllegalArgumentException e) {
            return WorkflowStatus.DRAFT;
        }
    }

    private static NodeType parseNodeType(String name) {
        if (name == null) return NodeType.ACTION;
        try {
            return NodeType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return NodeType.ACTION;
        }
    }

    private static NodeStatus parseNodeStatus(String name) {
        if (name == null) return NodeStatus.PENDING;
        try {
            return NodeStatus.valueOf(name);
        } catch (IllegalArgumentException e) {
            return NodeStatus.PENDING;
        }
    }

    private static EdgeType parseEdgeType(String name) {
        if (name == null) return EdgeType.NORMAL;
        try {
            return EdgeType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return EdgeType.NORMAL;
        }
    }
}