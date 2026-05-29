package site.sorghum.agent4j.bin.builtin;

import org.noear.solon.Solon;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.bin.skill.SkillV2;
import site.sorghum.agent4j.bin.skill.SkillStoreV2;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolParameter;
import site.sorghum.agent4j.tool.ToolResult;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * run_skill 工具 - 调用用户定义的 skill。
 * <p>
 * LLM 根据系统提示中的 skill 索引，自动选择合适的 skill 调用。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class RunSkillTool extends AgentTool {

    @Override
    public String getName() {
        return "run_skill";
    }

    @Override
    public String getDescription() {
        return "Invoke a user-defined playbook from the Skills index pinned in the system prompt. " +
                "Pass `name` as the bare skill identifier (e.g. 'my-custom-skill'). " +
                "Entries tagged `[🧬 subagent]` spawn an isolated subagent — only the final distilled answer comes back. " +
                "Plain skills are inlined: the body becomes a tool result you read and follow.";
    }

    @Override
    public String toToolSpec() {
        return "### run_skill\n\n" +
                "描述：调用用户定义的 skill playbook。从系统提示中的 Skills 索引选择合适的 skill。\n\n" +
                "## 使用指南\n\n" +
                "1. **查看索引**：系统提示中有所有可用 skill 的索引\n" +
                "2. **选择 skill**：根据任务描述选择最匹配的 skill\n" +
                "3. **传入参数**：将具体任务描述作为 arguments 传入\n\n" +
                "## Skill 类型\n\n" +
                "- **普通 skill**：正文直接插入上下文，你阅读后执行\n" +
                "- **[🧬 subagent] skill**：在隔离子代理中运行，只返回最终结果\n\n" +
                "参数：\n" +
                "  - name (string, 必填): skill 名称（如 'explore', 'my-skill'）\n" +
                "  - arguments (string, 可选): 具体任务描述\n\n" +
                "只读：是\n" +
                "风暴豁免：是";
    }

    @Override
    public List<ToolParameter> getParameters() {
        return Arrays.asList(
                new ToolParameter("name", "string", true,
                        "Skill identifier as it appears in the pinned Skills index (e.g. 'explore', 'review'). Case-sensitive."),
                new ToolParameter("arguments", "string", false,
                        "Free-form arguments the skill should act on. For subagent skills: REQUIRED — becomes the entire task description.")
        );
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public ToolResult execute(ToolContext ctx) {
        String rawName = ctx.getString("name");
        if (rawName == null || rawName.trim().isEmpty()) {
            return ToolResult.fail("MISSING_NAME", "run_skill requires a 'name' argument");
        }

        // 清理名称（移除可能的 [🧬 subagent] 标签）
        String name = cleanSkillName(rawName);
        if (name.isEmpty()) {
            return ToolResult.fail("INVALID_NAME", "run_skill requires a valid skill name");
        }

        // 查找 skill
        SkillV2 skill = Solon.context().getBean(SkillStoreV2.class).read(name);
        if (skill == null) {
            // 列出可用的 skill
            String available = Solon.context().getBean(SkillStoreV2.class).list().stream()
                    .map(SkillV2::getName)
                    .collect(Collectors.joining(", "));
            return ToolResult.fail("SKILL_NOT_FOUND",
                    "unknown skill: " + name + "\navailable: " + (available.isEmpty() ? "(none)" : available));
        }

        String arguments = ctx.getString("arguments");
        if (arguments == null) {
            arguments = "";
        }

        // 根据运行模式处理
        if (skill.getRunAs() == SkillV2.RunAs.SUBAGENT) {
            // TODO: 实现 subagent 模式
            // 目前暂时以 inline 模式处理
            return ToolResult.ok(skill.toFullContent(arguments));
        } else {
            // inline 模式：返回完整内容
            return ToolResult.ok(skill.toFullContent(arguments));
        }
    }

    /**
     * 清理 skill 名称（移除 [xxx] 标签）。
     */
    private String cleanSkillName(String raw) {
        // 移除 [xxx] 标签
        String stripped = raw.replaceAll("\\[[^\\]]*\\]", " ").trim();
        // 获取第一个字母数字开头的 token
        String[] tokens = stripped.split("\\s+");
        for (String token : tokens) {
            if (token.matches("^[a-zA-Z0-9].*")) {
                return token;
            }
        }
        return "";
    }
}