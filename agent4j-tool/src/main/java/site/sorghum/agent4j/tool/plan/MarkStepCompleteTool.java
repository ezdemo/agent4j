package site.sorghum.agent4j.tool.plan;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 步骤完成标记工具 —— 标记已审批计划中的一步为完成。
 *
 * @author Sorghum
 */
@Component
public class MarkStepCompleteTool extends AgentTool {

    @Inject
    private PlanService planService;

    @Override
    public String getName() { return "mark_step_complete"; }

    @Override
    public String getDescription() {
        return "Mark one approved-plan step as done.";
    }

    @Override
    public String toToolSpec() {
        return "### mark_step_complete\n\n"
                + "描述：标记已审批计划中的一个步骤为已完成。跟踪计划执行的进度。\n\n"
                + "## 使用指南\n\n"
                + "1. **标记完成**：执行完计划中的某一步后，用此工具标记完成\n"
                + "2. **提供结果**：在 result 中描述该步骤的执行结果\n"
                + "3. **提供依据**：在 evidence 中提供验证依据（如文件路径、测试结果等）\n\n"
                + "参数：\n"
                + "  - stepId (string, 必填): Step id\n"
                + "  - result (string, 可选): 结果描述\n"
                + "  - evidence (array, 可选): 验证依据\n\n"
                + "只读：否\n"
                + "风暴豁免：否";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Arrays.asList(
                new ToolParameter("stepId", "string", true, "Step id"),
                new ToolParameter("result", "string", false, "结果描述"),
                new ToolParameter("evidence", "array", false, "验证依据")
        );
    }

    @Override
    public ToolResult execute(ToolContext ctx) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> evidence = (List<Map<String, Object>>) ctx.getParams().get("evidence");
        return ToolResult.ok(planService.markStepComplete(ctx.getString("stepId"),
                ctx.getString("result"), evidence));
    }
}
