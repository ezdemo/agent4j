package site.sorghum.loopra.web.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.noear.solon.annotation.*;
import site.sorghum.loopra.bin.mcp.McpManageService;
import site.sorghum.loopra.bin.mcp.McpServerDTO;
import site.sorghum.loopra.bin.mcp.McpToolListDTO;
import site.sorghum.loopra.web.common.ServiceException;
import site.sorghum.loopra.web.model.ApiResponse;

import java.util.List;

/**
 * MCP 服务器管理控制器 —— 提供 MCP 服务器的增删改查、启停、连接检测及工具权限管理。
 * <p>
 * 接口设计参考 SolonCode MCP 管理功能文档，适配 loopra REST API 风格。
 * </p>
 *
 * @author Sorghum
 */
@Api(tags = "MCP 服务器管理")
@Controller
@Mapping("/api/mcp")
public class McpController {

    @Inject
    private McpManageService mcpManageService;

    // ==================== 服务器列表 ====================

    @ApiOperation(value = "获取所有 MCP 服务器", notes = "返回所有已注册的 MCP 服务器配置列表")
    @Get
    @Mapping("/servers")
    public ApiResponse<List<McpServerDTO>> listServers() {
        return ApiResponse.ok(mcpManageService.listServers());
    }

    // ==================== 新增服务器 ====================

    @ApiOperation(value = "新增 MCP 服务器", notes = "新增一个 MCP 服务器配置，支持 stdio / sse / streamable 三种连接类型")
    @Post
    @Mapping("/servers/add")
    public ApiResponse<McpServerDTO> addServer(@ApiParam(value = "MCP 服务器配置") @Body McpServerDTO server) {
        // 校验必填字段
        validateServer(server, false);
        McpServerDTO result = mcpManageService.addServer(server);
        return ApiResponse.ok(result);
    }

    // ==================== 更新服务器 ====================

    @ApiOperation(value = "更新 MCP 服务器", notes = "更新已有 MCP 服务器配置，可通过 originalName 指定原名称以便重命名")
    @Post
    @Mapping("/servers/update")
    public ApiResponse<McpServerDTO> updateServer(@ApiParam(value = "更新请求体") @Body McpUpdateRequest request) {
        if (request.server == null) {
            throw new ServiceException("server 不能为空");
        }
        validateServer(request.server, true);
        McpServerDTO result = mcpManageService.updateServer(request.originalName, request.server);
        return ApiResponse.ok(result);
    }

    // ==================== 删除服务器 ====================

    @ApiOperation(value = "删除 MCP 服务器", notes = "根据名称删除一个 MCP 服务器配置")
    @Post
    @Mapping("/servers/remove")
    public ApiResponse<String> removeServer(@ApiParam(value = "{\"name\":\"...\"}") @Body McpNameRequest request) {
        if (request.name == null || request.name.isBlank()) {
            throw new ServiceException("名称不能为空");
        }
        mcpManageService.removeServer(request.name);
        return ApiResponse.ok("服务器已删除");
    }

    // ==================== 启停控制 ====================

    @ApiOperation(value = "启用/禁用 MCP 服务器", notes = "切换 MCP 服务器的启用状态，实时生效无需保存")
    @Post
    @Mapping("/servers/toggle")
    public ApiResponse<McpServerDTO> toggleServer(
            @ApiParam @Body McpToggleRequest request) {
        if (request.name == null || request.name.isBlank()) {
            throw new ServiceException("名称不能为空");
        }
        McpServerDTO result = mcpManageService.toggleServer(request.name, request.enabled);
        if (result == null) {
            throw new ServiceException("未找到指定服务器: " + request.name);
        }
        return ApiResponse.ok(result);
    }

    // ==================== 连接检测 ====================

    @ApiOperation(value = "检测 MCP 服务器连接", notes = "基于当前表单输入实时组装配置，测试 MCP 服务器是否可达（超时 15 秒）")
    @Post
    @Mapping("/servers/check")
    public ApiResponse<String> checkConnection(@ApiParam(value = "MCP 服务器配置（同新增表单）") @Body McpServerDTO server) {
        validateServer(server, false);
        boolean ok = mcpManageService.checkConnection(server);
        if (ok) {
            return ApiResponse.ok("连接成功");
        } else {
            return ApiResponse.fail("连接失败，请检查服务器配置");
        }
    }

    // ==================== 工具列表 ====================

    @ApiOperation(value = "查看服务器工具列表", notes = "获取指定 MCP 服务器暴露的所有工具及当前权限状态")
    @Get
    @Mapping("/servers/tools")
    public ApiResponse<McpToolListDTO> listTools(
            @ApiParam(value = "服务器名称", required = true) @Param("name") String name) {
        if (name == null || name.isBlank()) {
            throw new ServiceException("名称不能为空");
        }
        return ApiResponse.ok(mcpManageService.listTools(name));
    }

    // ==================== 保存工具权限 ====================

    @ApiOperation(value = "保存工具权限", notes = "保存指定 MCP 服务器的工具启用/禁用配置")
    @Post
    @Mapping("/servers/tools/save")
    public ApiResponse<String> saveToolPermissions(
            @ApiParam(value = "工具权限保存请求") @Body McpToolsSaveRequest request) {
        if (request.serverName == null || request.serverName.isBlank()) {
            throw new ServiceException("serverName 不能为空");
        }
        mcpManageService.saveToolPermissions(request.serverName, request.disallowedTools);
        return ApiResponse.ok("工具权限已保存");
    }

    // ==================== 内部校验 ====================

    /**
     * 校验 MCP 服务器配置的必填字段。
     */
    private void validateServer(McpServerDTO server, boolean isUpdate) {
        if (server.name == null || server.name.isBlank()) {
            throw new ServiceException("名称为必填项");
        }
        if (!server.name.matches("^[a-zA-Z0-9_-]+$")) {
            throw new ServiceException("名称仅允许字母、数字、下划线和连字符");
        }
        if (server.type == null || server.type.isBlank()) {
            throw new ServiceException("类型为必填项");
        }
        switch (server.type) {
            case "stdio":
                if (server.command == null || server.command.isBlank()) {
                    throw new ServiceException("命令为必填项");
                }
                break;
            case "sse":
            case "streamable":
                if (server.url == null || server.url.isBlank()) {
                    throw new ServiceException("URL 为必填项");
                }
                if (!server.url.startsWith("http://") && !server.url.startsWith("https://")) {
                    throw new ServiceException("URL 必须以 http:// 或 https:// 开头");
                }
                break;
            default:
                throw new ServiceException("无效的类型: " + server.type + "，仅支持 stdio / sse / streamable");
        }
    }

    // ==================== 请求体内部类 ====================

    public static class McpUpdateRequest {
        /** 原名称（编辑时用于查找，为空时按新增处理） */
        public String originalName;
        /** 新的服务器配置 */
        public McpServerDTO server;
    }

    public static class McpNameRequest {
        public String name;
    }

    public static class McpToggleRequest {
        public String name;
        public boolean enabled;
    }

    public static class McpToolsSaveRequest {
        public String serverName;
        public List<String> disallowedTools;
    }
}
