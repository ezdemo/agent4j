package site.sorghum.loopra.web.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import site.sorghum.loopra.web.common.ServiceException;
import site.sorghum.loopra.web.common.entity.ProcessResult;
import site.sorghum.loopra.web.model.WorktreeMergeResultDTO;
import site.sorghum.loopra.web.model.WorktreeStatusDTO;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * WorktreeService 集成测试 —— 使用临时 git 仓库验证隔离分支模式的完整生命周期：
 * 创建 / 状态 / 干净合并 / 冲突合并（反向合并 + 冲突解决后再次合并）/ 元数据保护 / 删除。
 */
class WorktreeServiceIntegrationTest {

    private static final String BASE_DIR_PROP = "loopra.worktree.baseDir";

    @TempDir
    Path tempRoot;

    private Path mainRepo;
    private Path worktreeBase;
    private WorktreeService service;
    private GitService gitService;
    private String previousBaseDir;

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(gitAvailable(), "git 不可用，跳过 WorktreeService 集成测试");
        mainRepo = tempRoot.resolve("main");
        worktreeBase = tempRoot.resolve("wt-base");
        Files.createDirectories(mainRepo);

        gitService = new GitService();
        service = new WorktreeService();
        Field gitField = WorktreeService.class.getDeclaredField("gitService");
        gitField.setAccessible(true);
        gitField.set(service, gitService);

        previousBaseDir = System.getProperty(BASE_DIR_PROP);
        System.setProperty(BASE_DIR_PROP, worktreeBase.toString());

