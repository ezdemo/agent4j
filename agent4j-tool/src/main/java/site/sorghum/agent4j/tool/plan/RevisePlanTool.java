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
 * 计划修订工具 —— 替换进行中计划的剩余步骤。
 *
 * @author Sorghum
 */
@Component
public class RevisePlanTool extends AgentTool {

    @Inject
    private PlanService planService;

    @Override
    public String getName() {
        return "revise_plan";
    }

    @Override
    public String getDescription() {
        return "Replace the remaining steps of an in-flight plan.";
    }

    @Override
    public String toToolSpec() {
        return """
                ### revise_plan
                
                描述：替换执行中计划的剩余步骤（发现需要调整时使用）。已完成的步骤不受影响。
                参数: reason(必填), remainingSteps(必填)。可写。
                """;
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Arrays.asList(
                new ToolParameter("reason", "string", true, "修订原因"),
                new ToolParameter("remainingSteps", "array", true, "剩余步骤")
        );
    }

    @Override
    public ToolResult execute(ToolContext ctx) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> remainingSteps = (List<Map<String, Object>>) ctx.getParams()
                .get("remainingSteps");
        return ToolResult.ok(planService.revisePlan(ctx.getString("reason"), remainingSteps));
    }
}
