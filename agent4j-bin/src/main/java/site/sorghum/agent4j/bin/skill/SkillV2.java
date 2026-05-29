package site.sorghum.agent4j.bin.skill;

import java.util.List;
import java.util.Set;

/**
 * Skill V2 - Claude Code 风格的 Skill 定义。
 * <p>
 * 使用 Markdown + YAML frontmatter 格式。
 * </p>
 *
 * @author Sorghum
 */
public interface SkillV2 {

    /** Skill 运行模式 */
    enum RunAs {
        /** 直接插入上下文 */
        INLINE,
        /** 隔离子代理运行 */
        SUBAGENT
    }

    /** Skill 作用域 */
    enum Scope {
        /** 项目级 */
        PROJECT,
        /** 自定义路径 */
        CUSTOM,
        /** 全局级 */
        GLOBAL,
        /** 内置 */
        BUILTIN
    }

    /** 获取 skill 名称 */
    String getName();

    /** 获取描述（一行简短描述） */
    String getDescription();

    /** 获取完整的 Markdown 正文 */
    String getBody();

    /** 获取作用域 */
    Scope getScope();

    /** 获取文件路径 */
    String getPath();

    /** 获取运行模式 */
    RunAs getRunAs();

    /** 获取允许的工具列表（仅 subagent 模式有效） */
    List<String> getAllowedTools();

    /** 获取模型覆盖（仅 subagent 模式有效） */
    String getModel();

    /**
     * 生成索引行（用于系统提示中的 skill 列表）。
     * 格式：- skill-name [🧬 subagent] — 描述
     */
    default String toIndexLine() {
        String tag = getRunAs() == RunAs.SUBAGENT ? " [🧬 subagent]" : "";
        String desc = getDescription() != null ? getDescription() : "(no description)";
        if (desc.length() > 100) {
            desc = desc.substring(0, 97) + "...";
        }
        return "- " + getName() + tag + " — " + desc;
    }

    /**
     * 生成完整内容（用于 inline 模式插入上下文）。
     */
    default String toFullContent(String arguments) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Skill: ").append(getName()).append("\n");
        if (getDescription() != null && !getDescription().isEmpty()) {
            sb.append("> ").append(getDescription()).append("\n");
        }
        sb.append("(scope: ").append(getScope()).append(" · ").append(getPath()).append(")\n");
        sb.append("\n").append(getBody());
        if (arguments != null && !arguments.isEmpty()) {
            sb.append("\n\nArguments: ").append(arguments);
        }
        return sb.toString();
    }
}