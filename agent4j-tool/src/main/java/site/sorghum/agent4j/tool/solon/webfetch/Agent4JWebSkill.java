package site.sorghum.agent4j.tool.solon.webfetch;

import org.checkerframework.checker.units.qual.C;
import org.noear.solon.ai.skills.web.CodeSearchTool;
import org.noear.solon.ai.skills.web.WebfetchTool;
import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.solon.SolonToTools;
import site.sorghum.agent4j.tool.solon.ToolManager;

import java.util.List;

@Component
public class Agent4JWebSkill implements SolonToTools {

    WebfetchTool webfetchTool = new WebfetchTool();

    CodeSearchTool codeSearchTool = new CodeSearchTool();

    @Override
    public List<AgentTool> getTools() {
        return ToolManager.getToolsFromTools(webfetchTool.getTools(), codeSearchTool.getTools());
    }
}
