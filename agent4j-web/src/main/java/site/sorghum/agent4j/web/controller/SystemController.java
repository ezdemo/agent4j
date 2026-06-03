package site.sorghum.agent4j.web.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Get;
import org.noear.solon.annotation.Mapping;
import site.sorghum.agent4j.web.model.ApiResponse;
import site.sorghum.agent4j.web.model.SystemHealthDTO;
import site.sorghum.agent4j.web.model.SystemVersionDTO;

/**
 * 系统管理端点 —— 健康检查、版本查询。
 *
 * @author Sorghum
 */
@Api(tags = "系统")
@Controller
@Mapping("/api/system")
public class SystemController {

    private static final String VERSION = "1.0-SNAPSHOT";
    private static final String BUILD_TIME = "2025-01-01";

    @ApiOperation(value = "健康检查", notes = "返回服务是否正常运行，无需 Agent 初始化即可访问")
    @Get
    @Mapping("/health")
    public ApiResponse<SystemHealthDTO> health() {
        return ApiResponse.ok(new SystemHealthDTO("ok", VERSION, BUILD_TIME));
    }

    @ApiOperation(value = "获取版本信息", notes = "返回当前 Agent4j 的版本号和构建时间")
    @Get
    @Mapping("/version")
    public ApiResponse<SystemVersionDTO> version() {
        return ApiResponse.ok(new SystemVersionDTO(VERSION, BUILD_TIME, "Agent4j"));
    }
}
