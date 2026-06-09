package site.sorghum.agent4j.tool.solon.common;

import lombok.Getter;
import org.noear.solon.ai.talents.cli.SkillTalent;
import org.noear.solon.ai.talents.cli.TerminalTalent;
import org.noear.solon.ai.talents.cli.TerminalTalentProxy;
import org.noear.solon.ai.talents.mount.MountDir;
import org.noear.solon.ai.talents.mount.MountManager;
import org.noear.solon.ai.talents.mount.MountType;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.solon.SolonToTools;
import site.sorghum.agent4j.tool.solon.ToolManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Agent4JSkillProvider implements SolonToTools {
    SkillTalent skillTalent;
    TerminalTalent terminalTalent;
    public static Map<String, Agent4JSkillProvider> cliSkillProviderMap = new ConcurrentHashMap<>();
    @Getter
    public MountManager poolManager;

    public Agent4JSkillProvider(String workDir) {
        poolManager = new MountManager(workDir) {{
            register(MountDir.builder()
                    .type(MountType.SKILLS)
                    .alias("@claude-skills")
                    .path("~/.claude/skills")
                    .build());
            register(MountDir.builder()
                    .type(MountType.SKILLS)
                    .alias("@agent4j-skills")
                    .path("~/.agent4j/skills")
                    .build());
            register(MountDir.builder()
                    .type(MountType.SKILLS)
                    .alias("@superpowers-skill")
                    .path("~/.agent4j/plugin/superpowers")
                    .build());
        }};
        skillTalent = new SkillTalent(poolManager);
        terminalTalent = new TerminalTalent(poolManager);
        terminalTalent.setSandboxMode(false);
        terminalTalent.setBashAsyncEnabled(true);
    }

    public static Agent4JSkillProvider getOrCreate(String rootDir) {
        return cliSkillProviderMap.computeIfAbsent(rootDir, k -> new Agent4JSkillProvider(rootDir));
    }

    @Override
    public List<AgentTool> getTools() {
        return ToolManager.getToolsFromSKill(List.of(
                skillTalent,
                terminalTalent
        ));
    }

    @Override
    public String getSystemPrompt() {
        return """
                ## 技能库执行规约
                ### 运行模式: 路径导航
                优先使用合适的技能解决问题（不确定用什么技能时，可通过 skillsearch 搜索）。
                注意：在执行任务中，请务必通过 skillread 读取或回顾规约。
                当前技能较多，仅展示路径索引（没有描述）。请推断功能并调用 skillread。
                如果不确定，请使用 skillsearch 检索
                """;
    }

}