package site.sorghum.agent4j.tool.solon.common;

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
    public static PoolManager poolManager = new PoolManager() {{
        register("@skill", "~/.claude/skills");
        register("@superpowers-skill", "~/.agent4j/plugin/superpowers");
    }};

    public Agent4JSkillProvider(String workDir) {
        super(workDir, poolManager);
        this.sandboxMode(false);
    }

    public static Agent4JSkillProvider getOrCreate(String rootDir) {
        return cliSkillProviderMap.computeIfAbsent(rootDir, k -> new Agent4JSkillProvider(rootDir));
    }

    @Override
    public List<AgentTool> getTools() {
        return ToolManager.getToolsFromSKill(this.getSkills());
    }

    @Override
    public String getSystemPrompt() {
        return """
                ## 技能库执行规约
                ### 运行模式: 路径导航
                优先使用合适的技能解决问题（不确定用什么技能时，可通过 skillsearch 搜索）。注意：在执行任务中，请务必通过 skillread 读取或回顾规约。
                当前技能较多，仅展示路径索引（没有描述）。请推断功能并调用 skillread。如果不确定，请使用 skillsearch 检索
                """;
    }

}