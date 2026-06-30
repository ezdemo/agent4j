package site.sorghum.agent4j.bin.builtin;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.ai.chat.tool.AbsToolProvider;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Param;
import site.sorghum.agent4j.bin.workflow2.SimpleWorkflow;
import site.sorghum.agent4j.bin.workflow2.SimpleWorkflowEngine;
import site.sorghum.agent4j.bin.workspace.WorkspaceManager;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.solon.SolonToTools;

import java.util.Collection;
import java.util.List;

/**
 * Workflow Status 工具 —— 查看工作流状态。
 * <p>
 * 替代旧的 workflow_visualize。
 * 返回当前步骤索引、总步骤数、各步骤状态。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
@Component
public class WorkflowStatusTool extends AbsToolProvider implements SolonToTools {

    @ToolMapping(name = "workflow_status", description = """
            查看当前会话的工作流状态。
            
            返回：
            - 工作流标题和状态
            - 当前步骤索引 / 总步骤数
            - 各步骤的状态（PENDING / RUNNING / DONE / SKIPPED / FAILED）
            - 进度百分比
            """)
    public String workflowStatus(
            @Param(name = "sessionId", description = "会话 ID。留空自动从上下文获取当前会话", required = false) String sessionId,
            ToolContext ctx) {
        try {
            if (sessionId == null || sessionId.isBlank()) {
                sessionId = ctx.getSessionId();
            }
            if (sessionId == null || sessionId.isBlank()) {
                return "SESSION_MISSING: 无法获取会话 ID";
            }

            String rootDir = ctx.getRootDir().toAbsolutePath().toString();
            WorkspaceManager workspaceManager = WorkspaceManager.getOrCreate(rootDir);

            SimpleWorkflow wf = workspaceManager.getWorkflowStore2().findBySession(sessionId);
            if (wf == null) {
                return "WORKFLOW_NOT_FOUND: 当前会话没有活跃工作流。";
            }

            SimpleWorkflowEngine engine = new SimpleWorkflowEngine();
            var resp = engine.toStatusJson(wf);
            return resp.toJson();

        } catch (Exception e) {
            log.error("[workflow] 获取工作流状态失败", e);
            return "STATUS_FAILED: " + e.getMessage();
        }
    }

    @Override
    public Collection<FunctionTool> getSolonTools() {
        return this.getTools();
    }
}
