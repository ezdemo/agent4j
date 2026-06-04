package site.sorghum.agent4j.tool.solon.common;

import org.noear.solon.ai.chat.skill.Skill;
import org.noear.solon.ai.skills.cli.CliSkillProvider;
import org.noear.solon.ai.skills.cli.PoolManager;
import org.noear.solon.ai.skills.cli.TerminalSkill;
import org.noear.solon.ai.skills.file.ZipSkill;
import org.noear.solon.ai.skills.pdf.PdfSkill;
import org.noear.solon.ai.skills.sys.NodejsSkill;
import org.noear.solon.ai.skills.sys.PythonSkill;
import org.noear.solon.ai.skills.sys.ShellSkill;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.solon.SolonToTools;
import site.sorghum.agent4j.tool.solon.ToolManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class Agent4JSkillProvider extends CliSkillProvider implements SolonToTools {

    public static Map<String, NodejsSkill> nodejsSkillMap = new ConcurrentHashMap<>();
    public static Map<String, PdfSkill> pdfSkillMap = new ConcurrentHashMap<>();
    public static Map<String, PythonSkill> pythonSkillMap = new ConcurrentHashMap<>();
    public static Map<String, ZipSkill> zipSkillMap = new ConcurrentHashMap<>();

    public static Map<String, Agent4JSkillProvider> cliSkillProviderMap = new ConcurrentHashMap<>();
    PdfSkill pdfSkill;
    NodejsSkill nodejsSkill;
    PythonSkill pythonSkill;
    ZipSkill zipSkill;
    public static PoolManager poolManager = new PoolManager() {{
        register("@skill", "~/.claude/skills");
    }};

    public Agent4JSkillProvider(String workDir) {
        super(workDir, poolManager);
        pdfSkill = pdfSkillMap.computeIfAbsent(workDir, k -> new PdfSkill(workDir));
        nodejsSkill = nodejsSkillMap.computeIfAbsent(workDir, k -> new NodejsSkill(workDir));
        pythonSkill = pythonSkillMap.computeIfAbsent(workDir, k -> new PythonSkill(workDir));
        zipSkill = zipSkillMap.computeIfAbsent(workDir, k -> new ZipSkill(workDir));
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