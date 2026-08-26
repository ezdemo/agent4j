package site.sorghum.loopra.web.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.noear.solon.annotation.Body;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Get;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.annotation.Path;
import org.noear.solon.annotation.Post;
import site.sorghum.loopra.integration.extpack.ExtPackStore;
import site.sorghum.loopra.integration.extpack.ExtPackStore.ExtPackView;
import site.sorghum.loopra.integration.extpack.ExtPackStore.InstalledExtPack;
import site.sorghum.loopra.web.common.ServiceException;
import site.sorghum.loopra.web.model.ApiResponse;

import java.util.List;

/** Solon H-SPI 拓展包管理 API（与 cutin 插件 {@code /api/plugins/*} 并存）。 */
@Api(tags = "拓展包管理")
@Controller
@Mapping("/api/extpacks")
public class ExtPackController {

    /** 拓展包仓库（无状态，按需创建）。 */
    private final ExtPackStore extPackStore = new ExtPackStore();

    @ApiOperation("列出全部已安装拓展包及运行状态")
    @Get
    @Mapping("")
    public ApiResponse<List<ExtPackView>> list() {
        return ApiResponse.ok(extPackStore.list());
    }

    @ApiOperation(value = "从 JAR 直链安装拓展包", notes = "下载校验后启动，并同步桥接到全部存活 AgentLoop；持久化到 ~/.loopra/extpacks/extpacks.json")
    @Post
    @Mapping("/install")
    public ApiResponse<InstalledExtPack> install(@Body InstallRequest request) {
        if (request == null || request.source == null || request.source.isBlank()) {
            throw new ServiceException("source 不能为空（仅支持以 .jar 结尾的 http(s) 直链或本地路径）");
        }
        try {
            return ApiResponse.ok(extPackStore.install(request.source));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ServiceException("安装失败: " + exception.getMessage());
        }
    }

    @ApiOperation("启动拓展包（加载 Solon 容器并桥接 Agent 能力）")
    @Post
    @Mapping("/{id}/start")
    public ApiResponse<ExtPackView> start(@Path("id") String id) {
        try {
            extPackStore.start(id);
            return ApiResponse.ok(view(id));
        } catch (IllegalArgumentException exception) {
            throw new ServiceException("拓展包不存在: " + id);
        } catch (RuntimeException exception) {
            throw new ServiceException("启动失败: " + exception.getMessage());
        }
    }

    @ApiOperation("停止拓展包（注销 Agent 桥接并卸载 Solon 容器）")
    @Post
    @Mapping("/{id}/stop")
    public ApiResponse<ExtPackView> stop(@Path("id") String id) {
        try {
            extPackStore.stop(id);
            return ApiResponse.ok(view(id));
        } catch (IllegalArgumentException exception) {
            throw new ServiceException("拓展包不存在: " + id);
        }
    }

    @ApiOperation("卸载拓展包（停止、删除本地 jar 并更新清单）")
    @Post
    @Mapping("/{id}/uninstall")
    public ApiResponse<String> uninstall(@Path("id") String id) {
        try {
            extPackStore.uninstall(id);
            return ApiResponse.ok("拓展包已卸载: " + id);
        } catch (IllegalArgumentException exception) {
            throw new ServiceException("拓展包不存在: " + id);
        }
    }

    @ApiOperation("启用/停用拓展包")
    @Post
    @Mapping("/{id}/toggle")
    public ApiResponse<InstalledExtPack> toggle(@Path("id") String id, @Body ToggleRequest request) {
        if (request == null) {
            throw new ServiceException("请求体不能为空");
        }
        try {
            return ApiResponse.ok(extPackStore.setEnabled(id, request.enabled));
        } catch (IllegalArgumentException exception) {
            throw new ServiceException("拓展包不存在: " + id);
        }
    }

    private ExtPackView view(String id) {
        return extPackStore.list().stream()
            .filter(view -> view.id().equals(id))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("unknown extpack: " + id));
    }

    /** 拓展包安装请求体。 */
    public static class InstallRequest {
        /** 插件 jar 直链（以 .jar 结尾）或本地 jar 路径。 */
        public String source;
    }

    /** 启停请求体。 */
    public static class ToggleRequest {
        public boolean enabled;
    }
}
