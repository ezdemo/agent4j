package site.sorghum.agent4j.web.controller;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.noear.solon.annotation.*;
import org.noear.snack4.ONode;
import site.sorghum.agent4j.web.common.ServiceException;
import site.sorghum.agent4j.web.model.*;
import site.sorghum.agent4j.web.service.AgentService;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Git 管理 API —— 提供 Git 状态查看、仓库初始化、暂存/取消暂存、提交、
 * Diff 内容查看及历史版本文件内容获取等完整的 Git 管理能力。
 *
 * @author Sorghum
 */
@Slf4j
@Api(tags = "Git")
@Controller
@Mapping("/api/git")
public class GitController {

    @Inject
    private AgentService agentService;

    // ==================== 查询端点 ====================

    @ApiOperation(value = "获取当前 Git 分支", notes = "返回工作区所在 Git 仓库的当前分支名称")
    @Get
    @Mapping("/branch")
    public ApiResponse<GitBranchDTO> branch(
            @ApiParam(value = "工作区 hash") @Param(value = "workspaceHash", required = false) String workspaceHash) {
        String workspacePath = resolveWorkspace(workspaceHash);
        String branch = runGitSimple(workspacePath, "rev-parse", "--abbrev-ref", "HEAD");
        if (branch == null) {
            throw new ServiceException("无法获取 Git 分支信息，目录可能不是 Git 仓库");
        }
        return ApiResponse.ok(new GitBranchDTO(branch.trim()));
    }

    @ApiOperation(value = "获取 Git 变更文件列表", notes = "返回暂存、未暂存和未跟踪的文件变更列表")
    @Get
    @Mapping("/diff")
    public ApiResponse<GitDiffDTO> diff(
            @ApiParam(value = "工作区 hash") @Param(value = "workspaceHash", required = false) String workspaceHash) {
        String workspacePath = resolveWorkspace(workspaceHash);

        String branch = runGitSimple(workspacePath, "rev-parse", "--abbrev-ref", "HEAD");
        String raw = runGitSimple(workspacePath, "status", "--porcelain");

        List<GitFileChangeDTO> staged = new ArrayList<>();
        List<GitFileChangeDTO> unstaged = new ArrayList<>();
        List<GitFileChangeDTO> untracked = new ArrayList<>();

        if (raw != null && !raw.trim().isEmpty()) {
            for (String line : raw.split("\n")) {
                if (line.trim().isEmpty() || line.length() < 3) continue;

                String indexStatus = String.valueOf(line.charAt(0));
                String workStatus = String.valueOf(line.charAt(1));
                String filename = line.substring(3).trim();

                if (filename.contains(" -> ")) {
                    String[] parts = filename.split(" -> ", 2);
                    filename = parts[1];
                }

                if (indexStatus.equals("?") && workStatus.equals("?")) {
                    untracked.add(new GitFileChangeDTO(filename, indexStatus, workStatus, null));
                } else {
                    if (!indexStatus.equals(" ") && !indexStatus.equals("?")) {
                        staged.add(new GitFileChangeDTO(filename, indexStatus, workStatus, indexStatus));
                    }
                    if (!workStatus.equals(" ") && !workStatus.equals("?")) {
                        unstaged.add(new GitFileChangeDTO(filename, indexStatus, workStatus, workStatus));
                    }
                }
            }
        }

        return ApiResponse.ok(new GitDiffDTO(
                branch != null ? branch.trim() : "unknown",
                staged, unstaged, untracked,
                staged.size() + unstaged.size() + untracked.size()
        ));
    }

    /**
     * Git 综合状态检测 —— 四阶段检测：git 可用性 → 仓库状态 → 分支名 → 变更文件。
     */
    @ApiOperation(value = "Git 综合状态检测",
            notes = "依次检测 Git 是否安装、工作区是否初始化为 Git 仓库、获取当前分支名及变更文件列表")
    @Get
    @Mapping("/status")
    public ApiResponse<GitStatusDTO> status(
            @ApiParam(value = "工作区 hash") @Param(value = "workspaceHash", required = false) String workspaceHash) throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        File workspaceDir = new File(resolveWorkspace(workspaceHash));

