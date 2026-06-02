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
                
                描述：替换正在执行中的计划的剩余步骤。当在计划执行过程中发现需要调整时使用。
                
                ## 使用指南
                
                1. **说明原因**：在 reason 中清楚说明为什么需要修订
                2. **提供新步骤**：用 remainingSteps 提供调整后的剩余步骤列表
                3. **与 submit_plan 的区别**：revise_plan 替换的是剩余步骤，
                   已完成的步骤不受影响
                
                参数：
                  - reason (string, 必填): 修订原因
                  - remainingSteps (array, 必填): 剩余步骤
                
                只读：否
                风暴豁免：否""";
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
