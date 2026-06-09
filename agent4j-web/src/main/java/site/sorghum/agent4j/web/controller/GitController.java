package site.sorghum.agent4j.web.controller;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.noear.solon.annotation.*;
import org.noear.snack4.ONode;
import site.sorghum.agent4j.bin.agent.ChatMessage;
import site.sorghum.agent4j.bin.model.ModelClient;
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

        List<GitFileChangeDTO> changed = new ArrayList<>();
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

                // 去掉文件名前后的引号（用于文件名包含空格的情况）
                if (filename.startsWith("\"") && filename.endsWith("\"")) {
                    filename = filename.substring(1, filename.length() - 1);
                }

                if (indexStatus.equals("?") && workStatus.equals("?")) {
                    untracked.add(new GitFileChangeDTO(filename, indexStatus, workStatus, "U"));
                } else {
                    // 有变更（不管是否暂存）
                    String status = !indexStatus.equals(" ") && !indexStatus.equals("?") ? indexStatus : workStatus;
                    changed.add(new GitFileChangeDTO(filename, indexStatus, workStatus, status));
                }
            }
        }

        return ApiResponse.ok(new GitDiffDTO(
                branch != null ? branch.trim() : "unknown",
                changed, untracked,
                changed.size() + untracked.size()
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
            @ApiParam(value = "工作区 hash")
            @Param(value = "workspaceHash", required = false) String workspaceHash) throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        File workspaceDir = new File(resolveWorkspace(workspaceHash));

        // 1. 检测 git 是否可用
        try {
            ProcessResult checkGit = runGit(workspaceDir, "git", "--version");
            if (checkGit.exitCode != 0) {
                return ApiResponse.ok(new GitStatusDTO(false, false, null,
                        workspaceDir.getAbsolutePath(), List.of(), List.of()));
            }
        } catch (Exception e) {
            return ApiResponse.ok(new GitStatusDTO(false, false, null,
                    workspaceDir.getAbsolutePath(), List.of(), List.of()));
        }

        // 2. 检测是否是 git 仓库
        ProcessResult checkRepo = runGit(workspaceDir, "git", "rev-parse", "--is-inside-work-tree");
        if (checkRepo.exitCode != 0) {
            return ApiResponse.ok(new GitStatusDTO(true, false, null,
                    workspaceDir.getAbsolutePath(), List.of(), List.of()));
        }

        // 3. 获取分支名
        ProcessResult branchResult = runGit(workspaceDir, "git", "branch", "--show-current");
        String branch = branchResult.stdout.trim();
        if (branch.isEmpty()) branch = "master";

        // 4. 解析 git status --porcelain=v1
        ProcessResult statusResult = runGit(workspaceDir, "git", "status", "--porcelain=v1");
        List<GitFileChangeDTO> changed = new ArrayList<>();
        List<GitFileChangeDTO> untracked = new ArrayList<>();

        for (String line : statusResult.stdout.split("\n")) {
            if (line.length() < 4) continue;
            String x = line.substring(0, 1);
            String y = line.substring(1, 2);
            String filePath = line.substring(3).trim();

            // 处理重命名的情况：R  old -> new
            if (filePath.contains(" -> ")) {
                String[] parts = filePath.split(" -> ", 2);
                filePath = parts[1];
            }

            // 去掉文件名前后的引号（用于文件名包含空格的情况）
            if (filePath.startsWith("\"") && filePath.endsWith("\"")) {
                filePath = filePath.substring(1, filePath.length() - 1);
            }

            if (filePath.endsWith("/")) {
                filePath = filePath.substring(0, filePath.length() - 1);
            }

            if ("?".equals(x) && "?".equals(y)) {
                untracked.add(new GitFileChangeDTO(filePath, x, y, "U"));
            } else {
                // 有变更（不管是否暂存）
                String status = !" ".equals(x) && !"?".equals(x) ? x : y;
                changed.add(new GitFileChangeDTO(filePath, x, y, status));
            }
        }

        return ApiResponse.ok(new GitStatusDTO(true, true, branch,
                workspaceDir.getAbsolutePath(), changed, untracked));
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
            @ApiParam(value = "可选的文件路径，用于查看指定文件的 diff")
            @Param(value = "path", required = false) String path) throws Exception {
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
            @ApiParam(value = "Git 引用（分支名、标签、提交哈希等），默认为 HEAD")
            @Param(value = "ref", required = false) String ref) throws Exception {
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
            @ApiParam(value = "是否执行初始提交，默认为 false")
            @Param(value = "initialCommit", required = false) Boolean initialCommit) throws Exception {
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

        // git add：有指定文件时精确暂存，否则只提交已暂存的文件
        if (files != null && !files.isEmpty()) {
            // 先清空暂存区，避免之前已暂存的非选中文件被一起提交
            runGit(workspaceDir, "git", "reset", "HEAD", "--");

            List<String> addCmd = new ArrayList<>();
            addCmd.add("git");
            addCmd.add("add");
            addCmd.add("--");
            addCmd.addAll(files);
            ProcessResult addResult = runGit(workspaceDir, addCmd.toArray(new String[0]));
            if (addResult.exitCode != 0) {
                return ApiResponse.fail("git add failed: " + addResult.stderr);
            }
        }
        // 不指定文件时，只提交已暂存的文件，不自动 add 未追踪的文件

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

    /**
     * 切换文件状态：
     * - 未追踪 → 变更：git add
     * - 变更 → 未追踪：git rm --cached（仅对新文件有效）
     */
    @ApiOperation(value = "切换文件状态", notes = "未追踪文件变为变更状态，或变更文件变为未追踪状态")
    @Post
    @Mapping("/toggle")
    public ApiResponse<Map<String, Object>> toggle(
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

        // 检查文件当前状态
        ProcessResult statusResult = runGit(workspaceDir, "git", "status", "--porcelain", "--", path);
        String statusLine = statusResult.stdout.trim();
        
        ProcessResult result;
        String newState;
        
        // 状态码为空格开头表示已暂存，? 表示未追踪
        if (statusLine.isEmpty() || statusLine.startsWith("M") || statusLine.startsWith("A") || 
            statusLine.startsWith("D") || statusLine.startsWith("R")) {
            // 已暂存或已追踪的变更 → 取消暂存
            result = runGit(workspaceDir, "git", "reset", "HEAD", "--", path);
            newState = "untracked";
        } else if (statusLine.startsWith("?")) {
            // 未追踪 → 暂存
            result = runGit(workspaceDir, "git", "add", "--", path);
            newState = "changed";
        } else {
            // 其他情况（如  M 未暂存修改）→ 暂存
            result = runGit(workspaceDir, "git", "add", "--", path);
            newState = "changed";
        }

        if (result.exitCode != 0) {
            return ApiResponse.fail("git toggle failed: " + result.stderr);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("path", path);
        data.put("newState", newState);
        return ApiResponse.ok(data);
    }

    // ==================== AI 辅助 ====================

    /**
     * AI 自动生成 Git 提交消息 —— 获取当前变更内容及近 10 条提交日志，
     * 调用 LLM 生成符合项目提交风格的提交消息。
     * 支持传入 files 数组仅针对选中文件生成。
     */
    @ApiOperation(value = "AI 自动生成 Git 提交消息",
            notes = "根据当前 git diff 和近 10 条提交日志，调用 AI 生成符合风格的提交消息。支持传入 files 数组仅针对选中文件生成")
    @Post
    @Mapping("/generate-commit-message")
    public ApiResponse<GitGenerateMessageDTO> generateCommitMessage(
            @ApiParam(value = "工作区 hash") @Param(value = "workspaceHash", required = false) String workspaceHash,
            @Body String body) throws Exception {
        File workspaceDir = new File(resolveWorkspace(workspaceHash));

        // 安全校验：确认是 git 仓库
        ProcessResult check = runGit(workspaceDir, "git", "rev-parse", "--is-inside-work-tree");
        if (check.exitCode != 0) {
            return ApiResponse.fail("Not a git repository");
        }

        // 获取 AI 模型客户端
        ModelClient modelClient = agentService.getSharedModelClient();
        if (modelClient == null) {
            return ApiResponse.fail("AI 模型未配置，请先设置 OPENAI_API_KEY 环境变量");
        }

        // 解析可选的 files 参数
        List<String> files = null;
        if (body != null && !body.trim().isEmpty()) {
            try {
                ONode json = ONode.ofJson(body);
                if (json != null && json.isObject()) {
                    ONode filesNode = json.get("files");
                    if (filesNode != null && filesNode.isArray()) {
                        files = new ArrayList<>();
                        for (ONode f : filesNode.getArray()) {
                            files.add(f.getString());
                        }
                    }
                }
            } catch (Exception ignored) { }
        }

        // 1. 获取近 10 条提交日志（用于风格参考）
        String recentLog = runGitSimple(workspaceDir.getAbsolutePath(),
                "log", "--oneline", "-10");
        if (recentLog == null) recentLog = "";

        // 2. 获取变更（支持按文件过滤）
        String fullDiff;
        if (files != null && !files.isEmpty()) {
            // 仅获取选中文件的 diff
            StringBuilder sb = new StringBuilder();
            for (String f : files) {
                try {
                    ProcessResult sr = runGit(workspaceDir, "git", "diff", "--cached", "--", f);
                    if (sr.stdout != null && !sr.stdout.isEmpty()) sb.append(sr.stdout).append("\n");
                } catch (Exception ignored) { }
                try {
                    ProcessResult ur = runGit(workspaceDir, "git", "diff", "--", f);
                    if (ur.stdout != null && !ur.stdout.isEmpty()) sb.append(ur.stdout).append("\n");
                } catch (Exception ignored) { }
            }
            fullDiff = sb.toString().trim();
        } else {
            // 全部变更
            String stagedDiff = "";
            String unstagedDiff = "";
            try {
                ProcessResult sr = runGit(workspaceDir, "git", "diff", "--cached");
                stagedDiff = sr.stdout != null ? sr.stdout : "";
            } catch (Exception ignored) { }
            try {
                ProcessResult ur = runGit(workspaceDir, "git", "diff");
                unstagedDiff = ur.stdout != null ? ur.stdout : "";
            } catch (Exception ignored) { }
            fullDiff = (stagedDiff + "\n" + unstagedDiff).trim();
        }

        if (fullDiff.isEmpty()) {
            return ApiResponse.fail("没有待提交的变更");
        }

        // 截断 diff 防止超出模型上下文窗口（最多 8000 字符）
        if (fullDiff.length() > 8000) {
            fullDiff = fullDiff.substring(0, 8000)
                    + "\n\n... (diff 过长，已截断至前 8000 字符)";
        }

        // 3. 构建 AI 提示词
        String systemPrompt = "你是一个 Git 提交消息生成助手。"
                + "根据 git diff 内容生成一条简洁、描述性的提交消息。"
                + "严格遵循以下规约：\n"
                + "- 参考「近 10 条提交」的风格（如前缀、格式、语言等）\n"
                + "- 消息长度不超过 72 字符（中文不超过 50 字）\n"
                + "- 使用中文或英文取决于近期提交的语言\n"
                + "- 仅输出提交消息本身，不要任何解释、引号或 markdown 格式";

        String userPrompt = "近 10 条提交（风格参考）：\n"
                + (recentLog.isEmpty() ? "（无历史提交）" : recentLog) + "\n\n"
                + "当前变更（git diff）：\n" + fullDiff + "\n\n"
                + "请生成提交消息：";

        // 4. 调用 AI 模型
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        messages.add(ChatMessage.user(userPrompt));

        try {
            ONode response = modelClient.chat(messages, null);
            String content = response.get("content").getString();
            if (content == null || content.trim().isEmpty()) {
                return ApiResponse.fail("AI 未能生成提交消息");
            }
            // 清理多余的引号和空白
            String message = content.trim()
                    .replaceAll("^[\"']+|[\"']+$", "")
                    .replaceAll("^```[a-z]*\\s*|```$", "")
                    .trim();
            return ApiResponse.ok(new GitGenerateMessageDTO(message));
        } catch (Exception e) {
            log.error("[git] AI 生成提交消息失败: {}", e.getMessage());
            return ApiResponse.fail("AI 调用失败: " + e.getMessage());
        }
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
