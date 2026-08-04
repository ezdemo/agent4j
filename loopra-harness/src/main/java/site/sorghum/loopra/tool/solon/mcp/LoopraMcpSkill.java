package site.sorghum.loopra.tool.solon.mcp;

import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.ai.talents.gateway.McpGatewayTalent;
import org.noear.solon.annotation.Component;
import site.sorghum.loopra.tool.SolonToTools;

import java.util.Collection;

@Component
public class LoopraMcpSkill extends McpGatewayTalent implements SolonToTools {
    public LoopraMcpSkill() {

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
