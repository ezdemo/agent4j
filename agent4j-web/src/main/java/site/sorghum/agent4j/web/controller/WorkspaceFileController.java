package site.sorghum.agent4j.web.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Get;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.annotation.Param;
import site.sorghum.agent4j.web.model.ApiResponse;
import site.sorghum.agent4j.web.model.WorkspaceFileEntryDTO;
import site.sorghum.agent4j.web.service.WorkspaceFileService;

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
}
