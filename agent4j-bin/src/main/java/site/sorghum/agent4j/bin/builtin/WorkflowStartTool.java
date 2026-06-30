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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Workflow Start 工具 —— 创建有序步骤工作流。
 * <p>
 * 替代旧的 workflow_create_dag（DAG 图）方式。
 * LLM 传入有序步骤列表，系统创建 SimpleWorkflow 并持久化。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
@Component
public class WorkflowStartTool extends AbsToolProvider implements SolonToTools {

    @ToolMapping(name = "workflow_start", description = """
            创建一个有序步骤的工作流。
            
            使用场景：
            - 需要多步骤跟踪和进度可视化时
            - 需要人工审批环节时
            - 需要步骤级失败恢复时
            
            注意：工作流是有序的线性步骤列表，LLM 在每一步内完全自由推理。
            每完成一步后调用 workflow_step 标记完成并自动推进到下一步。
            """)
    public String workflowStart(
            @Param(name = "title", description = "工作流标题") String title,
            @Param(name = "description", description = "工作流详细描述") String description,
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
            List<SimpleWorkflowEngine.StepDef> stepDefs = parseSteps(stepsJson);
            if (stepDefs.isEmpty()) {
                return "PARSE_ERROR: 步骤列表为空，请提供至少一个步骤";
            }

            // 获取工作区信息
            String rootDir = ctx.getRootDir().toAbsolutePath().toString();
            WorkspaceManager workspaceManager = WorkspaceManager.getOrCreate(rootDir);

            // 创建工作流
            SimpleWorkflowEngine engine = new SimpleWorkflowEngine();
            SimpleWorkflow wf = engine.createWorkflow(
                    sessionId,
                    workspaceManager.getCurrentWorkspaceHash(),
                    title,
                    description != null ? description : title,
                    stepDefs);

            // 持久化（使用 KV store）
            workspaceManager.getWorkflowStore2().save(wf);

            log.info("[workflow] 创建工作流成功: title={}, steps={}", title, stepDefs.size());

            // 返回状态信息
            return buildResponse(wf);

        } catch (Exception e) {
            log.error("[workflow] 创建工作流失败", e);
            return "CREATE_FAILED: " + e.getMessage();
        }
    }

    private List<SimpleWorkflowEngine.StepDef> parseSteps(String stepsJson) {
        List<SimpleWorkflowEngine.StepDef> defs = new ArrayList<>();
        try {
            org.noear.snack4.ONode arr = org.noear.snack4.ONode.ofJson(stepsJson);
            if (!arr.isArray()) return defs;
            for (int i = 0; i < arr.getArray().size(); i++) {
                var item = arr.getArray().get(i);
                String desc = item.get("description").getString();
                if (desc == null || desc.isBlank()) continue;
                String kindStr = item.get("kind").getString();
                site.sorghum.agent4j.bin.workflow2.StepKind kind = "hitl".equalsIgnoreCase(kindStr)
                        ? site.sorghum.agent4j.bin.workflow2.StepKind.HITL
                        : "fork".equalsIgnoreCase(kindStr)
                        ? site.sorghum.agent4j.bin.workflow2.StepKind.FORK
                        : site.sorghum.agent4j.bin.workflow2.StepKind.STEP;
                defs.add(new SimpleWorkflowEngine.StepDef(desc, kind));
            }
        } catch (Exception e) {
            log.warn("[workflow] 解析步骤 JSON 失败: {}", e.getMessage());
        }
        return defs;
    }

    private String buildResponse(SimpleWorkflow wf) {
        var resp = new org.noear.snack4.ONode();
        resp.set("workflowId", wf.getId());
        resp.set("title", wf.getTitle());
        resp.set("status", wf.getStatus());
        resp.set("currentStepIndex", wf.getCurrentStepIndex());
        resp.set("totalSteps", wf.getSteps().size());
        resp.set("progress", wf.progressText());

        var stepsArr = org.noear.snack4.ONode.ofJson("[]").asArray();
        resp.set("steps", stepsArr);
        for (var step : wf.getSteps()) {
            var s = stepsArr.addNew();
            s.set("id", step.getId());
            s.set("description", step.getDescription());
            s.set("kind", step.getKind().name());
            s.set("status", step.getStatus().name());
        }

        var current = wf.currentStep();
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
