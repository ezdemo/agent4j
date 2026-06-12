package site.sorghum.agent4j.web.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.noear.solon.annotation.*;
import site.sorghum.agent4j.web.model.*;
import site.sorghum.agent4j.web.service.SnapshotService;

import java.util.ArrayList;
import java.util.List;

/**
 * 快照检查点 API —— 提供工作区快照创建、撤回和查询能力。
 * <p>
 * 快照基于 Git 底层命令实现，不污染用户的分支和提交历史：
 * <ul>
 *   <li>createCheckpoint —— AI 修改前保存当前工作区状态</li>
 *   <li>rollback —— 撤回 AI 修改，恢复到快照时的状态</li>
 *   <li>list —— 列出所有快照</li>
 * </ul>
 * </p>
 *
 * @author Sorghum
 */
@Api(tags = "快照检查点")
@Controller
@Mapping("/api/snapshots")
public class SnapshotController {

    @Inject
    private SnapshotService snapshotService;

    @ApiOperation(value = "创建快照检查点",
            notes = "在 AI 执行代码修改前保存当前工作区状态，基于 Git 底层命令实现，不影响提交历史")
    @Post
    @Mapping("/checkpoint")
    public ApiResponse<SnapshotDTO> createCheckpoint(
            @ApiParam(value = "工作区 hash") @Param(value = "workspaceHash", required = false) String workspaceHash,
            @ApiParam(value = "消息 ID（用于标识快照）") @Param("msgId") String msgId) {
        if (msgId == null || msgId.trim().isEmpty()) {
            return ApiResponse.fail("msgId 不能为空");
        }
        SnapshotService.SnapshotInfo info = snapshotService.createCheckpoint(workspaceHash, msgId.trim());
        return ApiResponse.ok(new SnapshotDTO(info.getMsgId(), info.getCommitHash(), info.getTreeHash(), info.getCreatedAt()));
    }

    @ApiOperation(value = "撤回到快照",
            notes = "恢复工作区到指定消息的快照状态，撤回该消息及之后所有 AI 修改")
    @Post
    @Mapping("/rollback")
    public ApiResponse<SnapshotRollbackDTO> rollback(
            @ApiParam(value = "工作区 hash") @Param(value = "workspaceHash", required = false) String workspaceHash,
            @ApiParam(value = "要撤回的消息 ID") @Param("msgId") String msgId) {
        if (msgId == null || msgId.trim().isEmpty()) {
            return ApiResponse.fail("msgId 不能为空");
        }
        SnapshotService.SnapshotRollbackResult result = snapshotService.rollbackToSnapshot(workspaceHash, msgId.trim());
        return ApiResponse.ok(new SnapshotRollbackDTO(
                result.getMsgId(), result.getCommitHash(), result.getTreeHash(),
                result.isSuccess(), result.getMessage()));
    }

    @ApiOperation(value = "列出快照", notes = "列出当前工作区的所有快照检查点")
    @Get
    @Mapping("")
    public ApiResponse<List<SnapshotDTO>> list(
            @ApiParam(value = "工作区 hash") @Param(value = "workspaceHash", required = false) String workspaceHash,
            @ApiParam(value = "会话名称") @Param(value = "sessionName", required = false) String sessionName) {
        List<SnapshotService.SnapshotInfo> snapshots = snapshotService.listSnapshots(workspaceHash, sessionName);
        List<SnapshotDTO> dtos = new ArrayList<>();
        for (SnapshotService.SnapshotInfo info : snapshots) {
            dtos.add(new SnapshotDTO(info.getMsgId(), info.getCommitHash(), info.getTreeHash(), info.getCreatedAt()));
        }
        return ApiResponse.ok(dtos);
    }

    @ApiOperation(value = "检查 Git 仓库状态", notes = "检查当前工作区是否为 Git 仓库（创建快照的前提条件）")
    @Get
    @Mapping("/status")
    public ApiResponse<SnapshotStatusDTO> status(
            @ApiParam(value = "工作区 hash") @Param(value = "workspaceHash", required = false) String workspaceHash) {
        boolean isGitRepo = snapshotService.isGitRepo(workspaceHash);
        return ApiResponse.ok(new SnapshotStatusDTO(isGitRepo));
    }

    @ApiOperation(value = "删除快照", notes = "删除指定消息的快照引用")
    @Delete
    @Mapping("/{msgId}")
    public ApiResponse<String> delete(
            @ApiParam(value = "消息 ID") @Path("msgId") String msgId,
            @ApiParam(value = "工作区 hash") @Param(value = "workspaceHash", required = false) String workspaceHash) {
        snapshotService.deleteSnapshot(workspaceHash, msgId);
        return ApiResponse.ok("快照已删除");
    }
}
