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

    /**
     * 健康检查 — 不需要版本注入，直接返回固定状态。
     */
    @ApiOperation(value = "健康检查", notes = "返回服务是否正常运行，无需 Agent 初始化即可访问")
    @Get
    @Mapping("/health")
    public ApiResponse<SystemHealthDTO> health() {
        String version = readVersion();
        return ApiResponse.ok(new SystemHealthDTO("ok", version, ""));
    }

    /**
     * 获取版本信息 — 从 Solon 配置读取 Maven 注入的版本号。
     */
    @ApiOperation(value = "获取版本信息", notes = "返回当前 Agent4j 的运行版本和名称")
    @Get
    @Mapping("/version")
    public ApiResponse<SystemVersionDTO> version() {
        String version = readVersion();
        String name = org.noear.solon.Solon.cfg().get("solon.app.name");
        return ApiResponse.ok(new SystemVersionDTO(version, "", name));
    }

    /**
     * 从 Solon 配置读取版本号（由 Maven 资源过滤从 pom.xml 注入）。
     */
    private String readVersion() {
        String v = org.noear.solon.Solon.cfg().get("solon.app.version");
        return (v != null && !v.isEmpty()) ? v : "unknown";
    }
}
