package site.sorghum.agent4j.tool.solon.webfetch;

import org.noear.solon.ai.talents.web.CodeSearchTalent;
import org.noear.solon.ai.talents.web.WebfetchTalent;
import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.solon.SolonToTools;
import site.sorghum.agent4j.tool.solon.ToolManager;

import java.util.List;

@Component
public class Agent4JWebSkill implements SolonToTools {

    WebfetchTalent fetchTool = new WebfetchTalent();

    CodeSearchTalent codeSearchTool = new CodeSearchTalent();

    @Override
    public List<AgentTool> getTools() {
        return ToolManager.getToolsFromSKill(
                List.of(
                        fetchTool,
                        codeSearchTool
                )
        );
    }
}
