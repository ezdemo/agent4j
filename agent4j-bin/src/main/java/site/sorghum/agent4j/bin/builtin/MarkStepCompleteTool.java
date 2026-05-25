package site.sorghum.agent4j.bin.builtin;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.bin.service.PlanService;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
