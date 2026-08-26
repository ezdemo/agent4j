package site.sorghum.loopra.extra.demo;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Get;
import org.noear.solon.annotation.Mapping;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * 示例路由：演示拓展包自带的 Solon 容器能力。
 *
 * <p>由 {@link DemoExtPackPlugin} 在 start 时通过 {@code beanMake} 挂载到宿主，
 * 安装并启动拓展包后可直接访问：</p>
 * <ul>
 *   <li>GET /api/ext-demo/health —— 存活探测</li>
 *   <li>GET /api/ext-demo/time —— 服务器时间</li>
 * </ul>
 */
@Controller
@Mapping("/api/ext-demo")
public class DemoExtController {

    @Get
    @Mapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "ok",
            "extpack", "loopra-extra-demo",
            "version", "1.0.0");
    }

    @Get
    @Mapping("/time")
    public Map<String, Object> time() {
        return Map.of(
            "serverTime", LocalDateTime.now().toString(),
            "zone", ZoneId.systemDefault().getId());
    }
}
