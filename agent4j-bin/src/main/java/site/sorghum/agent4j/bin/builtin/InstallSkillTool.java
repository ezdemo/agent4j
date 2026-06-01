package site.sorghum.agent4j.bin.builtin;

import org.noear.solon.Solon;
import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.bin.skill.SkillStoreV2;
import site.sorghum.agent4j.bin.skill.SkillV2;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * install_skill 工具 - 创建新的 skill。
 * <p>
 * LLM 可以在运行时创建新的 skill，立即可用。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class InstallSkillTool extends AgentTool {

    private static final Pattern VALID_SKILL_NAME = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$");

    @Override
    public String getName() {
        return "install_skill";
    }

    @Override
    public String getDescription() {
        return "Author and save a new skill — a reusable playbook future turns invoke via `run_skill`. " +
                "Runnable immediately (same turn); appears in the pinned Skills index on next `/new` or launch.";
    }

    @Override
    public String toToolSpec() {
        return "### install_skill\n\n" +
                "描述：创建并保存新的 skill，供后续会话通过 run_skill 调用。\n\n" +
                "## 使用指南\n\n" +
                "1. **命名规范**：字母、数字、_、-、.，1-64 字符，字母开头\n" +
                "2. **描述必须**：简短描述会出现在 skill 索引中\n" +
                "3. **正文格式**：Markdown 格式的指令\n" +
                "4. **运行模式**：\n" +
                "   - inline（默认）：正文直接插入上下文\n" +
                "   - subagent：隔离子代理运行，只返回结果\n\n" +
                "参数：\n" +
                "  - name (string, 必填): skill 名称\n" +
                "  - description (string, 必填): 一句话描述\n" +
                "  - body (string, 必填): Markdown 格式的指令正文\n" +
                "  - scope (string, 可选): 'project' 或 'global'\n" +
                "  - runAs (string, 可选): 'inline' 或 'subagent'\n\n" +
                "只读：否\n" +
                "风暴豁免：否";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Arrays.asList(
                new ToolParameter("name", "string", true,
                        "Identifier — letters/digits/_/-/., 1-64 chars, starts alnum. Becomes the filename."),
                new ToolParameter("description", "string", true,
                        "≤120 char one-liner shown in the pinned Skills index."),
                new ToolParameter("body", "string", true,
                        "Markdown playbook. For subagent skills, write the subagent's persona/rules."),
                new ToolParameter("scope", "string", false,
                        "'project' (default) writes to <repo>/.agent4j/skills/; 'global' writes to ~/.agent4j/skills/."),
                new ToolParameter("runAs", "string", false,
                        "'inline' (default) appends body to parent log. 'subagent' spawns isolated child loop.")
        );
    }

    @Override
    public ToolResult execute(ToolContext ctx) {
        String name = ctx.getString("name");
        String description = ctx.getString("description");
        String body = ctx.getString("body");
        String scopeStr = ctx.getString("scope");
        String runAsStr = ctx.getString("runAs");

        // 验证参数
        if (name == null || name.trim().isEmpty()) {
            return ToolResult.fail("MISSING_NAME", "install_skill requires a non-empty 'name'");
        }
        name = name.trim();

        if (!VALID_SKILL_NAME.matcher(name).matches()) {
            return ToolResult.fail("INVALID_NAME",
                    "invalid skill name: \"" + name + "\" — use letters, digits, _, -, .");
        }

        if (description == null || description.trim().isEmpty()) {
            return ToolResult.fail("MISSING_DESCRIPTION",
                    "install_skill requires a non-empty 'description'");
        }
        description = description.replaceAll("[\r\n]+", " ").trim();

        if (body == null || body.trim().isEmpty()) {
            return ToolResult.fail("MISSING_BODY",
                    "install_skill requires a non-empty 'body'");
        }

        // 确定作用域
        boolean hasProjectScope = Solon.context().getBean(SkillStoreV2.class).getRoots().stream()
                .anyMatch(r -> r.scope() == SkillV2.Scope.PROJECT);

        boolean wantsGlobal = "global".equalsIgnoreCase(scopeStr);
        if (!wantsGlobal && !hasProjectScope) {
            wantsGlobal = true; // 没有项目目录时默认全局
        }

        // 确定目标目录
        Path targetDir;
        if (wantsGlobal) {
            targetDir = Paths.get(System.getProperty("user.home"), ".agent4j", "skills");
        } else {
            // 使用第一个项目级目录
            targetDir = Solon.context().getBean(SkillStoreV2.class).getRoots().stream()
                    .filter(r -> r.scope() == SkillV2.Scope.PROJECT)
                    .map(r -> r.dir())
                    .findFirst()
                    .orElse(Paths.get(System.getProperty("user.home"), ".agent4j", "skills"));
        }

        // 构建文件内容
        StringBuilder content = new StringBuilder();
        content.append("---\n");
        content.append("name: ").append(name).append("\n");
        content.append("description: ").append(description).append("\n");

        if ("subagent".equalsIgnoreCase(runAsStr)) {
            content.append("runAs: subagent\n");
        }

        content.append("---\n\n");
        content.append(body.trim()).append("\n");

        // 写入文件
        try {
            Files.createDirectories(targetDir);
            Path targetFile = targetDir.resolve(name + ".md");

            if (Files.exists(targetFile)) {
                return ToolResult.fail("ALREADY_EXISTS",
                        "skill \"" + name + "\" already exists at " + targetFile);
            }

            Files.write(targetFile, content.toString().getBytes(StandardCharsets.UTF_8));

            return ToolResult.ok("✅ Skill \"" + name + "\" created at " + targetFile + "\n" +
                    "It will appear in the Skills index on next `/new` or launch.");

        } catch (IOException e) {
            return ToolResult.fail("IO_ERROR", "Failed to create skill: " + e.getMessage());
        }
    }
}