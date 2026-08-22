package site.sorghum.loopra.web.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.noear.solon.annotation.Body;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Get;
import org.noear.solon.annotation.Init;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.annotation.Path;
import org.noear.solon.annotation.Post;
import site.sorghum.loopra.bin.config.ConfigService;
import site.sorghum.loopra.integration.cutin.plugin.LoopraPluginRuntime;
import site.sorghum.loopra.integration.cutin.plugin.LoopraPluginRuntime.PluginView;
import site.sorghum.loopra.integration.cutin.plugin.external.ExternalPluginStore;
import site.sorghum.loopra.web.common.ServiceException;
import site.sorghum.loopra.web.model.ApiResponse;

import java.util.List;

/** Cutin/Loopra 插件运行时管理 API。 */
@Api(tags = "插件管理")
@Controller
@Mapping("/api/plugins")
public class PluginController {

    @Init
    public void init() {
        if (ConfigService.getConfig() != null) {
            LoopraPluginRuntime.configureDisabled(ConfigService.getConfig().disabledPlugins());
        }
    }

    @ApiOperation("列出全部插件及运行实例状态")
    @Get
    @Mapping("")
    public ApiResponse<List<PluginView>> list() {
        return ApiResponse.ok(LoopraPluginRuntime.plugins());
    }

    @ApiOperation("在线启用或停用插件")
    @Post
    @Mapping("/{id}/toggle")
    public ApiResponse<PluginView> toggle(@Path("id") String id, @Body ToggleRequest request) {
        if (request == null) {
            throw new ServiceException("请求体不能为空");
        }
        try {
            PluginView plugin = LoopraPluginRuntime.setEnabled(id, request.enabled);
            try {
                ConfigService.setDisabledPlugins(LoopraPluginRuntime.disabledPluginIds());
            } catch (RuntimeException persistFailure) {
                LoopraPluginRuntime.setEnabled(id, !request.enabled);
                throw persistFailure;
            }
            return ApiResponse.ok(plugin);
        } catch (IllegalArgumentException exception) {
            throw new ServiceException("插件不存在: " + id);
        } catch (RuntimeException exception) {
            throw new ServiceException("切换插件失败: " + exception.getMessage());
        }
    }

    public static class ToggleRequest {
        public boolean enabled;
    }

    // ==================== 外置插件管理 ====================

    /** 外置插件仓库（无状态，按需创建）。 */
    private final ExternalPluginStore externalStore = new ExternalPluginStore();

    @ApiOperation("列出已安装的外置插件")
    @Get
    @Mapping("/external")
    public ApiResponse<List<ExternalPluginStore.InstalledPlugin>> listExternal() {
        return ApiResponse.ok(externalStore.installed());
    }

    @ApiOperation(value = "从 JAR 直链安装外置插件", notes = "下载后热注册到全部存活 AgentLoop，并持久化到 ~/.loopra/plugins/installed.json")
    @Post
    @Mapping("/external/install")
    public ApiResponse<ExternalPluginStore.InstalledPlugin> installExternal(@Body InstallRequest request) {
        if (request == null || request.source == null || request.source.isBlank()) {
            throw new ServiceException("source 不能为空（仅支持以 .jar 结尾的 http(s) 直链）");
        }
        try {
            return ApiResponse.ok(externalStore.install(request.source));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ServiceException("安装失败: " + exception.getMessage());
        }
    }

    @ApiOperation(value = "卸载外置插件", notes = "从全部存活 AgentLoop 注销、删除本地 jar 并更新清单")
    @Post
    @Mapping("/external/{id}/remove")
    public ApiResponse<String> removeExternal(@Path("id") String id) {
        try {
            externalStore.uninstall(id);
            return ApiResponse.ok("插件已卸载: " + id);
        } catch (IllegalArgumentException exception) {
            throw new ServiceException("插件不存在: " + id);
        }
    }

    @ApiOperation("启用/停用外置插件")
    @Post
    @Mapping("/external/{id}/toggle")
    public ApiResponse<ExternalPluginStore.InstalledPlugin> toggleExternal(
            @Path("id") String id, @Body ToggleRequest request) {
        if (request == null) {
            throw new ServiceException("请求体不能为空");
        }
        try {
            return ApiResponse.ok(externalStore.setEnabled(id, request.enabled));
        } catch (IllegalArgumentException exception) {
            throw new ServiceException("插件不存在: " + id);
        }
    }

    /** 外置插件安装请求体。 */
    public static class InstallRequest {
        /** 插件 jar 直链（以 .jar 结尾）。 */
        public String source;
    }
}
