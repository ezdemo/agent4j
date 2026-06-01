package site.sorghum.agent4j.web.controller;

import org.noear.solon.annotation.*;

import site.sorghum.agent4j.web.common.ServiceException;
import site.sorghum.agent4j.web.model.*;
import site.sorghum.agent4j.web.service.AgentService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
        return ApiResponse.ok(new GitBranchDTO(branch.trim()));
    }

    /** 变更文件列表 —— GET /api/git/diff?workspaceHash=xxx */
    @Get
    @Mapping("/diff")
    public Object diff(@Param(value = "workspaceHash", required = false) String workspaceHash) {
        String workspacePath = resolveWorkspace(workspaceHash);

        String branch = runGit(workspacePath, "rev-parse", "--abbrev-ref", "HEAD");
        String raw = runGit(workspacePath, "status", "--porcelain");

        List<GitFileChangeDTO> staged = new ArrayList<>();
        List<GitFileChangeDTO> unstaged = new ArrayList<>();
        List<GitFileChangeDTO> untracked = new ArrayList<>();

        if (raw != null && !raw.trim().isEmpty()) {
            for (String line : raw.split("\n")) {
                if (line.trim().isEmpty() || line.length() < 3) continue;

                String indexStatus = String.valueOf(line.charAt(0));
                String workStatus  = String.valueOf(line.charAt(1));
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

    // ==================== 工具方法 ====================

    private String resolveWorkspace(String workspaceHash) {
        String workspacePath = agentService.resolveWorkspacePath(workspaceHash);
        if (workspacePath == null) workspacePath = agentService.getWorkspace();
        if (workspacePath == null) throw new ServiceException("未设置工作区");
        return workspacePath;
    }

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
