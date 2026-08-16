package site.sorghum.loopra.tool.solon.openapi;

import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.ai.talents.gateway.OpenApiGatewayTalent;
import org.noear.solon.annotation.Component;
import site.sorghum.loopra.tool.SolonToTools;

import java.util.Collection;

/** 将 Solon OpenAPI 网关能力暴露为 Loopra 可调用的工具集合。 */
@Component
public class LoopraOpenApiSkill extends OpenApiGatewayTalent implements SolonToTools {


    @Override
    public Collection<FunctionTool> getSolonTools() {
        return this.getTools(null);
    }
}
