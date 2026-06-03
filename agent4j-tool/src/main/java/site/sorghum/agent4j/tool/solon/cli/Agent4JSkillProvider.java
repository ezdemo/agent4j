package site.sorghum.agent4j.tool.solon.cli;

import org.noear.solon.ai.skills.cli.CliSkillProvider;
import org.noear.solon.ai.skills.cli.PoolManager;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.solon.SolonToTools;
import site.sorghum.agent4j.tool.solon.ToolManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Agent4JSkillProvider extends CliSkillProvider implements SolonToTools {


    public static Map<String, Agent4JSkillProvider> cliSkillProviderMap = new ConcurrentHashMap<>();

    public static PoolManager poolManager = new PoolManager(){{
        register("@skill","~/.claude/skills");
    }};

    public Agent4JSkillProvider(String workDir) {
        super(workDir, poolManager);
    }

    public static Agent4JSkillProvider getOrCreate(String rootDir) {
        return cliSkillProviderMap.computeIfAbsent(rootDir, k -> new Agent4JSkillProvider(rootDir));
    }

    @Override
    public List<AgentTool> getTools(){
        return ToolManager.getTools(this.getSkills());
    }
}