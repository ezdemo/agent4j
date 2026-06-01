package site.sorghum.agent4j.tool.job;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.util.Collections;
import java.util.List;

/**
 * 作业停止工具 —— 停止后台作业进程。
 *
 * @author Sorghum
 */
@Component
public class StopJobTool extends AgentTool {

    @Inject
    private JobService jobService;

    @Override
    public String getName() {
        return "stop_job";
    }

    @Override
    public String getDescription() {
        return "Stop a background job. SIGTERM first, SIGKILL after grace period.";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Collections.singletonList(
                new ToolParameter("jobId", "int", true, "Job id")
        );
    }

    @Override
    public ToolResult execute(ToolContext ctx) {
        return ToolResult.ok(jobService.stopJob(ctx.getInt("jobId", 0)));
    }
}
