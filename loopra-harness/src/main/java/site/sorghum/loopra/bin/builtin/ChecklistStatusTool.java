package site.sorghum.loopra.bin.builtin;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.ai.chat.tool.AbsToolProvider;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Param;
import site.sorghum.loopra.bin.checklist.Checklist;
import site.sorghum.loopra.bin.checklist.ChecklistEngine;
import site.sorghum.loopra.bin.workspace.WorkspaceManager;
import site.sorghum.loopra.tool.ToolContext;
import site.sorghum.loopra.tool.SolonToTools;

import java.util.Collection;

/**
 * Checklist Status 工具 —— 查看清单状态。
 * <p>
 * 返回当前步骤索引、总步骤数、各步骤状态。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
@Component
public class ChecklistStatusTool extends AbsToolProvider implements SolonToTools {

    @ToolMapping(name = "checklist_status", description = """
            查看当前会话的清单状态。
            
            返回：
            - 清单标题和状态
            - 当前步骤索引 / 总步骤数
            - 各步骤的状态（PENDING / RUNNING / DONE / SKIPPED / FAILED）
            - 进度百分比
            """)
    public String checklistStatus(
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

            Checklist cl = workspaceManager.getChecklistStore().findBySession(sessionId);
            if (cl == null) {
                return "CHECKLIST_NOT_FOUND: 当前会话没有活跃清单。";
            }

            ChecklistEngine engine = new ChecklistEngine();
            var resp = engine.toStatusJson(cl);
            return resp.toJson();

        } catch (Exception e) {
            log.error("[checklist] 获取清单状态失败", e);
            return "STATUS_FAILED: " + e.getMessage();
        }
    }

    @Override
    public Collection<FunctionTool> getSolonTools() {
        return this.getTools();
    }
}
