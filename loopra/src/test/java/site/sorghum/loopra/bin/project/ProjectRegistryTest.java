package site.sorghum.loopra.bin.project;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ProjectRegistry 单元测试：重点验证 JUnit 测试临时目录不写入注册表，
 * 避免桌面端首页项目列表堆积测试残留项目（junitXXX / defaultWorkSpace）。
 */
class ProjectRegistryTest {

    @TempDir
    Path junitTempDir;

    /** 本次测试中正常注册的项目 hash，测试结束后清理，避免污染 ~/.loopra/workspace。 */
    private final List<String> createdHashes = new ArrayList<>();

    @AfterEach
    void cleanupRegisteredProjects() {
        ProjectRegistry registry = new ProjectRegistry();
        for (String hash : createdHashes) {
            try {
                registry.deleteProject(hash);
            } catch (IOException ignored) {
            }
        }
    }

    @Test
    void junitTempDirIsRecognized() {
        // @TempDir 生成的目录名以 junit 开头且位于系统临时目录下，应被识别为测试临时路径
        assertTrue(ProjectRegistry.isJunitTempPath(junitTempDir.toString()),
                "@TempDir 目录应被识别为测试临时路径");
        // 测试临时目录下的默认工作区（LoopraConfig 未配置项目时的初始化路径）也应命中
        Path nested = junitTempDir.resolve(".loopra").resolve("defaultWorkSpace");
        assertTrue(ProjectRegistry.isJunitTempPath(nested.toString()),
                "junit 目录下的 defaultWorkSpace 应被识别为测试临时路径");
    }

    @Test
    void normalPathsAreNotTestTemp() {
        // 普通项目路径（即使位于临时目录下但目录名非 junit 开头）不应被误判
        String tmpDir = System.getProperty("java.io.tmpdir");
        assertFalse(ProjectRegistry.isJunitTempPath(Paths.get(tmpDir, "my-real-project").toString()));
        // 项目工作目录与非法输入
        assertFalse(ProjectRegistry.isJunitTempPath(Paths.get(".").toAbsolutePath().normalize().toString()));
        assertFalse(ProjectRegistry.isJunitTempPath(null));
        assertFalse(ProjectRegistry.isJunitTempPath(""));
    }

    @Test
    void junitTempDirIsNotRegistered() throws IOException {
        ProjectRegistry registry = ProjectRegistry.getOrCreate(junitTempDir.toString());

        // 目录结构仍创建（Goal/Checklist/会话存储可用），但不写注册表配置
        assertTrue(Files.isDirectory(registry.getSessionsDir(junitTempDir.toString())),
                "测试临时目录的会话目录仍应创建，保证存储可用");
        assertFalse(Files.exists(registry.getProjectConfigPath(junitTempDir.toString())),
                "测试临时目录不应写入 workspace.json");

        // 项目列表中不应出现该测试路径
        boolean found = new ProjectRegistry().listProjects().stream()
                .anyMatch(p -> p.path().equals(junitTempDir.toAbsolutePath().normalize().toString()));
        assertFalse(found, "junit 临时目录不应出现在项目列表");
    }

    @Test
    void normalProjectIsRegistered() throws IOException {
        // 使用非 junit 前缀的临时目录模拟正常项目注册
        Path projectDir = Files.createTempDirectory("prj-reg-test-");
        try {
            ProjectRegistry registry = ProjectRegistry.getOrCreate(projectDir.toString());
            createdHashes.add(registry.getProjectDir(projectDir.toString()).getFileName().toString());

            assertTrue(Files.exists(registry.getProjectConfigPath(projectDir.toString())),
                    "正常路径应写入 workspace.json");

            boolean found = new ProjectRegistry().listProjects().stream()
                    .anyMatch(p -> p.path().equals(projectDir.toAbsolutePath().normalize().toString()));
            assertTrue(found, "正常路径应出现在项目列表");
        } finally {
            new ProjectRegistry().deleteProject(ProjectRegistry.computeProjectHash(projectDir.toString()));
        }
    }
}
