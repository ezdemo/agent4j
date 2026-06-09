package site.sorghum.agent4j.web.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.*;
import site.sorghum.agent4j.web.model.*;
import site.sorghum.agent4j.web.service.GitService;

import java.util.Map;

/**
 * Git 管理 API —— 提供 Git 状态查看、仓库初始化、暂存/取消暂存、提交、
 * Diff 内容查看及历史版本文件内容获取等完整的 Git 管理能力。
 * <p>
 * 业务逻辑已委托给 {@link GitService}，本控制器仅负责请求映射和参数绑定。
 * 异常由 {@link site.sorghum.agent4j.web.common.GlobalExceptionFilter} 统一处理。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
@Api(tags = "Git")
@Controller
@Mapping("/api/git")
public class GitController {

    @Inject
    private GitService gitService;

    // ==================== 查询端点 ====================

    @ApiOperation(value = "获取当前 Git 分支", notes = "返回工作区所在 Git 仓库的当前分支名称")
    @Get
    @Mapping("/branch")
    public ApiResponse<GitBranchDTO> branch(
            @ApiParam(value = "工作区 hash") @Param(value = "workspaceHash", required = false) String workspaceHash) {
        String branch = gitService.getBranch(workspaceHash);
        return ApiResponse.ok(new GitBranchDTO(branch));
    }

    @ApiOperation(value = "获取 Git 变更文件列表", notes = "返回暂存、未暂存和未跟踪的文件变更列表")
    @Get
    @Mapping("/diff")
    public ApiResponse<GitDiffDTO> diff(
            @ApiParam(value = "工作区 hash") @Param(value = "workspaceHash", required = false) String workspaceHash) {
        return ApiResponse.ok(gitService.getDiff(workspaceHash));
    }

    @ApiOperation(value = "Git 综合状态检测",
            notes = "依次检测 Git 是否安装、工作区是否初始化为 Git 仓库、获取当前分支名及变更文件列表")
    @Get
    @Mapping("/status")
    public ApiResponse<GitStatusDTO> status(
            @ApiParam(value = "工作区 hash")
            @Param(value = "workspaceHash", required = false) String workspaceHash) throws Exception {
        return ApiResponse.ok(gitService.getStatus(workspaceHash));
    }

    @ApiOperation(value = "获取 Git Diff 内容",
            notes = "返回完整的 unified diff 文本和 stat 变更统计摘要。单文件 diff 超过 2000 行时自动截断")
    @Get
    @Mapping("/diff-content")
    public ApiResponse<GitDiffContentDTO> diffContent(
            @ApiParam(value = "工作区 hash") @Param(value = "workspaceHash", required = false) String workspaceHash,
            @ApiParam(value = "可选的文件路径，用于查看指定文件的 diff")
            @Param(value = "path", required = false) String path) throws Exception {
        return ApiResponse.ok(gitService.getDiffContent(workspaceHash, path));
    }

    @ApiOperation(value = "获取 Git 仓库中指定版本的文件内容",
            notes = "通过 git show ref:path 获取文件内容，默认 ref 为 HEAD")
    @Get
    @Mapping("/file-content")
    public ApiResponse<GitFileContentDTO> fileContent(
            @ApiParam(value = "工作区 hash") @Param(value = "workspaceHash", required = false) String workspaceHash,
            @ApiParam(value = "文件路径（相对于仓库根目录）") @Param("path") String path,
            @ApiParam(value = "Git 引用（分支名、标签、提交哈希等），默认为 HEAD")
            @Param(value = "ref", required = false) String ref) throws Exception {
        return ApiResponse.ok(gitService.getFileContent(workspaceHash, path, ref));
    }

    // ==================== 操作端点 ====================

    @ApiOperation(value = "初始化 Git 仓库",
            notes = "在工作区执行 git init，自动生成 .gitignore 文件（仅当文件不存在时），可选执行初始提交")
    @Post
    @Mapping("/init")
    public ApiResponse<Map<String, Object>> init(
            @ApiParam(value = "工作区 hash") @Param(value = "workspaceHash", required = false) String workspaceHash,
            @ApiParam(value = "是否执行初始提交，默认为 false")
            @Param(value = "initialCommit", required = false) Boolean initialCommit) throws Exception {
        return ApiResponse.ok(gitService.initRepo(workspaceHash, initialCommit));
    }

    @ApiOperation(value = "Git 提交",
            notes = "支持精确文件列表或全量 add -A。请求体为 JSON：{ \"message\": \"...\", \"files\": [\"a.java\", \"b.css\"] }")
    @Post
    @Mapping("/commit")
    public ApiResponse<GitCommitResultDTO> commit(
            @ApiParam(value = "工作区 hash") @Param(value = "workspaceHash", required = false) String workspaceHash,
            @Body String body) throws Exception {
        return ApiResponse.ok(gitService.commit(workspaceHash, body));
    }

    @ApiOperation(value = "切换文件状态", notes = "未追踪文件变为变更状态，或变更文件变为未追踪状态")
    @Post
    @Mapping("/toggle")
    public ApiResponse<Map<String, Object>> toggle(
            @ApiParam(value = "工作区 hash") @Param(value = "workspaceHash", required = false) String workspaceHash,
            @Body String body) throws Exception {
        return ApiResponse.ok(gitService.toggleFile(workspaceHash, body));
    }

    // ==================== AI 辅助 ====================

    @ApiOperation(value = "AI 自动生成 Git 提交消息",
            notes = "根据当前 git diff 和近 10 条提交日志，调用 AI 生成符合风格的提交消息。支持传入 files 数组仅针对选中文件生成")
    @Post
    @Mapping("/generate-commit-message")
    public ApiResponse<GitGenerateMessageDTO> generateCommitMessage(
            @ApiParam(value = "工作区 hash") @Param(value = "workspaceHash", required = false) String workspaceHash,
            @Body String body) throws Exception {
        return ApiResponse.ok(gitService.generateCommitMessage(workspaceHash, body));
    }
}
