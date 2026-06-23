package site.sorghum.agent4j.bin.builtin;

import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.ai.chat.tool.AbsToolProvider;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Param;
import org.noear.snack4.ONode;
import site.sorghum.agent4j.bin.workflow.*;
import site.sorghum.agent4j.bin.workspace.WorkspaceManager;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.solon.SolonToTools;

import java.util.Collection;

/**
 * Workflow Visualization 工具 —— 可视化显示工作流结构。
 * <p>
 * 提供工作流的文本表示，包括节点列表、依赖关系和执行状态。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class WorkflowVisualizationTool extends AbsToolProvider implements SolonToTools {

    @ToolMapping(name = "workflow_visualize", description = """
                可视化显示当前会话的工作流结构。
                返回工作流的节点列表、依赖关系和执行状态。
                """)
    public String workflowVisualize(@Param(name = "sessionId", description = "会话 ID。留空自动从上下文获取当前会话", required = false) String sessionId,
                                   ToolContext ctx) {
        // 获取 sessionId
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = ctx.getSessionId();
        }
        if (sessionId == null || sessionId.isBlank()) {
            return "SESSION_MISSING: 无法获取会话 ID，请确保已在会话中";
        }

        try {
            WorkflowStore workflowStore = WorkspaceManager.getOrCreate(ctx.getRootDir().toAbsolutePath().toString()).getWorkflowStore();
            Workflow workflow = workflowStore.findBySession(sessionId);
            if (workflow == null) {
                return "WORKFLOW_NOT_FOUND: 当前会话没有活跃工作流。请先使用 /workflow create 创建工作流。";
            }

            return generateVisualization(workflow);

        } catch (IllegalStateException e) {
            return "WORKSPACE_NOT_INITIALIZED: 工作区未初始化，无法获取工作流：" + e.getMessage();
        } catch (Exception e) {
            return "VISUALIZATION_FAILED: 生成工作流可视化失败：" + e.getMessage();
        }
    }

    /**
     * 生成工作流的 JSON 可视化表示。
     */
    private String generateVisualization(Workflow workflow) {
        ONode response = ONode.ofJson("{}");
        
        // 工作流基本信息
        response.set("workflowId", workflow.getId());
        response.set("title", workflow.getTitle());
        response.set("description", workflow.getDescription());
        response.set("status", workflow.getStatus().name());
        response.set("progress", workflow.progressText());
        
        // 节点列表
        ONode nodesArr = response.getOrNew("nodes").asArray();
        for (WorkflowNode node : workflow.getNodes()) {
            ONode nodeObj = nodesArr.addNew();
            nodeObj.set("id", node.getId());
            nodeObj.set("description", node.getDescription());
            nodeObj.set("type", node.getType().name());
            nodeObj.set("status", node.getStatus().name());
            nodeObj.set("retryCount", node.getRetryCount());
            
            if (node.getLastError() != null && !node.getLastError().isEmpty()) {
                nodeObj.set("lastError", node.getLastError());
            }
            if (node.getResult() != null && !node.getResult().isEmpty()) {
                nodeObj.set("result", node.getResult());
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
        }
        
        // 执行路径
        ONode pathArr = response.getOrNew("executionPath").asArray();
        buildExecutionPath(workflow, "start", pathArr);
        
        return response.toJson();
    }

    /**
     * 递归构建执行路径。
     */
    private void buildExecutionPath(Workflow workflow, String nodeId, ONode pathArr) {
        WorkflowNode node = workflow.findNode(nodeId);
        if (node == null) return;
        
        ONode pathNode = pathArr.addNew();
        pathNode.set("id", node.getId());
        pathNode.set("description", node.getDescription());
        pathNode.set("status", node.getStatus().name());
        
        // 获取后继节点
        java.util.List<String> successors = workflow.getSuccessorIds(nodeId);
        if (!successors.isEmpty()) {
            ONode nextArr = pathNode.getOrNew("next").asArray();
            for (String successorId : successors) {
                buildExecutionPath(workflow, successorId, nextArr);
            }
        }
    }

    @Override
    public Collection<FunctionTool> getSolonTools() {
        return this.getTools();
    }

    @Override
    public String getSystemPrompt() {
        return """
                可视化显示当前会话的工作流结构。
                返回工作流的节点列表、依赖关系和执行状态。
                """;
    }
}