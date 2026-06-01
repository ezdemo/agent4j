package site.sorghum.agent4j.bin.skill;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SkillStoreV2 测试类。
 *
 * @author Sorghum
 */
class SkillStoreV2Test {

    @TempDir
    Path tempDir;

    Path projectRoot;
    Path homeDir;
    SkillStoreV2 store;

    @BeforeEach
    void setUp() {
        projectRoot = tempDir.resolve("project");
        homeDir = tempDir.resolve("home");
        try {
            Files.createDirectories(projectRoot);
            Files.createDirectories(homeDir);
        } catch (IOException e) {
            fail("Failed to create test directories", e);
        }
        store = new SkillStoreV2(projectRoot, homeDir, Collections.emptyList());
    }

    @Test
    void testEmptyStore() {
        List<SkillV2> skills = store.list();
        assertNotNull(skills);
        assertTrue(skills.isEmpty());
    }

    @Test
    void testReadNonExistentSkill() {
        SkillV2 skill = store.read("non-existent");
        assertNull(skill);
    }

    @Test
    void testReadInvalidSkillName() {
        assertNull(store.read(null));
        assertNull(store.read(""));
        assertNull(store.read("invalid name!"));
        assertNull(store.read(".starts-with-dot"));
    }

    @Test
    void testLoadSkillFromFile() throws IOException {
        // 创建 skill 文件
        Path skillsDir = projectRoot.resolve(".agent4j").resolve("skills");
        Files.createDirectories(skillsDir);
        Path skillFile = skillsDir.resolve("test-skill.md");

        String content = "---\n" +
                "name: test-skill\n" +
                "description: A test skill\n" +
                "---\n\n" +
                "# Test Skill\n\n" +
                "This is a test skill body.";
        Files.write(skillFile, content.getBytes(StandardCharsets.UTF_8));

        // 读取 skill
        SkillV2 skill = store.read("test-skill");
        assertNotNull(skill);
        assertEquals("test-skill", skill.name());
        assertEquals("A test skill", skill.description());
        assertEquals(SkillV2.RunAs.INLINE, skill.runAs());
        assertTrue(skill.body().contains("# Test Skill"));
    }

    @Test
    void testLoadSkillFromDirectory() throws IOException {
        // 创建 skill 目录
        Path skillDir = projectRoot.resolve(".agent4j").resolve("skills").resolve("my-skill");
        Files.createDirectories(skillDir);
        Path skillFile = skillDir.resolve("SKILL.md");

        String content = "---\n" +
                "name: my-skill\n" +
                "description: My custom skill\n" +
                "runAs: subagent\n" +
                "allowed-tools: read_file, glob\n" +
                "---\n\n" +
                "# My Skill\n\n" +
                "Subagent instructions here.";
        Files.write(skillFile, content.getBytes(StandardCharsets.UTF_8));

        // 读取 skill
        SkillV2 skill = store.read("my-skill");
        assertNotNull(skill);
        assertEquals("my-skill", skill.name());
        assertEquals("My custom skill", skill.description());
        assertEquals(SkillV2.RunAs.SUBAGENT, skill.runAs());
        assertNotNull(skill.allowedTools());
        assertEquals(2, skill.allowedTools().size());
        assertTrue(skill.allowedTools().contains("read_file"));
        assertTrue(skill.allowedTools().contains("glob"));
    }

    @Test
    void testListMultipleSkills() throws IOException {
        // 创建多个 skill
        Path skillsDir = projectRoot.resolve(".agent4j").resolve("skills");
        Files.createDirectories(skillsDir);

        createSkillFile(skillsDir.resolve("alpha.md"), "alpha", "Alpha skill");
        createSkillFile(skillsDir.resolve("beta.md"), "beta", "Beta skill");
        createSkillFile(skillsDir.resolve("gamma.md"), "gamma", "Gamma skill");

        List<SkillV2> skills = store.list();
        assertEquals(3, skills.size());
        // 应该按名称排序
        assertEquals("alpha", skills.get(0).name());
        assertEquals("beta", skills.get(1).name());
        assertEquals("gamma", skills.get(2).name());
    }

