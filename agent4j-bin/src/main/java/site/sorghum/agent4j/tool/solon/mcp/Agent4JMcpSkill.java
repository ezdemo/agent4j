package site.sorghum.agent4j.tool.solon.mcp;

import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.ai.talents.gateway.McpGatewayTalent;
import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.tool.solon.SolonToTools;

import java.util.Collection;

@Component
public class Agent4JMcpSkill extends McpGatewayTalent implements SolonToTools {
    public Agent4JMcpSkill() {

    }

    @Override
    public Collection<FunctionTool> getSolonTools() {
        return this.getTools(null);
    }

    @Override
    public String getSystemPrompt() {
        return "\n" + getInstruction(null);
    }
}
