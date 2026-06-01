package site.sorghum.agent4j.bin;

import org.noear.solon.Solon;
import org.noear.solon.annotation.Import;
import org.noear.solon.annotation.SolonMain;
import site.sorghum.agent4j.bin.agent.Agent4jAgent;
import site.sorghum.agent4j.bin.agent.ConsoleAgentOutput;
import site.sorghum.agent4j.bin.command.ChatCommandRegistry;
import site.sorghum.agent4j.bin.config.Agent4jConfig;

import java.util.Scanner;

/**
 * Agent4j 入口——纯 Java AI Agent。
 * <p>
 * 命令处理已抽象为 {@link site.sorghum.agent4j.bin.command.ChatCommand} 接口，通过 Solon IoC
 * 自动发现和注册。新增命令只需实现 {@code ChatCommand} 并标注
 * {@link org.noear.solon.annotation.Component @Component} 即可。
 * </p>
 *
 * @author Sorghum
 */
@Import(scanPackages = {"site.sorghum.agent4j"})
@SolonMain
public class Agent4jApp {

    public static void main(String[] args) throws Throwable {
        // 0. 启动 Solon IoC 容器
        Solon.start(Agent4jApp.class, args);

        // 1. 加载配置
        Agent4jConfig config;
        try {
            config = Agent4jConfig.load();
        } catch (Exception e) {
            System.err.println("无法加载配置: " + e.getMessage());
            System.exit(1);
            return;
        }

        String apiUrl = envOr("OPENAI_BASE_URL", config.chatApiUrl());
        String apiKey = envOr("OPENAI_API_KEY", config.apiKey());
        String model = envOr("MODEL", config.model());

        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("未配置 apiKey");
            System.exit(1);
        }

        // 2. 获取命令注册表（Solon IoC 自动收集所有 ChatCommand Bean）
        ChatCommandRegistry cmdRegistry = Solon.context().getBean(ChatCommandRegistry.class);

        printBanner(apiUrl, apiKey, model, config, cmdRegistry);

        Agent4jAgent agent = Agent4jAgent.builder()
                .config(config)
                .apiUrl(apiUrl)
                .apiKey(apiKey)
                .model(model)
                .workspace(config.workspaceDir())
                .commandRegistry(cmdRegistry)
                .build();

        // 设置输出接口（控制台输出）
        agent.setOutput(new ConsoleAgentOutput());

        // 注册事件监听：追踪 token 用量
        final ConsoleUsageListener usageListener = new ConsoleUsageListener(agent);
        agent.setListener(usageListener);
        final int[] lastUsage = usageListener.getLastUsage();

        try (Scanner scanner = new Scanner(System.in, "UTF-8")) {
            while (true) {
                System.out.print("[" + agent.historySize() + "] > ");
                if (!scanner.hasNextLine()) break;
                String input = scanner.nextLine().trim();

                if (input.isEmpty()) continue;

                // 所有输入统一交给 agent.chat() ——
                // "/" 开头的命令通过 ChatCommandRegistry 自动路由分发，
                // 非命令消息转发到 LLM 推理循环。
                try {
                    long t0 = System.currentTimeMillis();
                    String reply = agent.chat(input);
                    long elapsed = System.currentTimeMillis() - t0;

                    // 检查退出信号（命令返回 EXIT 时设置）
                    if (agent.isTerminated()) break;

                    // 命令已自行通过 System.out 输出内容，此处不重复打印
                    System.out.println();
                    // HITL 待审批时跳过打印：interceptForHITL/interceptForSandboxHITL 已通过
                    // output.onContentDelta() 输出过 HITL 消息，此处不应重复输出
                    if (reply != null && !reply.isEmpty() && !"/exit".equals(reply)
                            && !agent.hasPendingHITL()) {
                        System.out.println(reply);
                    } else if ((reply == null || reply.isEmpty()) && !input.startsWith("/")) {
                        // 只有非命令输入返回空时才提示可能的 API 异常
                        System.out.println("(模型返回空内容，可能 API 异常或模型选择有误)");
                    }

                    if (lastUsage[2] > 0) {
                        printUsage(agent, lastUsage, elapsed);
                        resetUsage(lastUsage);
                        agent.saveUsage();
                        agent.flushSession();
                    }
                } catch (Exception e) {
                    System.err.println("错误: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    // ========== 静态辅助方法 ==========

    private static void printBanner(String apiUrl, String apiKey, String model,
                                    Agent4jConfig config, ChatCommandRegistry cmdRegistry) {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║          Agent4j — 代码助手               ║");
        System.out.println("╠══════════════════════════════════════════╣");
        System.out.println("║ 模型: " + padRight(model, 27) + "║");
        String wsDisplay = config.workspaceDir() != null ? config.workspaceDir().toString() : "(未设置)";
        System.out.println("║ 工作区: " + padRight(truncate(wsDisplay, 25), 25) + "║");
        System.out.println("║ API: " + padRight(truncate(apiUrl, 25), 25) + "║");
        System.out.println("╠══════════════════════════════════════════╣");
        System.out.println("║ /help 查看命令列表       /exit 退出       ║");
        System.out.println("║ HITL: " + padRight(config.hitl() ? "开启 (工具调用需审批)" : "关闭", 33) + "║");
        System.out.println("╚══════════════════════════════════════════╝");
        System.out.println();
    }

    private static void printUsage(Agent4jAgent agent, int[] usage, long elapsedMs) {
        if (usage[2] <= 0) return;
        int cacheTotal = usage[3] + usage[4];
        String cacheStr = cacheTotal > 0
                ? " | cache: " + usage[3] + " hit + " + usage[4] + " miss"
                + " (" + (usage[3] * 100 / cacheTotal) + "%)"
                : "";
        System.out.println("[" + (elapsedMs / 1000.0) + "s]"
                + " | in=" + usage[0] + " out=" + usage[1] + " total=" + usage[2] + cacheStr);

        // 会话累计
        long[] sess = agent.getSessionUsage();
        long sessCacheTotal = sess[2] + sess[3];
        String sessCacheStr = sessCacheTotal > 0
                ? " | cache: " + sess[2] + " hit + " + sess[3] + " miss"
                + " (" + (sess[2] * 100 / sessCacheTotal) + "%)"
                : "";
        System.out.println("       [会话累计 in=" + sess[0] + " out=" + sess[1] + sessCacheStr + "]");
    }

    private static void resetUsage(int[] usage) {
        usage[0] = usage[1] = usage[2] = usage[3] = usage[4] = 0;
    }

    private static String envOr(String key, String fallback) {
        String v = System.getenv(key);
        return (v != null && !v.isEmpty()) ? v : fallback;
    }

    private static String padRight(String s, int width) {
        if (s == null) return "";
        if (s.length() >= width) return s;
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) sb.append(' ');
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : "..." + s.substring(s.length() - max + 3);
    }
}
