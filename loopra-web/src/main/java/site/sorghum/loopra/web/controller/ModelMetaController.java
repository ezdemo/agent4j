package site.sorghum.loopra.web.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.annotation.Post;
import site.sorghum.loopra.web.model.ApiResponse;
import site.sorghum.loopra.web.service.ModelMetaService;

/**
 * 模型元数据 API 控制器。
 * <p>
 * 提供模型元数据缓存的强制刷新功能。
 * </p>
 *
 * @author Sorghum
 */
@Api(tags = "模型元数据")
@Controller
@Mapping("/api/model-meta")
public class ModelMetaController {

    @Inject
    private ModelMetaService modelMetaService;

    @ApiOperation(value = "强制刷新模型元数据缓存", notes = "重新从远程 API 下载模型元数据并保存到本地，然后重新解析")
    @Post
    @Mapping("/refresh")
    public ApiResponse<String> refresh() {
        boolean success = modelMetaService.refreshModelMeta();
        if (success) {
            return ApiResponse.ok("模型元数据缓存已成功刷新");
        } else {
            return ApiResponse.fail("刷新模型元数据缓存失败，请查看日志");
        }
    }
}