        // 初始化临时仓库（独立 user 配置，避免依赖全局 git config）
        runOk(mainRepo.toFile(), "git", "init", "-q");
        runOk(mainRepo.toFile(), "git", "config", "user.name", "test");
        runOk(mainRepo.toFile(), "git", "config", "user.email", "test@loopra.local");
        Files.writeString(mainRepo.resolve("f.txt"), "line1\n");
        runOk(mainRepo.toFile(), "git", "add", "-A");
        runOk(mainRepo.toFile(), "git", "commit", "-qm", "init");
    }

    @AfterEach
    void tearDown() {
        if (previousBaseDir != null) {
            System.setProperty(BASE_DIR_PROP, previousBaseDir);
        } else {
            System.clearProperty(BASE_DIR_PROP);
        }
    }

    @Test
    void createsWorktreeAndMergesCleanChangesBack() throws Exception {
        WorktreeStatusDTO created = service.createAt(mainRepo.toString(), "sess1");
        assertTrue(created.exists(), "隔离分支应已创建");
        assertNotNull(created.branch());
        assertTrue(created.branch().startsWith(WorktreeService.BRANCH_PREFIX));

        Path wt = Path.of(created.worktreePath());
        assertTrue(Files.isDirectory(wt));
        // AI 在隔离分支内留下未提交改动
        Files.writeString(wt.resolve("f.txt"), "line1\nworktree-change\n");

        WorktreeStatusDTO dirty = service.statusAt(mainRepo.toString(), "sess1");
        assertTrue(dirty.exists() && dirty.dirty(), "隔离分支应报告未提交改动");

        WorktreeMergeResultDTO merged = service.mergeBackAt(mainRepo.toString(), "sess1");
        assertTrue(merged.merged(), "干净合并应成功: " + merged.message());
        assertFalse(merged.worktreeRemoved());
        assertTrue(Files.exists(wt), "合并后隔离分支目录应保留");
        assertEquals(created.branch(), runOutput(mainRepo.toFile(), "git", "-C", wt.toString(), "branch", "--show-current").trim());
        assertEquals("line1\nworktree-change\n", readNormalized(mainRepo.resolve("f.txt")));
        assertEquals(WorktreeService.AUTO_COMMIT_MESSAGE,
                runOutput(mainRepo.toFile(), "git", "log", "-1", "--pretty=%s").trim());

        // 同一会话继续在保留的隔离分支修改，并可再次合并。
        Files.writeString(wt.resolve("f.txt"), "line1\nworktree-change\nsecond-change\n");
        WorktreeMergeResultDTO mergedAgain = service.mergeBackAt(mainRepo.toString(), "sess1");
        assertTrue(mergedAgain.merged(), "保留隔离分支后应支持再次合并: " + mergedAgain.message());
        assertFalse(mergedAgain.worktreeRemoved());
        assertTrue(Files.exists(wt));
        assertEquals("line1\nworktree-change\nsecond-change\n", readNormalized(mainRepo.resolve("f.txt")));
    }

    @Test
    void usesGeneratedCommitMessageWhenMerging() throws Exception {
        GitService generatedMessageGitService = new GitService() {
            @Override
            String generateCommitMessageAt(java.io.File workspaceDir, String workspaceHash) {
                return "feat: update payment flow";
            }
        };
        Field gitField = WorktreeService.class.getDeclaredField("gitService");
        gitField.setAccessible(true);
        gitField.set(service, generatedMessageGitService);

        WorktreeStatusDTO created = service.createAt(mainRepo.toString(), "sess-title");
        Files.writeString(Path.of(created.worktreePath()).resolve("f.txt"), "line1\npayment-change\n");

        WorktreeMergeResultDTO merged = service.mergeBackAt(mainRepo.toString(), "sess-title");
        assertTrue(merged.merged(), "生成标题后应正常合并: " + merged.message());
        assertEquals("feat: update payment flow",
                runOutput(mainRepo.toFile(), "git", "log", "-1", "--pretty=%s").trim());
    }

    @Test
    void mergeConflictReturnsConflictFilesAndCanBeResolvedThenMerged() throws Exception {
        service.createAt(mainRepo.toString(), "sess-conflict");
        Path wt = service.pathFor(WorktreeService.hashOf(mainRepo.toString()), "sess-conflict");

        // 主项目前移
        Files.writeString(mainRepo.resolve("f.txt"), "line1\nmain-change\n");
        runOk(mainRepo.toFile(), "git", "commit", "-qam", "main moved");
        // 隔离分支分叉
        Files.writeString(wt.resolve("f.txt"), "line1\nwt-change\n");
        runOk(mainRepo.toFile(), "git", "-C", wt.toString(), "commit", "-qam", "wt moved");

        WorktreeMergeResultDTO conflicted = service.mergeBackAt(mainRepo.toString(), "sess-conflict");
        assertFalse(conflicted.merged());
        assertTrue(conflicted.conflicted());
        assertEquals(List.of("f.txt"), conflicted.conflictFiles());
        assertTrue(Files.exists(wt), "冲突时隔离分支必须保留，供 AI 解决");

        // AI 解决冲突：写入合并后内容并完成合并提交（隔离分支内，不触碰主项目）
        Files.writeString(wt.resolve("f.txt"), "line1\nmain-change\nwt-change\n");
        runOk(mainRepo.toFile(), "git", "-C", wt.toString(), "add", "-A");
        runOk(mainRepo.toFile(), "git", "-C", wt.toString(), "-c", "user.name=t",
                "-c", "user.email=t@t", "commit", "-qm", "resolve conflict");

        WorktreeMergeResultDTO merged = service.mergeBackAt(mainRepo.toString(), "sess-conflict");
        assertTrue(merged.merged(), "解决冲突后应合并成功: " + merged.message());
        assertEquals("line1\nmain-change\nwt-change\n", readNormalized(mainRepo.resolve("f.txt")));
        assertTrue(Files.exists(wt), "解决冲突并合并后隔离分支仍应保留");
    }

    @Test
    void metadataDirectoryKeepsMainWorkspaceVersion() throws Exception {
        // 主项目已有项目记忆（.loopra/loopra-memory.md）
        Files.createDirectories(mainRepo.resolve(".loopra"));
        Files.writeString(mainRepo.resolve(".loopra/loopra-memory.md"), "main-memory\n");
        runOk(mainRepo.toFile(), "git", "add", "-A");
        runOk(mainRepo.toFile(), "git", "commit", "-qm", "add memory");

        service.createAt(mainRepo.toString(), "sess-meta");
        Path wt = service.pathFor(WorktreeService.hashOf(mainRepo.toString()), "sess-meta");

        // 隔离分支内 AI 改动记忆文件与代码文件
        Files.writeString(wt.resolve(".loopra/loopra-memory.md"), "worktree-memory\n");
        Files.writeString(wt.resolve("f.txt"), "line1\ncode-change\n");

        WorktreeMergeResultDTO merged = service.mergeBackAt(mainRepo.toString(), "sess-meta");
        assertTrue(merged.merged());
        // 元数据以主项目版本为准；代码改动正常合并
        assertEquals("main-memory\n", readNormalized(mainRepo.resolve(".loopra/loopra-memory.md")));
        assertEquals("line1\ncode-change\n", readNormalized(mainRepo.resolve("f.txt")));
    }

    @Test
    void removeDiscardsUnmergedWorktree() throws Exception {
        service.createAt(mainRepo.toString(), "sess-remove");
        Path wt = service.pathFor(WorktreeService.hashOf(mainRepo.toString()), "sess-remove");
        Files.writeString(wt.resolve("f.txt"), "line1\nunmerged\n");

        WorktreeStatusDTO removed = service.removeAt(mainRepo.toString(), "sess-remove", true);
        assertFalse(removed.exists());
        assertFalse(Files.exists(wt), "隔离分支目录应被删除");
        // 主项目不受影响
        assertEquals("line1\n", readNormalized(mainRepo.resolve("f.txt")));
    }

    @Test
    void nonGitWorkspaceFailsGracefully() {
        Path nonGit = tempRoot.resolve("non-git");
        assertDoesNotThrow(() -> Files.createDirectories(nonGit));
        WorktreeStatusDTO status = service.statusAt(nonGit.toString(), "sess-x");
        assertFalse(status.exists());
        assertTrue(status.message().contains("不是 Git 仓库"));
        assertThrows(ServiceException.class, () -> service.createAt(nonGit.toString(), "sess-x"));
    }

    @Test
    void idempotentCreateReturnsExistingWorktree() throws Exception {
        WorktreeStatusDTO first = service.createAt(mainRepo.toString(), "sess-dup");
        WorktreeStatusDTO second = service.createAt(mainRepo.toString(), "sess-dup");
        assertTrue(first.exists() && second.exists());
        assertEquals(first.worktreePath(), second.worktreePath());
    }

    // ==================== 工具 ====================

    /** Windows 下 git autocrlf 会把项目文件写成 CRLF，断言前统一归一化。 */
    private static String readNormalized(Path file) throws Exception {
        return Files.readString(file).replace("\r\n", "\n");
    }

    private static boolean gitAvailable() {
        try {
            Process p = new ProcessBuilder("git", "--version").start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String runOutput(java.io.File dir, String... cmd) throws Exception {
        ProcessResult result = gitService.runGit(dir, WorktreeService.WORKTREE_TIMEOUT_SEC, cmd);
        assertEquals(0, result.exitCode, "命令失败: " + String.join(" ", cmd) + "\nstderr: " + result.stderr);
        return result.stdout;
    }

    private void runOk(java.io.File dir, String... cmd) throws Exception {
        ProcessResult result = gitService.runGit(dir, WorktreeService.WORKTREE_TIMEOUT_SEC, cmd);
        assertEquals(0, result.exitCode, "命令失败: " + String.join(" ", cmd) + "\nstderr: " + result.stderr);
    }
}
