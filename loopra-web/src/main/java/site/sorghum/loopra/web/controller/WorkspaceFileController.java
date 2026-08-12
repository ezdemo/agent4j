package site.sorghum.loopra.web.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.noear.solon.annotation.*;
import site.sorghum.loopra.web.model.ApiResponse;
import site.sorghum.loopra.web.model.WorkspaceFileEntryDTO;
import site.sorghum.loopra.web.service.WorkspaceFileService;

import java.util.List;

/** 工作区文件浏览 API。 */
@Api(tags = "工作区文件")
@Controller
@Mapping("/api/files")
public class WorkspaceFileController {

    @Inject
    private WorkspaceFileService workspaceFileService;

    @ApiOperation(value = "获取工作区目录项", notes = "仅返回指定目录的直接子项，前端按需加载子目录")
    @Get
    @Mapping("/tree")
    public ApiResponse<List<WorkspaceFileEntryDTO>> tree(
            @ApiParam(value = "工作区 hash", required = true) @Param(value = "workspaceHash", required = true) String workspaceHash,
            @ApiParam(value = "相对于工作区的目录路径") @Param(value = "path", required = false) String path) {
        return ApiResponse.ok(workspaceFileService.list(workspaceHash, path));
    }

    @ApiOperation(value = "搜索工作区文件", notes = "仅返回工作区内的普通文件，供聊天输入框 @ 引用")
    @Get
    @Mapping("/search")
    public ApiResponse<List<WorkspaceFileEntryDTO>> search(
            @ApiParam(value = "工作区 hash", required = true) @Param(value = "workspaceHash", required = true) String workspaceHash,
            @ApiParam(value = "文件名或路径关键字") @Param(value = "query", required = false) String query) {
        return ApiResponse.ok(workspaceFileService.search(workspaceHash, query));
    }

    @ApiOperation(value = "删除工作区文件或目录", notes = "递归删除目录；禁止删除工作区根目录")
    @Delete
    @Mapping("/delete")
    public ApiResponse<String> delete(
            @ApiParam(value = "工作区 hash", required = true) @Param(value = "workspaceHash", required = true) String workspaceHash,
            @ApiParam(value = "相对于工作区的文件或目录路径", required = true) @Param(value = "path", required = true) String path) {
        workspaceFileService.delete(workspaceHash, path);
        return ApiResponse.ok("已删除");
    }
}
