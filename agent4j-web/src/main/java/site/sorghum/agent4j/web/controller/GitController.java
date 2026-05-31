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

        // 2. 变更文件列表（staged + unstaged）
        String raw = runGit(workspacePath, "status", "--porcelain=v1");

        List<Map<String, String>> files = new ArrayList<>();
        if (raw != null && !raw.trim().isEmpty()) {
            for (String line : raw.split("\n")) {
                if (line.trim().isEmpty()) continue;
                // porcelain v1 格式: XY filename   或   X -> Y filename
                String index = line.length() > 0 ? String.valueOf(line.charAt(0)) : " ";
                String workTree = line.length() > 1 ? String.valueOf(line.charAt(1)) : " ";
                String filename = line.length() > 3 ? line.substring(3).trim() : "";

                // 处理 rename: "R  old -> new"
                if (filename.contains(" -> ")) {
                    String[] parts = filename.split(" -> ", 2);
                    filename = parts[1];
                }

                String status;
                if (index.equals("?") && workTree.equals("?")) {
                    status = "U";  // untracked
                } else if (index.equals("A") || workTree.equals("A")) {
                    status = "A";  // added
                } else if (index.equals("D") || workTree.equals("D")) {
                    status = "D";  // deleted
                } else if (index.equals("R") || workTree.equals("R")) {
                    status = "R";  // renamed
                } else if (index.equals("M") || workTree.equals("M")) {
                    status = "M";  // modified
                } else {
                    status = index.trim() + workTree.trim();
                }

                Map<String, String> file = new LinkedHashMap<>();
                file.put("path", filename);
                file.put("status", status);
                files.add(file);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("branch", branch != null ? branch.trim() : "unknown");
        data.put("files", files);
        data.put("count", files.size());
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
