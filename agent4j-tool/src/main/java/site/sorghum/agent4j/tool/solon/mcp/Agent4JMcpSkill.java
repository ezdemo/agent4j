package site.sorghum.agent4j.tool.solon.mcp;

import org.noear.solon.ai.skills.toolgateway.McpGatewaySkill;
import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.solon.SolonToTools;
import site.sorghum.agent4j.tool.solon.ToolManager;

import java.util.List;

@Component
public class Agent4JMcpSkill extends McpGatewaySkill implements SolonToTools {

    @Override
    public List<AgentTool> getTools() {
        return ToolManager.getToolsFromSKill(List.of(this));
    }
}
