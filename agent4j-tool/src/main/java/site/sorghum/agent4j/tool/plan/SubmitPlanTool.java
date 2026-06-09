package site.sorghum.agent4j.tool.plan;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.util.List;
import java.util.Map;

/**
 * 计划提交工具 —— 提交一份计划供用户审查。
 *
 * @author Sorghum
 */
@Component
public class SubmitPlanTool extends AgentTool {

    @Inject
    private PlanService planService;

    @Override
    public String getName() {
        return "submit_plan";
    }

    @Override
    public String getDescription() {
        return "Submit one concrete plan for review.";
    }

    @Override
    public String toToolSpec() {
        return """
                ### submit_plan
                
                描述：提交执行计划供用户审查（计划模式下使用）。先用只读工具探索，再提交计划。
                参数: summary(可选), plan(必填，Markdown), steps(必填，[{id,title,action}])。只读。
                """;
    }

    @Override
    public boolean isReadOnly() {
        return true; // 计划模式下可用——submit_plan 是计划模式的核心工具
    }

    @Override
    public List<ToolParameter> getParameters() {
        return List.of(
                new ToolParameter("summary", "string", false, "计划标题"),
                new ToolParameter("plan", "string", true, "Markdown 计划内容"),
                new ToolParameter("steps", "array", true, "步骤列表")
        );
    }

    @Override
    public ToolResult execute(ToolContext ctx) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) ctx.getParams().get("steps");
        return ToolResult.ok(planService.submitPlan(ctx.getString("summary"),
                ctx.getString("plan"), steps));
    }
}
