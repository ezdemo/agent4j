package site.sorghum.agent4j.bin.builtin;

import lombok.extern.slf4j.Slf4j;
import org.noear.snack4.ONode;
import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.ai.chat.tool.AbsToolProvider;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Param;
import site.sorghum.agent4j.bin.agent.core.Agent4jAgent;
import site.sorghum.agent4j.bin.workflow.*;
import site.sorghum.agent4j.bin.workspace.WorkspaceManager;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.solon.SolonToTools;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Workflow Create DAG 工具 —— 通过工具调用创建完整的工作流 DAG 结构。
 * <p>
 * LLM 通过调用此工具传入完整的节点和边定义，系统负责解析、验证和持久化。
 * 比直接让 LLM 返回 JSON 更可靠，因为工具调用有参数校验。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
@Component
public class WorkflowCreateDagTool extends AbsToolProvider implements SolonToTools {

    @ToolMapping(name = "workflow_create_dag", description = """
            创建一个完整的工作流 DAG（有向无环图）结构。
            
            使用场景：
            - 当用户要求创建一个复杂的工作流时
            - 当任务需要条件分支或并行执行时
            - 当需要明确的依赖关系时
            
            参数说明：
            - title: 工作流标题
            - description: 工作流详细描述
            - nodesJson: 节点数组的 JSON 字符串，每个节点包含：
              * id: 节点ID（如 "n1", "n2"）
              * description: 节点描述
              * type: 节点类型（ACTION/CONDITION/PARALLEL，默认 ACTION）
              * condition: 条件表达式（仅 CONDITION 类型需要）
            - edgesJson: 边数组的 JSON 字符串，每条边包含：
              * from: 源节点ID
              * to: 目标节点ID
              * type: 边类型（NORMAL/CONDITION_TRUE/CONDITION_FALSE，默认 NORMAL）
            
            示例调用：
            workflow_create_dag(
              title="用户注册功能",
              description="实现完整的用户注册流程",
              nodesJson='[{"id":"n1","description":"创建用户表","type":"ACTION"},{"id":"n2","description":"验证邮箱","type":"ACTION"}]',
              edgesJson='[{"from":"start","to":"n1","type":"NORMAL"},{"from":"n1","to":"n2","type":"NORMAL"},{"from":"n2","to":"end","type":"NORMAL"}]'
            )
            """)
    public String workflowCreateDag(
            @Param(name = "title", description = "工作流标题") String title,
            @Param(name = "description", description = "工作流详细描述") String description,
            @Param(name = "nodesJson", description = "节点数组的 JSON 字符串") String nodesJson,
            @Param(name = "edgesJson", description = "边数组的 JSON 字符串") String edgesJson,
            ToolContext ctx) {
        
        // 参数校验
        if (title == null || title.isBlank()) {
            return "PARAM_MISSING: 缺少必填参数 'title'";
        }
        if (nodesJson == null || nodesJson.isBlank()) {
            return "PARAM_MISSING: 缺少必填参数 'nodesJson'";
        }
        if (edgesJson == null || edgesJson.isBlank()) {
            return "PARAM_MISSING: 缺少必填参数 'edgesJson'";
        }
        
        String sessionId = ctx.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return "SESSION_MISSING: 无法获取会话 ID，请确保已在会话中";
        }
        
