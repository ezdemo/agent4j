package site.sorghum.agent4j.web;

import org.noear.solon.Solon;
import org.noear.solon.annotation.Import;
import org.noear.solon.annotation.SolonMain;
import org.noear.solon.web.cors.CrossFilter;

/**
 * Agent4j Web 入口 —— 通过 REST API 暴露全部 Agent 功能。
 *
 * @author Sorghum
 */
@Import(scanPackages = {"site.sorghum.agent4j"})
@SolonMain
public class Agent4jWebApp {

    public static void main(String[] args) {
        Solon.start(Agent4jWebApp.class, args);

        // 全局 CORS 处理（优先级 -1 确保最先执行）
        Solon.app().router().filter(-1, new CrossFilter().allowedOrigins("*"));
        System.out.printf("""
                http://127.0.0.1:%s/index.html
                %n""", Solon.cfg().serverPort());
    }
}
