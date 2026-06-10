package site.sorghum.agent4j.tool.job;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.util.List;

/**
 * 作业等待工具 —— 阻塞等待后台作业完成。
 *
 * @author Sorghum
 */
@Component
public class WaitForJobTool extends AgentTool {

    @Inject
    private JobService jobService;

    @Override
    public String getName() {
        return "wait_for_job";
    }

    @Override
    public String getDescription() {
        return "Block until a background job finishes. Returns {exited, exitCode, latestOutput}.";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return List.of(
                new ToolParameter("jobId", "int", true, "Job id"),
                new ToolParameter("timeoutMs", "int", false, "Max wait in ms (default 5000)"),
                new ToolParameter("waitFor", "string", false, "'exit' or 'output-or-exit' (default 'exit')")
        );
    }

    @Override
    public ToolResult execute(ToolContext ctx) {
        try {
            Integer timeoutMs = ctx.has("timeoutMs") ? ctx.getInt("timeoutMs", 0) : null;
            return ToolResult.ok(jobService.waitForJob(ctx.getInt("jobId", 0), timeoutMs, ctx.getString("waitFor")));
        } catch (InterruptedException e) {
            return ToolResult.ok("[INTERRUPTED]");
        }
    }
}
