package site.sorghum.agent4j.web;

import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import org.noear.solon.Solon;
import org.noear.solon.annotation.Import;
import org.noear.solon.annotation.SolonMain;
import org.noear.solon.web.cors.CrossFilter;
import site.sorghum.agent4j.bin.acp.AcpBootstrap;

import java.lang.reflect.Array;
import java.util.Arrays;

/**
 * Agent4j Web 入口 —— 通过 REST API 暴露全部 Agent 功能。
 * <p>
 * 启动参数：
 * <ul>
 *   <li><code>--agent4j.acp=true</code> 启用 ACP 协议支持（stdio 模式）</li>
 *   <li><code>--agent4j.acp.ws.port=8765</code> 启用 ACP WebSocket 模式（需 acp-websocket-jetty）</li>
 * </ul>
 * </p>
 *
 * @author Sorghum
 */
@Import(scanPackages = {"site.sorghum.agent4j"})
@SolonMain
public class Agent4jWebApp {

    public static void main(String[] args) {
        System.out.println(
                "args:" + Arrays.toString(args)
        );

        // 启动 Solon Web 服务
        Solon.start(Agent4jWebApp.class, args);

        // 全局 CORS 处理（优先级 -1 确保最先执行）
        Solon.app().router().filter(-1, new CrossFilter().allowedOrigins("*"));

        System.out.printf("""
                [Web Interface]: http://127.0.0.1:%s/index.html
                %n""", Solon.cfg().serverPort());

        // ==================== ACP 协议支持 ====================
        // 通过 --agent4j.acp=true 启用
        boolean acpEnabled = Solon.cfg().getBool("agent4j.acp", false);
        if (!acpEnabled) {
            return; // ACP 未启用，Web 服务正常启动后返回
        }

        // 检测 WebSocket 端口配置
        int acpWsPort = Solon.cfg().getInt("agent4j.acp.ws.port", 0);

        try {
            if (acpWsPort > 0) {
                // WebSocket 模式（远程连接）
                System.out.println("[ACP] WebSocket 模式启动，端口: " + acpWsPort);
                AcpBootstrap.startWebSocket(acpWsPort);
            } else {
                // Stdio 模式（子进程通信）
                System.out.println("[ACP] Stdio 模式启动，等待客户端连接...");
                AcpBootstrap bootstrap = new AcpBootstrap(new StdioAcpAgentTransport());
                bootstrap.start(); // 非阻塞启动
                System.out.println("[ACP] ACP Agent 已在后台运行");
            }
        } catch (Exception e) {
            System.err.println("[ACP] 启动失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
