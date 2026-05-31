package site.sorghum.agent4j.web.controller;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Get;
import org.noear.solon.annotation.Mapping;

import site.sorghum.agent4j.web.model.ApiResponse;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统管理端点 —— 健康检查、版本查询。
 * <p>
 * 这些端点不依赖 Agent 初始化，在服务未就绪时也能访问，
 * 供前端连接设置页用于检测后端可达性。
 * </p>
 *
 * @author Sorghum
 */
@Controller
@Mapping("/api/system")
public class SystemController {

    private static final String VERSION = "1.0-SNAPSHOT";
    private static final String BUILD_TIME = "2025-01-01";

    /** 健康检查 —— GET /api/system/health */
    @Get
    @Mapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "ok");
        data.put("version", VERSION);
        data.put("buildTime", BUILD_TIME);
        return ApiResponse.ok(data);
    }

    /** 获取版本信息 —— GET /api/system/version */
    @Get
    @Mapping("/version")
    public ApiResponse<Map<String, Object>> version() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("version", VERSION);
        data.put("buildTime", BUILD_TIME);
        data.put("name", "Agent4j");
        return ApiResponse.ok(data);
    }
}
