package site.sorghum.agent4j.web;

import org.noear.solon.Solon;
import org.noear.solon.annotation.Import;
import org.noear.solon.annotation.SolonMain;

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
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║    Agent4j Web API  — 已启动             ║");
        System.out.println("║    http://localhost:8080                 ║");
        System.out.println("║    /api/chat       — 聊天接口            ║");
        System.out.println("║    /api/sessions   — 会话管理            ║");
        System.out.println("║    /api/tools      — 工具管理            ║");
        System.out.println("║    /api/agent      — Agent 控制          ║");
        System.out.println("║    /api/config     — 配置查询            ║");
        System.out.println("╚══════════════════════════════════════════╝");
    }
}
