package site.sorghum.loopra.web.controller;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;

/** 前端启动所需的静态配置接口。 */
@Controller
public class StaticController {

    @Mapping("/config.json")
    public void configJson(){
        Context current = Context.current();
        current.outputAsJson("""
                {
                  "apiBase": "/"
                }
                """);
    }
}
