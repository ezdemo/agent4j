package site.sorghum.loopra.web.controller;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;

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
