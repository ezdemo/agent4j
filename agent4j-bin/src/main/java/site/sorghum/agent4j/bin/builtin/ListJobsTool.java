package site.sorghum.agent4j.bin.builtin;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.bin.service.JobService;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.util.Collections;
import java.util.List;

@Component
public class ListJobsTool extends AgentTool {

    @Inject
    private JobService jobService;

    @Override
    public String getName() { return "list_jobs"; }

    @Override
    public String getDescription() {
        return "List every background job started this session.";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Collections.emptyList();
    }

    @Override
    public ToolResult execute(ToolContext ctx) {
        return ToolResult.ok(jobService.listJobs());
    }
}
