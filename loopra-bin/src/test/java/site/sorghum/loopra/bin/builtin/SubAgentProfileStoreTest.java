package site.sorghum.loopra.bin.builtin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SubAgentProfileStoreTest {

    private Path tempHome;
    private Path configFile;

    @BeforeEach
    void setUp() throws IOException {
        tempHome = Files.createTempDirectory("loopra-subagent-test");
        System.setProperty("user.home", tempHome.toString());
        configFile = tempHome.resolve(".loopra/sub-agents.json");
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("user.home");
    }

    @Test
    void firstCallGeneratesDefaultFileWithAllProfilesEnabled() throws IOException {
        SubAgentProfileStore store = new SubAgentProfileStore();

        var all = store.all();

        assertEquals(5, all.size());
        assertTrue(Files.exists(configFile), "首次访问应自动生成默认配置文件");
        assertTrue(all.stream().allMatch(SubAgentProfileConfig::enabled));
        assertTrue(Files.readString(configFile, StandardCharsets.UTF_8).contains("explore"));
    }

    @Test
    void disabledProfileIsExcludedFromAllAndFromLookup() throws IOException {
        SubAgentProfileStore store = new SubAgentProfileStore();
        store.all(); // 生成默认文件

        Files.writeString(configFile, """
                {"profiles":[
                  {"id":"explore","name":"探索","enable":false,"readOnly":true,"instructions":"x"},
                  {"id":"implement","name":"实现","enable":true,"readOnly":false,"instructions":"y"},
                  {"id":"test","name":"测试","enable":true,"readOnly":false,"instructions":"y"},
                  {"id":"review","name":"审查","enable":true,"readOnly":true,"instructions":"y"},
                  {"id":"plan","name":"方案","enable":true,"readOnly":true,"instructions":"y"}
                ]}
                """, StandardCharsets.UTF_8);

        assertEquals(4, store.all().size());
        assertThrows(IllegalArgumentException.class, () -> store.from("explore"));
    }

    @Test
    void modifiedNameAndInstructionsTakeEffectWithoutRestart() throws IOException {
        SubAgentProfileStore store = new SubAgentProfileStore();
        store.all(); // 生成默认文件

        Files.writeString(configFile, """
                {"profiles":[
                  {"id":"explore","name":"探索","enable":true,"readOnly":true,"instructions":"旧提示词"},
                  {"id":"implement","name":"实现(改)","enable":true,"readOnly":false,"instructions":"你是实现子代理(自定义版)。"}
                ]}
                """, StandardCharsets.UTF_8);

        SubAgentProfileConfig implement = store.from("implement");
        assertEquals("实现(改)", implement.name());
        assertTrue(implement.instructions().contains("自定义版"));
    }

    @Test
    void corruptedFileFallsBackToBuiltInDefaultsWithoutOverwriting() throws IOException {
        Files.createDirectories(configFile.getParent());
        Files.writeString(configFile, "{ not valid json", StandardCharsets.UTF_8);

        SubAgentProfileStore store = new SubAgentProfileStore();
        var all = store.all();

        assertEquals(5, all.size());
        // 不覆盖用户文件，保留原样供用户修复
        assertEquals("{ not valid json", Files.readString(configFile, StandardCharsets.UTF_8));
    }

    @Test
    void missingEnableFieldIsTreatedAsEnabled() throws IOException {
        Files.createDirectories(configFile.getParent());
        Files.writeString(configFile, """
                {"profiles":[{"id":"legacy","name":"旧角色","readOnly":true,"instructions":"hi"}]}
                """, StandardCharsets.UTF_8);

        SubAgentProfileStore store = new SubAgentProfileStore();
        assertEquals(6, store.all().size(), "内置角色应自动补全（1 自定义 + 5 内置）");
        assertTrue(store.from("legacy").enabled());
        assertTrue(store.all().stream().anyMatch(c -> c.id.equals("explore")), "内置角色应重新追加");
    }

    @Test
    void savePersistsProfilesAndRejectsInvalidInput() throws IOException {
        SubAgentProfileStore store = new SubAgentProfileStore();
        store.all(); // 生成默认文件

        SubAgentProfileConfig custom = new SubAgentProfileConfig();
        custom.id = "My-Custom";
        custom.name = "自定义";
        custom.enable = true;
        custom.readOnly = false;
        custom.instructions = "你是自定义子代理。";
        custom.allowedTools = List.of("workspace_read", " ", "workspace_write");

        store.save(List.of(custom));

        // id 归一化小写，allowedTools 空项被剔除
        SubAgentProfileConfig loaded = store.from("my-custom");
        assertEquals("my-custom", loaded.id());
        assertEquals(List.of("workspace_read", "workspace_write"), loaded.allowedTools);
        assertTrue(store.all().stream().noneMatch(c -> "explore".equals(c.id)), "全量保存后不应再包含旧的内置角色");

        // 重复 id 拒绝
        SubAgentProfileConfig dup = new SubAgentProfileConfig();
        dup.id = "my-custom";
        dup.instructions = "x";
        assertThrows(IllegalArgumentException.class, () -> store.save(List.of(custom, dup)));

        // 空 id 拒绝
        SubAgentProfileConfig blank = new SubAgentProfileConfig();
        blank.id = "  ";
        blank.instructions = "x";
        assertThrows(IllegalArgumentException.class, () -> store.save(List.of(blank)));
    }

    @Test
    void saveNormalizesEmptyAllowedToolsToNull() throws IOException {
        SubAgentProfileStore store = new SubAgentProfileStore();
        store.all();

        SubAgentProfileConfig config = new SubAgentProfileConfig();
        config.id = "empty-tools";
        config.enable = true;
        config.readOnly = true;
        config.instructions = "x";
        config.allowedTools = List.of();

        store.save(List.of(config));
        assertTrue(store.from("empty-tools").allowedTools == null, "空白名单应归一化为 null（自动模式）");
    }

    @Test
    void builtinProfilesAreReappliedOnRestartPreservingEnable() throws IOException {
        SubAgentProfileStore first = new SubAgentProfileStore();
        first.all(); // 生成默认文件

        // 模拟重启：用户改过内置角色内容/禁用/删除，并新增了自定义角色
        Files.writeString(configFile, """
                {"profiles":[
                  {"id":"explore","name":"改名","enable":false,"readOnly":true,"instructions":"用户自定义提示词","allowedTools":["workspace_write"]},
                  {"id":"custom-agent","name":"自定义","enable":true,"readOnly":false,"instructions":"自定义角色"}
                ]}
                """, StandardCharsets.UTF_8);

        SubAgentProfileStore restarted = new SubAgentProfileStore();
        var all = restarted.allIncludingDisabled();

        SubAgentProfileConfig explore = all.stream().filter(c -> c.id.equals("explore")).findFirst().orElseThrow();
        assertEquals(SubAgentProfile.EXPLORE.instructions(), explore.instructions(), "内置提示词应恢复为 Java 定义");
        assertEquals(SubAgentProfile.EXPLORE.displayName(), explore.name(), "内置名称应恢复为 Java 定义");
        assertEquals(false, explore.enable, "禁用状态应保留");
        assertTrue(explore.allowedTools == null, "内置角色白名单应清空（内置为准）");
        // 被删除的内置角色重新追加且启用
        assertTrue(all.stream().anyMatch(c -> c.id.equals("implement") && c.enabled()), "删除的内置角色应重新追加");
        // 自定义角色保留
        assertTrue(all.stream().anyMatch(c -> c.id.equals("custom-agent")), "自定义角色应保留");
    }
}
