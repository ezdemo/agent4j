package site.sorghum.agent4j.bin.builtin;

import lombok.extern.slf4j.Slf4j;
import org.noear.snack4.ONode;
import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.ai.chat.tool.AbsToolProvider;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Param;
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
            - 当子任务需要隔离的子代理执行时
            
            ═══════════════════════════════════════════════
            参数说明
            ═══════════════════════════════════════════════
            - title: 工作流标题
            - description: 工作流详细描述
            - nodesJson: 节点数组的 JSON 字符串
            - edgesJson: 边数组的 JSON 字符串
            
            ═══════════════════════════════════════════════
            节点类型（type）
            ═══════════════════════════════════════════════
            - ACTION:    普通动作（默认）。直接执行，适合简单任务（创建文件、运行命令等）
            - PARALLEL:  并行 fork 点。系统并发执行 parallelBranches 中的所有分支
            - CONDITION: 条件判断。系统自动评估条件并选择一个后继分支
            - SUBFLOW:   子代理执行。适合复杂任务（需求分析、架构设计、多步推理），会自动创建子代理
            - HITL:      人工审批。暂停工作流等待用户 approve/deny
            （start 和 end 节点由系统自动生成，不需要你创建）
            
            ═══════════════════════════════════════════════
            边类型（type）
            ═══════════════════════════════════════════════
            - NORMAL:           普通依赖（默认）
            - CONDITION_SELECT: 条件选择边（用于 CONDITION 节点的 N 路分支）
            - CONDITION_TRUE:   条件为真（兼容旧版，推荐用 CONDITION_SELECT）
            - CONDITION_FALSE:  条件为假（兼容旧版，推荐用 CONDITION_SELECT）
            边额外字段：
            - label: 边标签（可选，用于 CONDITION_SELECT 标注分支含义，如 "通过"/"失败"）
            
            ═══════════════════════════════════════════════
            节点额外字段
            ═══════════════════════════════════════════════
            PARALLEL 节点：
            - parallelBranches: ["n2", "n3"]  并行分支的起始节点ID列表（必填）
            
            CONDITION 节点：
            - condition: "描述判断条件"  条件表达式（必填）
            
            HITL 节点：
            - approvalPrompt: "审批提示文本"
            
            ═══════════════════════════════════════════════
            ⭐ DAG 构建规则（重要！）
            ═══════════════════════════════════════════════
            
            1. 【基本结构】
               - start 和 end 节点由系统自动添加，不要在 nodes 中创建
               - 必须有从 start 出发的边和到达 end 的边
               - 所有节点必须可达（不能有孤立节点）
               - 不能有循环依赖
            
            2. 【串行流程】
               start → n1 → n2 → n3 → end
               edges: [{"from":"start","to":"n1"}, {"from":"n1","to":"n2"}, {"from":"n2","to":"n3"}, {"from":"n3","to":"end"}]
            
            3. 【并行分支】⭐
               用 PARALLEL 节点作为 fork 点，parallelBranches 列出并行分支起始节点。
               汇聚节点用多个入边实现 join（系统自动等待所有前驱完成）。
               
               示例：并行开发前端和后端，然后集成测试
               ```
               nodes: [
                 {"id":"n1","description":"设计","type":"ACTION"},
                 {"id":"n_fork","description":"并行开发","type":"PARALLEL","parallelBranches":["n2","n3"]},
                 {"id":"n2","description":"前端开发","type":"ACTION"},
                 {"id":"n3","description":"后端开发","type":"ACTION"},
                 {"id":"n4","description":"集成测试","type":"ACTION"}
               ]
               edges: [
                 {"from":"start","to":"n1"},
                 {"from":"n1","to":"n_fork"},
                 {"from":"n_fork","to":"n2"},
                 {"from":"n_fork","to":"n3"},
                 {"from":"n2","to":"n4"},
                 {"from":"n3","to":"n4"},
                 {"from":"n4","to":"end"}
               ]
               ```
               关键：n4 有两个入边（n2→n4, n3→n4），系统自动等待 n2 和 n3 都完成才执行 n4。
            
            4. 【多条件分支】⭐
               用 CONDITION 节点 + CONDITION_SELECT 边实现 N 路分支。
               系统通过子代理评估条件，选择一个分支执行，自动跳过其他分支。
               
               示例：测试结果判断（3 个分支）
               ```
               nodes: [
                 {"id":"n1","description":"运行测试","type":"ACTION"},
                 {"id":"n_cond","description":"评估测试结果","type":"CONDITION","condition":"测试通过率"},
                 {"id":"n_pass","description":"部署","type":"ACTION"},
                 {"id":"n_fail","description":"修复失败用例","type":"ACTION"},
                 {"id":"n_rollback","description":"回滚","type":"ACTION"}
               ]
               edges: [
                 {"from":"start","to":"n1"},
                 {"from":"n1","to":"n_cond"},
                 {"from":"n_cond","to":"n_pass","type":"CONDITION_SELECT","label":"全部通过"},
                 {"from":"n_cond","to":"n_fail","type":"CONDITION_SELECT","label":"部分失败"},
                 {"from":"n_cond","to":"n_rollback","type":"CONDITION_SELECT","label":"严重失败"},
                 {"from":"n_pass","to":"end"},
                 {"from":"n_fail","to":"end"},
                 {"from":"n_rollback","to":"end"}
               ]
               ```
               关键：条件分支的汇聚节点（如 end）同样用多入边 join。
            
            5. 【ACTION vs SUBFLOW】⭐
               ACTION: 直接执行，LLM 自己做。适合简单任务（创建文件、运行命令、写配置等）。
               SUBFLOW: 创建子代理执行。适合复杂任务（需求分析、架构设计、大规模重构等需要深度推理的场景）。
               
               大多数节点用 ACTION 就够了，只有真正复杂的任务才用 SUBFLOW。
               
               示例：
               ```
               nodes: [
                 {"id":"n1","description":"分析需求文档，输出 API 接口规范","type":"SUBFLOW"},
                 {"id":"n2","description":"根据规范创建数据库表","type":"ACTION"},
                 {"id":"n3","description":"实现 API 接口","type":"ACTION"}
               ]
               ```
            
            6. 【人工审批】
               HITL 节点会暂停工作流，等待用户通过 /workflow approve 或 /workflow deny 操作。
               
               ```
               nodes: [
                 {"id":"n1","description":"生成部署方案","type":"ACTION"},
                 {"id":"n2","description":"人工确认部署","type":"HITL","approvalPrompt":"确认部署到生产环境？"},
                 {"id":"n3","description":"执行部署","type":"ACTION"}
               ]
               ```
            
            7. 【综合示例】（并发 + 多条件 + 子代理）
               用户需求："分析需求，并行开发前后端，测试通过后人工确认部署"
               ```
               nodes: [
                 {"id":"n1","description":"分析需求文档，输出设计文档","type":"SUBFLOW"},
                 {"id":"n_fork","description":"并行开发","type":"PARALLEL","parallelBranches":["n2","n3"]},
                 {"id":"n2","description":"实现前端页面","type":"ACTION"},
                 {"id":"n3","description":"实现后端 API","type":"ACTION"},
                 {"id":"n4","description":"运行集成测试","type":"ACTION"},
                 {"id":"n_cond","description":"评估测试结果","type":"CONDITION","condition":"集成测试是否全部通过"},
                 {"id":"n5","description":"确认部署","type":"HITL","approvalPrompt":"测试已通过，确认部署到生产？"},
                 {"id":"n6","description":"修复失败的用例","type":"ACTION"}
               ]
               edges: [
                 {"from":"start","to":"n1"},
                 {"from":"n1","to":"n_fork"},
                 {"from":"n_fork","to":"n2"},
                 {"from":"n_fork","to":"n3"},
                 {"from":"n2","to":"n4"},
                 {"from":"n3","to":"n4"},
                 {"from":"n4","to":"n_cond"},
                 {"from":"n_cond","to":"n5","type":"CONDITION_SELECT","label":"全部通过"},
                 {"from":"n_cond","to":"n6","type":"CONDITION_SELECT","label":"有失败"},
                 {"from":"n5","to":"end"},
                 {"from":"n6","to":"end"}
               ]
               ```
            
            ═══════════════════════════════════════════════
            常见错误（请避免）
            ═══════════════════════════════════════════════
            ❌ 在 nodes 中创建 start 或 end 节点（系统自动添加）
            ❌ PARALLEL 节点没有 parallelBranches 字段
            ❌ CONDITION 节点没有 condition 字段
            ❌ 条件分支用 NORMAL 边而不是 CONDITION_SELECT 边
            ❌ 并行分支的汇聚节点缺少某个分支的入边（会导致 join 不完整）
            ❌ 节点之间存在循环依赖
            ❌ 节点描述过于笼统（如"处理数据"，应该具体到"解析 CSV 文件并计算统计值"）
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

                    // 防止用户手动创建 start/end
                    if ("start".equalsIgnoreCase(id) || "end".equalsIgnoreCase(id)) {
                        continue;
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

                    // 解析 HITL 审批提示
                    String approvalPrompt = nodeObj.get("approvalPrompt").getString();
                    if (approvalPrompt != null && !approvalPrompt.isBlank()) {
                        node.setApprovalPrompt(approvalPrompt);
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
                    String label = edgeObj.get("label").getString();
                    
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
                            .label(label)
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
        
        // 验证 PARALLEL 节点的 parallelBranches 非空
        for (WorkflowNode node : nodes) {
            if (node.getType() == NodeType.PARALLEL) {
                if (node.getParallelBranches() == null || node.getParallelBranches().isEmpty()) {
                    return "PARALLEL 节点 '" + node.getId() + "' 缺少 parallelBranches 字段";
                }
            }
            if (node.getType() == NodeType.CONDITION) {
                if (node.getCondition() == null || node.getCondition().isBlank()) {
                    return "CONDITION 节点 '" + node.getId() + "' 缺少 condition 字段";
                }
            }
        }
        
        // 验证 CONDITION 节点的出边类型
        for (WorkflowNode node : nodes) {
            if (node.getType() == NodeType.CONDITION) {
                List<WorkflowEdge> outEdges = edges.stream()
                        .filter(e -> e.getFrom().equals(node.getId()))
                        .toList();
                boolean hasSelect = outEdges.stream().anyMatch(e ->
                        e.getType() == EdgeType.CONDITION_SELECT
                                || e.getType() == EdgeType.CONDITION_TRUE
                                || e.getType() == EdgeType.CONDITION_FALSE);
                if (!hasSelect) {
                    return "CONDITION 节点 '" + node.getId() + "' 的出边必须使用 CONDITION_SELECT 类型";
                }
            }
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
            if (node.getType() == NodeType.PARALLEL && node.getParallelBranches() != null) {
                ONode branchesArr = nodeObj.getOrNew("parallelBranches").asArray();
                for (String branch : node.getParallelBranches()) {
                    branchesArr.addNew().fill(branch);
                }
            }
            if (node.getType() == NodeType.CONDITION && node.getCondition() != null) {
                nodeObj.set("condition", node.getCondition());
            }
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
            if (edge.getLabel() != null) {
                edgeObj.set("label", edge.getLabel());
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
                支持 ACTION/PARALLEL/CONDITION/SUBFLOW/HITL 五种节点类型。
                """;
    }
}