    @Test
    void testProjectScopeOverrideGlobal() throws IOException {
        // 创建全局 skill
        Path globalSkillsDir = homeDir.resolve(".agent4j").resolve("skills");
        Files.createDirectories(globalSkillsDir);
        createSkillFile(globalSkillsDir.resolve("shared.md"), "shared", "Global version");

        // 创建项目级 skill（同名）
        Path projectSkillsDir = projectRoot.resolve(".agent4j").resolve("skills");
        Files.createDirectories(projectSkillsDir);
        createSkillFile(projectSkillsDir.resolve("shared.md"), "shared", "Project version");

        // 项目级应该覆盖全局级
        SkillV2 skill = store.read("shared");
        assertNotNull(skill);
        assertEquals("Project version", skill.description());
    }

    @Test
    void testBuildSkillsIndex() throws IOException {
        // 创建 skill
        Path skillsDir = projectRoot.resolve(".agent4j").resolve("skills");
        Files.createDirectories(skillsDir);

        String content = "---\n" +
                "name: explore\n" +
                "description: Run a focused codebase investigation\n" +
                "runAs: subagent\n" +
                "---\n\n" +
                "Body here.";
        Files.write(skillsDir.resolve("explore.md"), content.getBytes(StandardCharsets.UTF_8));

        String index = store.buildSkillsIndex();
        assertNotNull(index);
        assertFalse(index.isEmpty());
        assertTrue(index.contains("explore"));
        assertTrue(index.contains("[🧬 subagent]"));
        assertTrue(index.contains("Run a focused codebase investigation"));
    }

    @Test
    void testSkillToIndexLine() {
        SkillV2Impl inlineSkill = new SkillV2Impl(
                "test", "Test skill body", "Body", SkillV2.Scope.PROJECT,
                "/path/to/skill", SkillV2.RunAs.INLINE, null, null);
        assertEquals("- test — Test skill body", inlineSkill.toIndexLine());

        SkillV2Impl subagentSkill = new SkillV2Impl(
                "explore", "Explore codebase", "Body", SkillV2.Scope.PROJECT,
                "/path/to/skill", SkillV2.RunAs.SUBAGENT, null, null);
        assertEquals("- explore [🧬 subagent] — Explore codebase", subagentSkill.toIndexLine());
    }

    @Test
    void testSkillToFullContent() {
        SkillV2Impl skill = new SkillV2Impl(
                "my-skill", "My skill description", "# Instructions\n\nDo something.",
                SkillV2.Scope.PROJECT, "/path/to/skill", SkillV2.RunAs.INLINE, null, null);

        String content = skill.toFullContent(null);
        assertTrue(content.contains("# Skill: my-skill"));
        assertTrue(content.contains("> My skill description"));
        assertTrue(content.contains("# Instructions"));

        // 带参数
        String contentWithArgs = skill.toFullContent("arg1 arg2");
        assertTrue(contentWithArgs.contains("Arguments: arg1 arg2"));
    }

    @Test
    void testMultipleRoots() throws IOException {
        // 创建 .agents/skills 目录
        Path agentsSkillsDir = projectRoot.resolve(".agents").resolve("skills");
        Files.createDirectories(agentsSkillsDir);
        createSkillFile(agentsSkillsDir.resolve("agents-skill.md"), "agents-skill", "From .agents");

        // 创建 .claude/skills 目录
        Path claudeSkillsDir = projectRoot.resolve(".claude").resolve("skills");
        Files.createDirectories(claudeSkillsDir);
        createSkillFile(claudeSkillsDir.resolve("claude-skill.md"), "claude-skill", "From .claude");

        // 应该能读取所有目录的 skill
        assertNotNull(store.read("agents-skill"));
        assertNotNull(store.read("claude-skill"));
    }

    private void createSkillFile(Path path, String name, String description) throws IOException {
        Files.createDirectories(path.getParent());
        String content = "---\n" +
                "name: " + name + "\n" +
                "description: " + description + "\n" +
                "---\n\n" +
                "Body for " + name;
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }
}