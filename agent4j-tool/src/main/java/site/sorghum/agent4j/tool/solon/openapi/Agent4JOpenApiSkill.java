package site.sorghum.agent4j.tool.solon.openapi;

import org.noear.solon.ai.skills.openapi.OpenApiSkill;
import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.solon.SolonToTools;
import site.sorghum.agent4j.tool.solon.ToolManager;

import java.util.List;

@Component
public class Agent4JOpenApiSkill extends OpenApiSkill implements SolonToTools {

    @Override
    public List<AgentTool> getTools() {
        return ToolManager.getTools(List.of(this));
    }
}
