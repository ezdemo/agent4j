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
}