        // 1. 检测 git 是否可用
        try {
            ProcessResult checkGit = runGit(workspaceDir, "git", "--version");
            if (checkGit.exitCode != 0) {
                return ApiResponse.ok(new GitStatusDTO(false, false, null,
                        workspaceDir.getAbsolutePath(), List.of(), List.of(), List.of()));
            }
        } catch (Exception e) {
            return ApiResponse.ok(new GitStatusDTO(false, false, null,
                    workspaceDir.getAbsolutePath(), List.of(), List.of(), List.of()));
        }

        // 2. 检测是否是 git 仓库
        ProcessResult checkRepo = runGit(workspaceDir, "git", "rev-parse", "--is-inside-work-tree");
        if (checkRepo.exitCode != 0) {
            return ApiResponse.ok(new GitStatusDTO(true, false, null,
                    workspaceDir.getAbsolutePath(), List.of(), List.of(), List.of()));
        }

        // 3. 获取分支名
        ProcessResult branchResult = runGit(workspaceDir, "git", "branch", "--show-current");
        String branch = branchResult.stdout.trim();
        if (branch.isEmpty()) branch = "master";

        // 4. 解析 git status --porcelain=v1
        ProcessResult statusResult = runGit(workspaceDir, "git", "status", "--porcelain=v1");
        List<GitFileChangeDTO> changed = new ArrayList<>();
        List<GitFileChangeDTO> staged = new ArrayList<>();
        List<GitFileChangeDTO> untracked = new ArrayList<>();

        for (String line : statusResult.stdout.split("\n")) {
            if (line.length() < 4) continue;
            String x = line.substring(0, 1);
            String y = line.substring(1, 2);
            String filePath = line.substring(3);

            if (filePath.endsWith("/")) {
                filePath = filePath.substring(0, filePath.length() - 1);
            }

            if ("?".equals(x) && "?".equals(y)) {
                untracked.add(new GitFileChangeDTO(filePath, x, y, "U"));
            } else {
                if (!" ".equals(x) && !"?".equals(x))
                    staged.add(new GitFileChangeDTO(filePath, x, y, x));
                if (!" ".equals(y) && !"?".equals(y))
                    changed.add(new GitFileChangeDTO(filePath, x, y, y));
            }
        }

