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
 * 作业列表工具 —— 列出当前会话启动的所有后台作业。
 *
 * @author Sorghum
 */
@Component
public class ListJobsTool extends AgentTool {

    @Inject
    private JobService jobService;

    @Override
    public String getName() {
        return "list_jobs";
    }

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
