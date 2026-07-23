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
import site.sorghum.loopra.tool.solon.SolonToTools;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Checklist Start 工具 —— 创建有序步骤清单。
 * <p>
 * LLM 传入有序步骤列表，系统创建 Checklist 并持久化。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
@Component
public class ChecklistStartTool extends AbsToolProvider implements SolonToTools {

    @ToolMapping(name = "checklist_start", description = """
            创建一个有序步骤的工作清单（checklist）。
            
            使用场景：
            - 需要多步骤跟踪和进度可视化时
            - 需要人工审批环节时
            - 需要步骤级失败恢复时
            
            注意：清单是有序的线性步骤列表，LLM 在每一步内完全自由推理。
            每完成一步后调用 checklist_step 标记完成并自动推进到下一步。
            """)
    public String checklistStart(
            @Param(name = "title", description = "清单标题") String title,
            @Param(name = "description", description = "清单详细描述") String description,
            @Param(name = "steps", description = "步骤数组 JSON，如 [{\"description\":\"分析需求\",\"kind\":\"step\"},{\"description\":\"人工确认\",\"kind\":\"hitl\"}]") String stepsJson,
            ToolContext ctx) {
        // 参数校验
        if (title == null || title.isBlank()) {
            return "PARAM_MISSING: 缺少必填参数 'title'";
        }
        if (stepsJson == null || stepsJson.isBlank()) {
            return "PARAM_MISSING: 缺少必填参数 'steps'";
        }

        String sessionId = ctx.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return "SESSION_MISSING: 无法获取会话 ID";
        }

        try {
            // 解析步骤定义
            List<ChecklistEngine.StepDef> stepDefs = parseSteps(stepsJson);
            if (stepDefs.isEmpty()) {
                return "PARSE_ERROR: 步骤列表为空，请提供至少一个步骤";
            }

            // 获取工作区信息
            String rootDir = ctx.getRootDir().toAbsolutePath().toString();
            WorkspaceManager workspaceManager = WorkspaceManager.getOrCreate(rootDir);

            // 创建清单
            ChecklistEngine engine = new ChecklistEngine();
            Checklist cl = engine.createChecklist(
                    sessionId,
                    workspaceManager.getCurrentWorkspaceHash(),
                    title,
                    description != null ? description : title,
                    stepDefs);

            // 持久化（使用 KV store）
            workspaceManager.getChecklistStore().save(cl);

            log.info("[checklist] 创建清单成功: title={}, steps={}", title, stepDefs.size());

            // 返回状态信息
            return buildResponse(cl);

        } catch (Exception e) {
            log.error("[checklist] 创建清单失败", e);
            return "CREATE_FAILED: " + e.getMessage();
        }
    }

    private List<ChecklistEngine.StepDef> parseSteps(String stepsJson) {
        List<ChecklistEngine.StepDef> defs = new ArrayList<>();
        try {
            org.noear.snack4.ONode arr = org.noear.snack4.ONode.ofJson(stepsJson);
            if (!arr.isArray()) return defs;
            for (int i = 0; i < arr.getArray().size(); i++) {
                var item = arr.getArray().get(i);
                String desc = item.get("description").getString();
                if (desc == null || desc.isBlank()) continue;
                String kindStr = item.get("kind").getString();
                site.sorghum.loopra.bin.checklist.StepKind kind = "hitl".equalsIgnoreCase(kindStr)
                        ? site.sorghum.loopra.bin.checklist.StepKind.HITL
                        : "fork".equalsIgnoreCase(kindStr)
                        ? site.sorghum.loopra.bin.checklist.StepKind.FORK
                        : site.sorghum.loopra.bin.checklist.StepKind.STEP;
                defs.add(new ChecklistEngine.StepDef(desc, kind));
            }
        } catch (Exception e) {
            log.warn("[checklist] 解析步骤 JSON 失败: {}", e.getMessage());
        }
        return defs;
    }

    private String buildResponse(Checklist cl) {
        var resp = new org.noear.snack4.ONode();
        resp.set("checklistId", cl.getId());
        resp.set("title", cl.getTitle());
        resp.set("status", cl.getStatus());
        resp.set("currentStepIndex", cl.getCurrentStepIndex());
        resp.set("totalSteps", cl.getSteps().size());
        resp.set("progress", cl.progressText());

        var stepsArr = org.noear.snack4.ONode.ofJson("[]").asArray();
        resp.set("steps", stepsArr);
        for (var step : cl.getSteps()) {
            var s = stepsArr.addNew();
            s.set("id", step.getId());
            s.set("description", step.getDescription());
            s.set("kind", step.getKind().name());
            s.set("status", step.getStatus().name());
        }

        var current = cl.currentStep();
        if (current != null) {
            resp.set("currentStep", current.getId() + ": " + current.getDescription());
        }

        return resp.toJson();
    }

    @Override
    public Collection<FunctionTool> getSolonTools() {
        return this.getTools();
    }
}