        try {
            // 解析节点
            List<WorkflowNode> nodes = parseNodes(nodesJson);
            if (nodes.isEmpty()) {
                return "PARSE_ERROR: 节点列表为空，请提供至少一个节点";
            }
            
            // 解析边
            List<WorkflowEdge> edges = parseEdges(edgesJson);
            
            // 验证 DAG 结构
            String validationError = validateDag(nodes, edges);
            if (validationError != null) {
                return "VALIDATION_ERROR: " + validationError;
            }
            
            // 获取工作区信息
            String rootDir = ctx.getRootDir().toAbsolutePath().toString();
            WorkspaceManager workspaceManager = WorkspaceManager.getOrCreate(rootDir);
            String workspaceHash = workspaceManager.getCurrentWorkspaceHash();
            if (workspaceHash == null) {
                return "WORKSPACE_NOT_INITIALIZED: 工作区未初始化";
            }
            
            // 创建工作流
            Workflow workflow = Workflow.builder()
                    .id(UUID.randomUUID().toString().substring(0, 8))
                    .sessionId(sessionId)
                    .workspaceHash(workspaceHash)
                    .title(title.length() > 50 ? title.substring(0, 50) + "..." : title)
                    .description(description != null ? description : title)
                    .status(WorkflowStatus.DRAFT)
                    .maxRetries(3)
                    .nodes(nodes)
                    .edges(edges)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            
            // 持久化
            WorkflowStore store = workspaceManager.getWorkflowStore();
            store.save(workflow);
            
            // 生成可视化文本
            return generateSuccessResponse(workflow);
            
        } catch (Exception e) {
            log.error("[workflow] 创建 DAG 工作流失败", e);
            return "CREATE_FAILED: " + e.getMessage();
        }
    }
    
    /**
     * 解析节点 JSON。
     */
    private List<WorkflowNode> parseNodes(String nodesJson) {
        List<WorkflowNode> nodes = new ArrayList<>();
        
        // 添加开始节点
        nodes.add(WorkflowNode.builder()
                .id("start")
                .description("开始")
                .type(NodeType.START)
                .status(NodeStatus.DONE)
                .retryCount(0)
                .build());
        
        try {
            ONode nodesArray = ONode.ofJson(nodesJson);
            if (nodesArray.isArray()) {
                for (ONode nodeObj : nodesArray.getArray()) {
                    String id = nodeObj.get("id").getString();
                    String desc = nodeObj.get("description").getString();
                    String typeStr = nodeObj.get("type").getString();
                    String condition = nodeObj.get("condition").getString();
                    
                    if (id == null || id.isBlank() || desc == null || desc.isBlank()) {
                        continue; // 跳过无效节点
                    }
                    
                    NodeType type = NodeType.ACTION;
                    if (typeStr != null && !typeStr.isBlank()) {
                        try {
                            type = NodeType.valueOf(typeStr.toUpperCase());
                        } catch (IllegalArgumentException e) {
                            // 默认使用 ACTION
                        }
                    }
                    
                    WorkflowNode node = WorkflowNode.builder()
                            .id(id)
                            .description(desc)
                            .type(type)
                            .status(NodeStatus.PENDING)
                            .retryCount(0)
                            .condition(condition)
                            .build();
                    
                    // 解析并行分支
                    if (type == NodeType.PARALLEL && !nodeObj.get("parallelBranches").isNull()) {
                        List<String> branches = new ArrayList<>();
                        ONode branchesArray = nodeObj.get("parallelBranches");
                        if (branchesArray.isArray()) {
                            for (ONode branch : branchesArray.getArray()) {
                                String branchId = branch.getString();
                                if (branchId != null && !branchId.isBlank()) {
                                    branches.add(branchId);
                                }
                            }
                        }
                        node.setParallelBranches(branches);
                    }
                    
                    nodes.add(node);
                }
            }
        } catch (Exception e) {
            log.warn("[workflow] 解析节点 JSON 失败", e);
        }
        
        // 添加结束节点
        nodes.add(WorkflowNode.builder()
                .id("end")
                .description("结束")
                .type(NodeType.END)
                .status(NodeStatus.PENDING)
                .retryCount(0)
                .build());
        
        return nodes;
    }
    
    /**
     * 解析边 JSON。
     */
    private List<WorkflowEdge> parseEdges(String edgesJson) {
        List<WorkflowEdge> edges = new ArrayList<>();
        
        try {
            ONode edgesArray = ONode.ofJson(edgesJson);
            if (edgesArray.isArray()) {
                int edgeIdx = 1;
                for (ONode edgeObj : edgesArray.getArray()) {
                    String from = edgeObj.get("from").getString();
                    String to = edgeObj.get("to").getString();
                    String typeStr = edgeObj.get("type").getString();
                    String condition = edgeObj.get("condition").getString();
                    
                    if (from == null || from.isBlank() || to == null || to.isBlank()) {
                        continue; // 跳过无效边
                    }
                    
                    EdgeType type = EdgeType.NORMAL;
                    if (typeStr != null && !typeStr.isBlank()) {
                        try {
                            type = EdgeType.valueOf(typeStr.toUpperCase());
                        } catch (IllegalArgumentException e) {
                            // 默认使用 NORMAL
                        }
                    }
                    
                    edges.add(WorkflowEdge.builder()
                            .id("e" + edgeIdx++)
                            .from(from)
                            .to(to)
                            .type(type)
                            .condition(condition)
                            .build());
                }
            }
        } catch (Exception e) {
            log.warn("[workflow] 解析边 JSON 失败", e);
        }
        
        return edges;
    }
    
    /**
     * 验证 DAG 结构的有效性。
     */
    private String validateDag(List<WorkflowNode> nodes, List<WorkflowEdge> edges) {
        // 收集所有节点 ID
        List<String> nodeIds = nodes.stream().map(WorkflowNode::getId).toList();
        
        // 验证边的端点是否存在
        for (WorkflowEdge edge : edges) {
            if (!nodeIds.contains(edge.getFrom())) {
                return "边的源节点 '" + edge.getFrom() + "' 不存在";
            }
            if (!nodeIds.contains(edge.getTo())) {
                return "边的目标节点 '" + edge.getTo() + "' 不存在";
            }
        }
        
        // 检查是否有从 start 出发的边
        boolean hasStartEdge = edges.stream().anyMatch(e -> "start".equals(e.getFrom()));
        if (!hasStartEdge) {
            return "缺少从 start 节点出发的边";
        }
        
        // 检查是否有到达 end 的边
        boolean hasEndEdge = edges.stream().anyMatch(e -> "end".equals(e.getTo()));
        if (!hasEndEdge) {
            return "缺少到达 end 节点的边";
        }
        
        // 检查是否有循环（简单的 DFS 检测）
        if (hasCycle(nodes, edges)) {
            return "工作流存在循环依赖";
        }
        
        return null; // 验证通过
    }
    
    /**
     * 检测是否有循环。
     */
    private boolean hasCycle(List<WorkflowNode> nodes, List<WorkflowEdge> edges) {
        // 构建邻接表
        java.util.Map<String, List<String>> adjacency = new java.util.HashMap<>();
        for (WorkflowNode node : nodes) {
            adjacency.put(node.getId(), new ArrayList<>());
        }
        for (WorkflowEdge edge : edges) {
            adjacency.get(edge.getFrom()).add(edge.getTo());
        }
        
        // DFS 检测循环
        java.util.Set<String> visited = new java.util.HashSet<>();
        java.util.Set<String> recursionStack = new java.util.HashSet<>();
        
        for (WorkflowNode node : nodes) {
            if (dfsHasCycle(node.getId(), adjacency, visited, recursionStack)) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean dfsHasCycle(String node, java.util.Map<String, List<String>> adjacency,
                                java.util.Set<String> visited, java.util.Set<String> recursionStack) {
        if (recursionStack.contains(node)) {
            return true; // 发现循环
        }
        if (visited.contains(node)) {
            return false; // 已经检查过
        }
        
        visited.add(node);
        recursionStack.add(node);
        
        List<String> neighbors = adjacency.getOrDefault(node, List.of());
        for (String neighbor : neighbors) {
            if (dfsHasCycle(neighbor, adjacency, visited, recursionStack)) {
                return true;
            }
        }
        
        recursionStack.remove(node);
        return false;
    }
    
    /**
     * 生成成功响应（JSON 格式）。
     */
    private String generateSuccessResponse(Workflow workflow) {
        ONode response = ONode.ofJson("{}");
        response.set("success", true);
        response.set("workflowId", workflow.getId());
        response.set("title", workflow.getTitle());
        response.set("description", workflow.getDescription());
        response.set("status", workflow.getStatus().name());
        
        // 节点列表
        ONode nodesArr = response.getOrNew("nodes").asArray();
        for (WorkflowNode node : workflow.getNodes()) {
            ONode nodeObj = nodesArr.addNew();
            nodeObj.set("id", node.getId());
            nodeObj.set("description", node.getDescription());
            nodeObj.set("type", node.getType().name());
            nodeObj.set("status", node.getStatus().name());
        }
        
        // 边列表
        ONode edgesArr = response.getOrNew("edges").asArray();
        for (WorkflowEdge edge : workflow.getEdges()) {
            ONode edgeObj = edgesArr.addNew();
            edgeObj.set("id", edge.getId());
            edgeObj.set("from", edge.getFrom());
            edgeObj.set("to", edge.getTo());
            edgeObj.set("type", edge.getType().name());
            if (edge.getCondition() != null) {
                edgeObj.set("condition", edge.getCondition());
            }
        }
        
        response.set("nodeCount", workflow.getNodes().size());
        response.set("edgeCount", workflow.getEdges().size());
        
        return response.toJson();
    }
    
    @Override
    public Collection<FunctionTool> getSolonTools() {
        return this.getTools();
    }
    
    @Override
    public String getSystemPrompt() {
        return """
                创建一个完整的工作流 DAG（有向无环图）结构。
                当用户要求创建复杂的工作流、需要条件分支或并行执行时使用此工具。
                """;
    }
}