        return ApiResponse.ok(new GitStatusDTO(true, true, branch,
                workspaceDir.getAbsolutePath(), changed, staged, untracked));
    }

    /**
     * 获取 Git Diff 内容 —— 分别执行未暂存变更和已暂存变更的 diff 查询，合并输出。
     */
    @ApiOperation(value = "获取 Git Diff 内容",
            notes = "返回完整的 unified diff 文本和 stat 变更统计摘要。单文件 diff 超过 2000 行时自动截断")
    @Get
    @Mapping("/diff-content")
    public ApiResponse<GitDiffContentDTO> diffContent(
            @ApiParam(value = "工作区 hash") @Param(value = "workspaceHash", required = false) String workspaceHash,
            @ApiParam(value = "可选的文件路径，用于查看指定文件的 diff") @Param(value = "path", required = false) String path) throws Exception {
        File workspaceDir = new File(resolveWorkspace(workspaceHash));

        // 安全校验：防止路径穿越
        if (path != null && (path.contains("..") || path.startsWith("/"))) {
            return ApiResponse.fail("Invalid path");
        }

        boolean hasPath = path != null && !path.trim().isEmpty();

        // 未暂存的变更
        List<String> unstagedCmd = new ArrayList<>(Arrays.asList("git", "diff"));
        if (hasPath) { unstagedCmd.add("--"); unstagedCmd.add(path); }
        ProcessResult unstagedResult = runGit(workspaceDir, unstagedCmd.toArray(new String[0]));

        // 已暂存的变更
        List<String> stagedCmd = new ArrayList<>(Arrays.asList("git", "diff", "--cached"));
        if (hasPath) { stagedCmd.add("--"); stagedCmd.add(path); }
        ProcessResult stagedResult = runGit(workspaceDir, stagedCmd.toArray(new String[0]));

        // 合并 diff 输出
        String fullDiff = unstagedResult.stdout;
        if (!stagedResult.stdout.isEmpty()) {
            fullDiff += "\n" + stagedResult.stdout;
        }

        // 截断保护：单文件 diff 限制 2000 行
        if (hasPath) {
            String[] lines = fullDiff.split("\n");
            if (lines.length > 2000) {
                fullDiff = String.join("\n", Arrays.copyOf(lines, 2000))
                        + "\n\n... (差异过大，仅显示前 2000 行，请在终端查看完整 diff)";
            }
        }

        // stat 摘要
        List<String> statCmd = new ArrayList<>(Arrays.asList("git", "diff", "--stat"));
        if (hasPath) { statCmd.add("--"); statCmd.add(path); }
        ProcessResult statResult = runGit(workspaceDir, statCmd.toArray(new String[0]));

        List<String> statCachedCmd = new ArrayList<>(Arrays.asList("git", "diff", "--cached", "--stat"));
        if (hasPath) { statCachedCmd.add("--"); statCachedCmd.add(path); }
        ProcessResult statCachedResult = runGit(workspaceDir, statCachedCmd.toArray(new String[0]));

        String stat = statResult.stdout;
        if (!statCachedResult.stdout.isEmpty()) {
            stat += (stat.isEmpty() ? "" : "\n") + statCachedResult.stdout;
        }

        return ApiResponse.ok(new GitDiffContentDTO(fullDiff, stat));
    }

    /**
     * 获取 Git 仓库中指定版本的文件内容。
     */
    @ApiOperation(value = "获取 Git 仓库中指定版本的文件内容",
            notes = "通过 git show ref:path 获取文件内容，默认 ref 为 HEAD")
    @Get
    @Mapping("/file-content")
    public ApiResponse<GitFileContentDTO> fileContent(
            @ApiParam(value = "工作区 hash") @Param(value = "workspaceHash", required = false) String workspaceHash,
            @ApiParam(value = "文件路径（相对于仓库根目录）") @Param("path") String path,
            @ApiParam(value = "Git 引用（分支名、标签、提交哈希等），默认为 HEAD") @Param(value = "ref", required = false) String ref) throws Exception {
        File workspaceDir = new File(resolveWorkspace(workspaceHash));

        if (path == null || path.contains("..") || path.startsWith("/")) {
            return ApiResponse.fail("Invalid path");
        }
        if (ref == null || ref.isEmpty()) ref = "HEAD";

        ProcessResult result = runGit(workspaceDir, "git", "show", ref + ":" + path);

        if (result.exitCode != 0) {
            return ApiResponse.fail("File not found: " + result.stderr);
        }

        return ApiResponse.ok(new GitFileContentDTO(result.stdout));
    }

    // ==================== 操作端点 ====================

    /**
     * 初始化 Git 仓库 —— git init + 自动生成 .gitignore + 可选初始提交。
     */
    @ApiOperation(value = "初始化 Git 仓库",
            notes = "在工作区执行 git init，自动生成 .gitignore 文件（仅当文件不存在时），可选执行初始提交")
    @Post
    @Mapping("/init")
    public ApiResponse<Map<String, Object>> init(
            @ApiParam(value = "工作区 hash") @Param(value = "workspaceHash", required = false) String workspaceHash,
            @ApiParam(value = "是否执行初始提交，默认为 false") @Param(value = "initialCommit", required = false) Boolean initialCommit) throws Exception {
        File workspaceDir = new File(resolveWorkspace(workspaceHash));

        // 安全校验：确认不是已有仓库
        ProcessResult check = runGit(workspaceDir, "git", "rev-parse", "--is-inside-work-tree");
        if (check.exitCode == 0) {
            return ApiResponse.fail("Already a git repository");
        }

        // 执行 git init
        ProcessResult initResult = runGit(workspaceDir, "git", "init");
        if (initResult.exitCode != 0) {
            return ApiResponse.fail("git init failed: " + initResult.stderr);
        }

        // 自动生成 .gitignore（仅当文件不存在时）
        File gitignore = new File(workspaceDir, ".gitignore");
        if (!gitignore.exists()) {
            String content = String.join("\n",
                    "# Auto-generated by Agent4j",
                    ".git/",
                    ".idea/",
                    ".soloncode/",
                    ".gradle/",
                    ".mvn/",
                    "node_modules/",
                    "target/",
                    "build/",
                    "__pycache__/",
                    "*.class",
                    "*.jar",
                    "*.log"
            );
            java.nio.file.Files.write(gitignore.toPath(), content.getBytes(StandardCharsets.UTF_8));
        }

        // 可选：执行 initial commit
        if (Boolean.TRUE.equals(initialCommit)) {
            runGit(workspaceDir, "git", "add", "-A");
            runGit(workspaceDir, "git", "-c", "user.name=Agent4j",
                    "-c", "user.email=agent4j@sorghum.site",
                    "commit", "-m", "Initial commit");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("initialized", true);

        ProcessResult branchResult = runGit(workspaceDir, "git", "branch", "--show-current");
        String branch = branchResult.stdout.trim();
        data.put("branch", branch.isEmpty() ? "master" : branch);

        return ApiResponse.ok(data);
    }

    /**
     * 将指定文件添加到 Git 暂存区（git add）。
     */
    @ApiOperation(value = "暂存文件", notes = "将指定文件添加到 Git 暂存区（git add）")
    @Post
    @Mapping("/stage")
    public ApiResponse<Map<String, Object>> stage(
            @ApiParam(value = "工作区 hash") @Param(value = "workspaceHash", required = false) String workspaceHash,
            @Body String body) throws Exception {
        File workspaceDir = new File(resolveWorkspace(workspaceHash));
        ProcessResult check = runGit(workspaceDir, "git", "rev-parse", "--is-inside-work-tree");
        if (check.exitCode != 0) {
            return ApiResponse.fail("Not a git repository");
        }

        String path = extractPath(body);
        if (path == null) {
            return ApiResponse.fail("Path is required");
        }

        ProcessResult addResult = runGit(workspaceDir, "git", "add", "--", path);
        if (addResult.exitCode != 0) {
            return ApiResponse.fail("git add failed: " + addResult.stderr);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("path", path);
        return ApiResponse.ok(data);
    }

    /**
     * 将指定文件移出 Git 暂存区（git reset HEAD -- path）。
     */
    @ApiOperation(value = "取消暂存文件", notes = "将指定文件移出 Git 暂存区（git reset HEAD -- path）")
    @Post
    @Mapping("/unstage")
    public ApiResponse<Map<String, Object>> unstage(
            @ApiParam(value = "工作区 hash") @Param(value = "workspaceHash", required = false) String workspaceHash,
            @Body String body) throws Exception {
        File workspaceDir = new File(resolveWorkspace(workspaceHash));
        ProcessResult check = runGit(workspaceDir, "git", "rev-parse", "--is-inside-work-tree");
        if (check.exitCode != 0) {
            return ApiResponse.fail("Not a git repository");
        }

        String path = extractPath(body);
        if (path == null) {
            return ApiResponse.fail("Path is required");
        }

        ProcessResult resetResult = runGit(workspaceDir, "git", "reset", "HEAD", "--", path);
        if (resetResult.exitCode != 0) {
            return ApiResponse.fail("git reset failed: " + resetResult.stderr);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("path", path);
        return ApiResponse.ok(data);
    }

    /**
     * Git 提交 —— 支持精确文件列表或全量 add -A。
     */
    @ApiOperation(value = "Git 提交",
            notes = "支持精确文件列表或全量 add -A。请求体为 JSON：{ \"message\": \"...\", \"files\": [\"a.java\", \"b.css\"] }")
    @Post
    @Mapping("/commit")
    public ApiResponse<GitCommitResultDTO> commit(
            @ApiParam(value = "工作区 hash") @Param(value = "workspaceHash", required = false) String workspaceHash,
            @Body String body) throws Exception {
        File workspaceDir = new File(resolveWorkspace(workspaceHash));

        // 安全校验：确认是 git 仓库
        ProcessResult check = runGit(workspaceDir, "git", "rev-parse", "--is-inside-work-tree");
        if (check.exitCode != 0) {
            return ApiResponse.fail("Not a git repository");
        }

        // 解析 JSON body
        String message = null;
        List<String> files = null;
        if (body != null && !body.trim().isEmpty()) {
            try {
                ONode json = ONode.ofJson(body);
                if (json != null && json.isObject()) {
                    ONode msgNode = json.get("message");
                    if (msgNode != null && msgNode.isString()) {
                        message = msgNode.getString();
                    }
                    ONode filesNode = json.get("files");
                    if (filesNode != null && filesNode.isArray()) {
                        files = new ArrayList<>();
                        for (ONode f : filesNode.getArray()) {
                            files.add(f.getString());
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (message == null || message.trim().isEmpty()) {
            return ApiResponse.fail("Commit message is required");
        }

        // git add：有指定文件时精确暂存，否则 add -A
        ProcessResult addResult;
        if (files != null && !files.isEmpty()) {
            // 先清空暂存区，避免之前已暂存的非选中文件被一起提交
            runGit(workspaceDir, "git", "reset", "HEAD", "--");

            List<String> addCmd = new ArrayList<>();
            addCmd.add("git");
            addCmd.add("add");
            addCmd.add("--");
            addCmd.addAll(files);
            addResult = runGit(workspaceDir, addCmd.toArray(new String[0]));
        } else {
            addResult = runGit(workspaceDir, "git", "add", "-A");
        }
        if (addResult.exitCode != 0) {
            return ApiResponse.fail("git add failed: " + addResult.stderr);
        }

        // git commit
        ProcessResult commitResult = runGit(workspaceDir, "git",
                "-c", "user.name=Agent4j",
                "-c", "user.email=agent4j@sorghum.site",
                "commit", "-m", message);

        if (commitResult.exitCode != 0) {
            return ApiResponse.fail("git commit failed: " + commitResult.stderr);
        }

        return ApiResponse.ok(new GitCommitResultDTO(commitResult.stdout));
    }

    // ==================== 工具方法 ====================

    private String resolveWorkspace(String workspaceHash) {
        String workspacePath = agentService.resolveWorkspacePath(workspaceHash);
        if (workspacePath == null) workspacePath = agentService.getWorkspace();
        if (workspacePath == null) throw new ServiceException("未设置工作区");
        return workspacePath;
    }

    /**
     * 从 JSON 请求体中提取 path 字段。
     */
    private String extractPath(String body) {
        if (body == null || body.trim().isEmpty()) return null;
        try {
            ONode json = ONode.ofJson(body);
            if (json != null && json.isObject()) {
                ONode pathNode = json.get("path");
                if (pathNode != null && pathNode.isString()) {
                    String path = pathNode.getString();
                    if (path != null && !path.trim().isEmpty()
                            && !path.contains("..") && !path.startsWith("/")) {
                        return path;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 简单 Git 命令执行 —— 用于只读查询，无超时控制。
     */
    private String runGitSimple(String dir, String... args) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("git");
            Collections.addAll(cmd, args);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new java.io.File(dir));
            pb.redirectErrorStream(false);
            Process proc = pb.start();

            StringBuilder stdout = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!stdout.isEmpty()) stdout.append('\n');
                    stdout.append(line);
                }
            }

            try (BufferedReader errReader = new BufferedReader(
                    new InputStreamReader(proc.getErrorStream(), StandardCharsets.UTF_8))) {
                while (errReader.readLine() != null) { /* drain */ }
            }

            int exit = proc.waitFor();
            if (exit != 0) return null;
            return stdout.toString();
        } catch (Exception e) {
            log.warn("[git] 执行失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 增强 Git 命令执行 —— 带 10 秒超时保护、禁用终端交互提示。
     */
    private ProcessResult runGit(File workDir, String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir);
        pb.redirectErrorStream(false);
        pb.environment().put("GIT_TERMINAL_PROMPT", "0");

        Process proc = pb.start();
        String stdout = readStream(proc.getInputStream());
        String stderr = readStream(proc.getErrorStream());

        boolean finished = proc.waitFor(10, TimeUnit.SECONDS);
        if (!finished) {
            proc.destroyForcibly();
            ProcessResult result = new ProcessResult();
            result.exitCode = -1;
            result.stdout = "";
            result.stderr = "Command timed out after 10 seconds";
            return result;
        }

        ProcessResult result = new ProcessResult();
        result.exitCode = proc.exitValue();
        result.stdout = stdout;
        result.stderr = stderr;
        return result;
    }

    private String readStream(java.io.InputStream is) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Git 进程执行结果。
     */
    @Data
    static class ProcessResult {
        int exitCode;
        String stdout;
        String stderr;
    }
}
