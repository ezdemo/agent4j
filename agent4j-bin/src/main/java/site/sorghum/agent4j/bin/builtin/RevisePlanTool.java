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

/**
 * 计划修订工具 —— 替换进行中计划的剩余步骤。
 * <p>
 * 当计划需要调整时使用，保留已完成的步骤，
 * 替换剩余步骤为新的方案。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class RevisePlanTool extends AgentTool {

    @Inject
    private PlanService planService;

    @Override
    public String getName() { return "revise_plan"; }

    @Override
    public String getDescription() {
        return "Replace the remaining steps of an in-flight plan.";
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
