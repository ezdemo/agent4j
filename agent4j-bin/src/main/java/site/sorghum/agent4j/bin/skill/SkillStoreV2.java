package site.sorghum.agent4j.bin.skill;

import org.noear.solon.annotation.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skill Store V2 - 管理 Claude Code 风格的 Skill。
 * <p>
 * 支持多级目录、YAML frontmatter 解析、自动选择机制。
 * </p>
 *
 * @author Sorghum
 */
public class SkillStoreV2 {

    private static final String SKILLS_DIRNAME = "skills";
    private static final String SKILL_FILE = "SKILL.md";
    private static final int SKILLS_INDEX_MAX_CHARS = 4000;
    private static final Pattern VALID_SKILL_NAME = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$");
    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile("^---\\s*\n(.*?)\n---\\s*\n(.*)", Pattern.DOTALL);
    private static final Pattern FRONTMATTER_FIELD = Pattern.compile("^(\\S+)\\s*:\\s*(.*)$");

    private final Path projectRoot;
    private final Path homeDir;
    private final List<Path> customSkillPaths;

    public SkillStoreV2(Path projectRoot, Path homeDir, List<Path> customSkillPaths) {
        this.projectRoot = projectRoot != null ? projectRoot.toAbsolutePath() : null;
        this.homeDir = homeDir != null ? homeDir : Paths.get(System.getProperty("user.home"));
        this.customSkillPaths = customSkillPaths != null ? customSkillPaths : Collections.emptyList();
    }

    /**
     * 获取所有 skill 根目录（按优先级排序）。
     */
    public List<SkillRoot> getRoots() {
        List<SkillRoot> roots = new ArrayList<>();
        int priority = 0;

        // 项目级目录（优先级最高）
        if (projectRoot != null) {
            roots.add(new SkillRoot(projectRoot.resolve(".agent4j").resolve(SKILLS_DIRNAME),
                    SkillV2.Scope.PROJECT, priority++));
            roots.add(new SkillRoot(projectRoot.resolve(".agents").resolve(SKILLS_DIRNAME),
                    SkillV2.Scope.PROJECT, priority++));
            roots.add(new SkillRoot(projectRoot.resolve(".claude").resolve(SKILLS_DIRNAME),
                    SkillV2.Scope.PROJECT, priority++));
        }

        // 自定义路径
        for (Path path : customSkillPaths) {
            roots.add(new SkillRoot(path, SkillV2.Scope.CUSTOM, priority++));
        }

        // 全局目录
        roots.add(new SkillRoot(homeDir.resolve(".agent4j").resolve(SKILLS_DIRNAME),
                SkillV2.Scope.GLOBAL, priority++));
        roots.add(new SkillRoot(homeDir.resolve(".agents").resolve(SKILLS_DIRNAME),
                SkillV2.Scope.GLOBAL, priority++));
        roots.add(new SkillRoot(homeDir.resolve(".claude").resolve(SKILLS_DIRNAME),
                SkillV2.Scope.GLOBAL, priority++));

        return roots;
    }

    /**
     * 列出所有可用的 skill。
     */
    public List<SkillV2> list() {
        Map<String, SkillV2> byName = new LinkedHashMap<>();

        for (SkillRoot root : getRoots()) {
            if (!Files.exists(root.dir) || !Files.isDirectory(root.dir)) {
                continue;
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(root.dir)) {
                for (Path entry : stream) {
                    SkillV2 skill = readEntry(entry, root.scope);
                    if (skill != null && !byName.containsKey(skill.getName())) {
                        byName.put(skill.getName(), skill);
                    }
                }
            } catch (IOException e) {
                // 忽略无法读取的目录
            }
        }

        // 按名称排序
        List<SkillV2> skills = new ArrayList<>(byName.values());
        skills.sort(Comparator.comparing(SkillV2::getName));
        return skills;
    }

    /**
     * 根据名称读取 skill。
     */
    public SkillV2 read(String name) {
        if (!isValidSkillName(name)) {
            return null;
        }

        for (SkillRoot root : getRoots()) {
            if (!Files.exists(root.dir) || !Files.isDirectory(root.dir)) {
                continue;
            }

            // 尝试目录格式: skills/name/SKILL.md
            Path dirCandidate = root.dir.resolve(name).resolve(SKILL_FILE);
            if (Files.exists(dirCandidate) && Files.isRegularFile(dirCandidate)) {
                return parse(dirCandidate, name, root.scope);
            }

            // 尝试文件格式: skills/name.md
            Path fileCandidate = root.dir.resolve(name + ".md");
            if (Files.exists(fileCandidate) && Files.isRegularFile(fileCandidate)) {
                return parse(fileCandidate, name, root.scope);
            }
        }

        return null;
    }

