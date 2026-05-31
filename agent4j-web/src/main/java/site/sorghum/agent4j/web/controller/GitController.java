package site.sorghum.agent4j.web.controller;

import org.noear.solon.annotation.*;

import site.sorghum.agent4j.web.common.ServiceException;
import site.sorghum.agent4j.web.model.ApiResponse;
import site.sorghum.agent4j.web.service.AgentService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/**
 * Git 状态 API —— 返回当前分支和变更文件列表。
 *
 * @author Sorghum
 */
@Controller
@Mapping("/api/git")
public class GitController {

    @Inject
    private AgentService agentService;

    /** 当前分支 —— GET /api/git/branch?workspaceHash=xxx */
    @Get
    @Mapping("/branch")
    public Object branch(@Param(value = "workspaceHash", required = false) String workspaceHash) {
        String workspacePath = resolveWorkspace(workspaceHash);
        String branch = runGit(workspacePath, "rev-parse", "--abbrev-ref", "HEAD");
        if (branch == null) {
            throw new ServiceException("无法获取 Git 分部信息，目录可能不是 Git 仓库");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("branch", branch.trim());
        return ApiResponse.ok(data);
    }

    /** 变更文件列表 —— GET /api/git/diff?workspaceHash=xxx */
    @Get
    @Mapping("/diff")
    public Object diff(@Param(value = "workspaceHash", required = false) String workspaceHash) {
        String workspacePath = resolveWorkspace(workspaceHash);

        // 1. 当前分支
        String branch = runGit(workspacePath, "rev-parse", "--abbrev-ref", "HEAD");

        // 2. 使用 git status --porcelain 获取变更列表
        String raw = runGit(workspacePath, "status", "--porcelain");

        // 3. 分三组：staged / unstaged / untracked
        List<Map<String, String>> staged = new ArrayList<>();
        List<Map<String, String>> unstaged = new ArrayList<>();
        List<Map<String, String>> untracked = new ArrayList<>();

        if (raw != null && !raw.trim().isEmpty()) {
            for (String line : raw.split("\n")) {
                if (line.trim().isEmpty() || line.length() < 3) continue;

                String indexStatus = String.valueOf(line.charAt(0));
                String workStatus  = String.valueOf(line.charAt(1));
                String filename = line.substring(3).trim();

                // 处理 rename: "R  old -> new"
                if (filename.contains(" -> ")) {
                    String[] parts = filename.split(" -> ", 2);
                    filename = parts[1];
                }

                Map<String, String> file = new LinkedHashMap<>();
                file.put("path", filename);
                file.put("index", indexStatus);
                file.put("workTree", workStatus);

                if (indexStatus.equals("?") && workStatus.equals("?")) {
                    // ?? — untracked
                    untracked.add(file);
                } else {
                    // staged changes（index 列非空且非 ?）
                    if (!indexStatus.equals(" ") && !indexStatus.equals("?")) {
                        file.put("status", indexStatus);
                        staged.add(file);
                    }
                    // unstaged changes（workTree 列非空且非 ?）
                    if (!workStatus.equals(" ") && !workStatus.equals("?")) {
                        Map<String, String> unstagedFile = new LinkedHashMap<>(file);
                        unstagedFile.put("status", workStatus);
                        unstaged.add(unstagedFile);
                    }
                }
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("branch", branch != null ? branch.trim() : "unknown");
        data.put("staged", staged);
        data.put("unstaged", unstaged);
        data.put("untracked", untracked);
        data.put("count", staged.size() + unstaged.size() + untracked.size());
        return ApiResponse.ok(data);
    }

    // ==================== 工具方法 ====================

    private String resolveWorkspace(String workspaceHash) {
        String workspacePath = agentService.resolveWorkspacePath(workspaceHash);
        if (workspacePath == null) workspacePath = agentService.getWorkspace();
        if (workspacePath == null) throw new ServiceException("未设置工作区");
        return workspacePath;
    }

    /**
     * 在指定目录执行 git 命令，返回 stdout。
     */
    private String runGit(String dir, String... args) {
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
                    if (stdout.length() > 0) stdout.append('\n');
                    stdout.append(line);
                }
            }

            // 读取 stderr 避免进程阻塞
            try (BufferedReader errReader = new BufferedReader(
                    new InputStreamReader(proc.getErrorStream(), StandardCharsets.UTF_8))) {
                while (errReader.readLine() != null) { /* drain */ }
            }

            int exit = proc.waitFor();
            if (exit != 0) return null;
            return stdout.toString();
        } catch (Exception e) {
            System.err.println("[git] 执行失败: " + e.getMessage());
            return null;
        }
    }
}
