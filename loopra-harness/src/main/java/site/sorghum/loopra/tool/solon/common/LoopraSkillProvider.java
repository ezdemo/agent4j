package site.sorghum.loopra.tool.solon.common;

import lombok.Getter;
import lombok.SneakyThrows;
import org.noear.solon.Solon;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.ai.talents.cli.SkillTalent;
import org.noear.solon.ai.talents.lsp.LspManager;
import org.noear.solon.ai.talents.lsp.LspTalent;
import org.noear.solon.ai.talents.mount.MountDir;
import org.noear.solon.ai.talents.mount.MountManager;
import org.noear.solon.ai.talents.mount.MountType;
import site.sorghum.loopra.tool.SolonToTools;
import site.sorghum.loopra.tool.solon.lsp.SharedLoopraLspSkill;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class LoopraSkillProvider implements SolonToTools {
    SkillTalent skillTalent;
    SessionTerminalTalent terminalTalent;
    @Getter
    LspTalent lspTalent;
    public static Map<String, LoopraSkillProvider> cliSkillProviderMap = new ConcurrentHashMap<>();
    @Getter
    public MountManager poolManager;

    @SneakyThrows
    public LoopraSkillProvider(String workDir) {
        Files.createDirectories(Paths.get(System.getProperty("user.home"), ".loopra", "skills"));
        poolManager = new MountManager(workDir) {{
            register(MountDir.builder()
                    .type(MountType.SKILLS)
                    .alias("@loopra-skills")
                    .path("~/.loopra/skills")
                    .build());
        }};
        skillTalent = new SkillTalent(poolManager);
        terminalTalent = new SessionTerminalTalent(poolManager, workDir);
        terminalTalent.setSandboxEnabled(false);
        terminalTalent.setBashAsyncEnabled(true);
        lspTalent = new LspTalent(
                new LspManager(workDir), workDir
        );
        SharedLoopraLspSkill share = Solon.context().getBean(SharedLoopraLspSkill.class);
        share.copyToLoopra(lspTalent);
    }

    public static LoopraSkillProvider getOrCreate(String rootDir) {
        return cliSkillProviderMap.computeIfAbsent(rootDir, k -> new LoopraSkillProvider(rootDir));
    }

    /** Refresh every active skill pool after the installed skill directories change. */
    public static void refreshAllSkillPools() {
        cliSkillProviderMap.values().forEach(provider -> provider.poolManager.refresh());
    }


    @Override
    public Collection<FunctionTool> getSolonTools() {
        return Stream.of(
                skillTalent,
                terminalTalent,
                lspTalent
        ).map(
                talent -> talent.getTools(null)
        ).filter(
                Objects::nonNull
        ).flatMap(Collection::stream).filter(
                Objects::nonNull
        ).toList();
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