    /**
     * 读取目录条目。
     */
    private SkillV2 readEntry(Path entry, SkillV2.Scope scope) {
        if (Files.isDirectory(entry)) {
            String dirName = entry.getFileName().toString();
            if (!isValidSkillName(dirName)) {
                return null;
            }
            Path skillFile = entry.resolve(SKILL_FILE);
            if (Files.exists(skillFile) && Files.isRegularFile(skillFile)) {
                return parse(skillFile, dirName, scope);
            }
        } else if (Files.isRegularFile(entry)) {
            String fileName = entry.getFileName().toString();
            if (fileName.endsWith(".md")) {
                String stem = fileName.substring(0, fileName.length() - 3);
                if (isValidSkillName(stem)) {
                    return parse(entry, stem, scope);
                }
            }
        }
        return null;
    }

    /**
     * 解析 skill 文件。
     */
    private SkillV2 parse(Path path, String stem, SkillV2.Scope scope) {
        try {
            String raw = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            return parseContent(raw, stem, scope, path.toString());
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 解析 skill 内容。
     */
    private SkillV2 parseContent(String raw, String stem, SkillV2.Scope scope, String path) {
        Matcher matcher = FRONTMATTER_PATTERN.matcher(raw);
        if (!matcher.matches()) {
            // 没有 frontmatter，整个内容作为 body
            return new SkillV2Impl(stem, "", raw.trim(), scope, path,
                    SkillV2.RunAs.INLINE, null, null);
        }

        String frontmatter = matcher.group(1);
        String body = matcher.group(2).trim();

        // 解析 frontmatter 字段
        Map<String, String> fields = parseFrontmatter(frontmatter);

        String name = fields.get("name");
        if (name == null || !isValidSkillName(name)) {
            name = stem;
        }

        String description = fields.get("description");
        if (description == null) {
            description = "";
        }

        String runAsStr = fields.get("runAs");
        SkillV2.RunAs runAs = "subagent".equals(runAsStr) ? SkillV2.RunAs.SUBAGENT : SkillV2.RunAs.INLINE;

        String allowedToolsStr = fields.get("allowed-tools");
        List<String> allowedTools = parseAllowedTools(allowedToolsStr);

        String model = fields.get("model");
        if (model != null && !model.startsWith("deepseek-")) {
            model = null;
        }

        return new SkillV2Impl(name, description, body, scope, path, runAs, allowedTools, model);
    }

    /**
     * 解析 frontmatter 字段。
     */
    private Map<String, String> parseFrontmatter(String frontmatter) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String line : frontmatter.split("\n")) {
            Matcher matcher = FRONTMATTER_FIELD.matcher(line.trim());
            if (matcher.matches()) {
                String key = matcher.group(1).trim();
                String value = matcher.group(2).trim();
                fields.put(key, value);
            }
        }
        return fields;
    }

    /**
     * 解析 allowed-tools 字段。
     */
    private List<String> parseAllowedTools(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String[] parts = raw.split(",");
        List<String> tools = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                tools.add(trimmed);
            }
        }
        return tools.isEmpty() ? null : Collections.unmodifiableList(tools);
    }

    /**
     * 验证 skill 名称是否合法。
     */
    private boolean isValidSkillName(String name) {
        return name != null && VALID_SKILL_NAME.matcher(name).matches();
    }

    /**
     * 生成 skill 索引（用于系统提示）。
     */
    public String buildSkillsIndex() {
        List<SkillV2> skills = list();
        if (skills.isEmpty()) {
            return "";
        }

        StringBuilder lines = new StringBuilder();
        for (SkillV2 skill : skills) {
            lines.append(skill.toIndexLine()).append("\n");
        }

        String joined = lines.toString().trim();
        if (joined.length() > SKILLS_INDEX_MAX_CHARS) {
            joined = joined.substring(0, SKILLS_INDEX_MAX_CHARS) + "\n... (truncated)";
        }

        return String.join("\n",
                "",
                "# Skills — playbooks you can invoke",
                "",
                "One-liner index. Each entry is a user-authored playbook. Call `run_skill({ name: \"<skill-name>\", arguments: \"<task>\" })`.",
                "Entries tagged `[🧬 subagent]` spawn an isolated subagent — only its final answer returns.",
                "",
                "```",
                joined,
                "```"
        );
    }

    /**
     * Skill 根目录。
     */
    public static class SkillRoot {
        public final Path dir;
        public final SkillV2.Scope scope;
        public final int priority;

        public SkillRoot(Path dir, SkillV2.Scope scope, int priority) {
            this.dir = dir;
            this.scope = scope;
            this.priority = priority;
        }
    }
}