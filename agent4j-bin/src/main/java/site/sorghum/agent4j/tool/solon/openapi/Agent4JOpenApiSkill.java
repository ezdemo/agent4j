package site.sorghum.agent4j.tool.solon.openapi;

import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.ai.talents.gateway.OpenApiGatewayTalent;
import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.tool.solon.SolonToTools;

import java.util.Collection;

@Component
public class Agent4JOpenApiSkill extends OpenApiGatewayTalent implements SolonToTools {


    @Override
    public Collection<FunctionTool> getSolonTools() {
        return this.getTools(null);
    }
}
