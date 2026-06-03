package site.sorghum.agent4j.tool.solon.common;

import org.noear.solon.ai.chat.skill.Skill;
import org.noear.solon.ai.skills.cli.CliSkillProvider;
import org.noear.solon.ai.skills.cli.PoolManager;
import org.noear.solon.ai.skills.cli.TerminalSkill;
import org.noear.solon.ai.skills.pdf.PdfSkill;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.solon.SolonToTools;
import site.sorghum.agent4j.tool.solon.ToolManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class Agent4JSkillProvider extends CliSkillProvider implements SolonToTools {

    public static Map<String, PdfSkill> pdfSkillMap = new ConcurrentHashMap<>();

    public static Map<String, Agent4JSkillProvider> cliSkillProviderMap = new ConcurrentHashMap<>();
    PdfSkill pdfSkill;

    public static PoolManager poolManager = new PoolManager(){{
        register("@skill","~/.claude/skills");
    }};

    public Agent4JSkillProvider(String workDir) {
        super(workDir, poolManager);
        pdfSkill = pdfSkillMap.computeIfAbsent(workDir, k -> new PdfSkill(workDir));
    }

    public static Agent4JSkillProvider getOrCreate(String rootDir) {
        return cliSkillProviderMap.computeIfAbsent(rootDir, k -> new Agent4JSkillProvider(rootDir));
    }

    @Override
    public List<AgentTool> getTools(){
        return ToolManager.getTools(this.getSkills());
    }

    @Override
    public String getSystemPrompt() {
        ArrayList<Skill> skills = new ArrayList<>(this.getSkills());
        skills.add(pdfSkill);
        return skills.stream().filter(it -> !(it instanceof TerminalSkill)).map(
                it -> it.getInstruction(null)
        ).collect(Collectors.joining("\n"));
    }

}