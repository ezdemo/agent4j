package site.sorghum.agent4j.tool.solon.sys;

import org.noear.solon.ai.skills.file.ZipSkill;
import org.noear.solon.ai.skills.sys.NodejsSkill;
import org.noear.solon.ai.skills.sys.SystemClockSkill;
import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.solon.SolonToTools;
import site.sorghum.agent4j.tool.solon.ToolManager;

import java.util.ArrayList;
import java.util.List;

@Component
public class Agent4JSysSkill implements SolonToTools {

    SystemClockSkill systemClockSkill = new SystemClockSkill();

    @Override
    public List<AgentTool> getTools() {
        return new ArrayList<>(ToolManager.getToolsFromSKill(List.of(systemClockSkill)));
    }
}
