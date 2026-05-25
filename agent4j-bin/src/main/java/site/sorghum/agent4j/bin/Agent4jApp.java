package site.sorghum.agent4j.bin;

import org.noear.solon.Solon;
import org.noear.solon.annotation.Import;
import org.noear.solon.annotation.SolonMain;

import site.sorghum.agent4j.bin.agent.Agent4jAgent;
import site.sorghum.agent4j.bin.agent.AgentLoopListener;
import site.sorghum.agent4j.bin.agent.ConsoleAgentOutput;
import site.sorghum.agent4j.bin.config.Agent4jConfig;
import site.sorghum.agent4j.bin.session.SessionStore;
import site.sorghum.agent4j.bin.session.JsonlSessionStore;

import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Agent4j 入口——纯 Java AI Agent。
 * <p>
 * 配置从 {@code ~/.agent4j/config.json} 自动读取。
 * </p>
 *
 * @author Sorghum
 */
@Import(scanPackages = {"site.sorghum.agent4j"})
@SolonMain
public class Agent4jApp {

    public static void main(String[] args) throws Throwable {
        // 0. 启动 Solon IoC 容器（扫描 @Component，使 subBeansOfType 可用）
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
        String model = envOr("MODEL", "deepseek-v4-flash");

        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("未配置 apiKey");
            System.exit(1);
        }

        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║          Agent4j — 代码助手               ║");
        System.out.println("╠══════════════════════════════════════════╣");
        System.out.println("║ 模型: " + padRight(model, 27) + "║");
        System.out.println("║ 工作区: " + padRight(truncate(config.workspaceDir().toString(), 25), 25) + "║");
        System.out.println("║ API: " + padRight(truncate(apiUrl, 25), 25) + "║");
        System.out.println("╠══════════════════════════════════════════╣");
        System.out.println("║ /init 生成agent4j.md    /exit 退出        ║");
        System.out.println("╚══════════════════════════════════════════╝");
        System.out.println();

        Agent4jAgent agent = Agent4jAgent.builder()
                .apiUrl(apiUrl)
                .apiKey(apiKey)
                .model(model)
                .workspace(config.workspaceDir())
                .build();

        // 设置输出接口（控制台输出）
        agent.setOutput(new ConsoleAgentOutput());

        // 注册事件监听：仅跟踪 token 用量（输出渲染已委托给 ConsoleAgentOutput）
        final int[] lastUsage = {0, 0, 0, 0, 0}; // prompt, completion, total, cacheHit, cacheMiss
        agent.setListener(new AgentLoopListener() {
            @Override
            public void onUsage(int promptTokens, int completionTokens, int totalTokens,
                                 int cacheHit, int cacheMiss) {
                lastUsage[0] = promptTokens;
                lastUsage[1] = completionTokens;
                lastUsage[2] = totalTokens;
                lastUsage[3] = cacheHit;
                lastUsage[4] = cacheMiss;
                agent.addUsage(promptTokens, completionTokens, cacheHit, cacheMiss);
            }
        });

        try (Scanner scanner = new Scanner(System.in, "UTF-8")) {
            while (true) {
                System.out.print("[" + agent.historySize() + "] > ");
                if (!scanner.hasNextLine()) break;
                String input = scanner.nextLine().trim();

                if (input.isEmpty()) continue;
                if ("/exit".equalsIgnoreCase(input) || "/quit".equalsIgnoreCase(input)) {
                    agent.flushSession();
                    agent.saveUsage();
                    System.out.println("再见");
                    break;
                }
                if ("/new".equalsIgnoreCase(input)) {
                    agent.newSession();
                    System.out.println("(新会话已开启)");
                    continue;
                }
                if ("/compact".equalsIgnoreCase(input)) {
                    System.out.println("正在折叠历史消息...");
                    agent.compact();
                    agent.flushSession();
                    System.out.println("(完成，当前 " + agent.historySize() + " 条消息)");
                    continue;
                }
                if ("/plan".equalsIgnoreCase(input)) {
                    agent.setPlanMode(true);
                    System.out.println("(已进入计划模式 — 仅允许只读操作)");
                    System.out.println("探索完成后使用 submit_plan 提交计划，或输入 /execute 开始执行");
                    continue;
                }
                if ("/execute".equalsIgnoreCase(input)) {
                    agent.setPlanMode(false);
                    System.out.println("(已退出计划模式 — 允许全部操作)");
                    continue;
                }
                if ("/retry".equalsIgnoreCase(input)) {
                    System.out.println("重试上一条消息...");
                    String reply = agent.retryLast();
                    if (reply != null) {
                        System.out.println();
                        System.out.println(reply);
                    } else {
                        System.out.println("(没有可重试的消息)");
                    }
                    continue;
                }
                if ("/sessions".equalsIgnoreCase(input)) {
                    SessionStore store = agent.getSessionStore();
                    if (store == null) { System.out.println("(会话存储未启用)"); continue; }
                    try {
                        List<SessionStore.SessionInfo> sessions = store.list();
                        if (sessions.isEmpty()) { System.out.println("(无历史会话)"); continue; }
                        System.out.println("会话列表：");
                        for (int i = 0; i < Math.min(sessions.size(), 20); i++) {
                            SessionStore.SessionInfo s = sessions.get(i);
                            System.out.println("  " + i + ". " + s.name + " (" + s.messageCount + " 条消息, " + new java.text.SimpleDateFormat("MM-dd HH:mm").format(new java.util.Date(s.mtime)) + ")");
                        }
                        System.out.println("使用 /load N 加载");
                    } catch (Exception e) {
                        System.out.println("(读取失败: " + e.getMessage() + ")");
                    }
                    continue;
                }
                if (input.toLowerCase().startsWith("/load ")) {
                    SessionStore store = agent.getSessionStore();
                    if (store == null) { System.out.println("(会话存储未启用)"); continue; }
                    try {
                        int n = Integer.parseInt(input.substring(6).trim());
                        List<SessionStore.SessionInfo> sessions = store.list();
                        if (n < 0 || n >= sessions.size()) { System.out.println("(无效编号)"); continue; }
                        String name = sessions.get(n).name;
                        agent.newSession();
                        SessionStore newStore = new JsonlSessionStore();
                        newStore.switchTo(name);
                        List<Map<String, Object>> loaded = newStore.load();
                        // 注入历史
                        for (Map<String, Object> m : loaded) {
                            agent.injectHistory(m);
                        }
                        agent.setSessionStore(newStore);
                        System.out.println("(已加载会话: " + name + ", " + loaded.size() + " 条消息)");
                    } catch (NumberFormatException e) { System.out.println("用法: /load N"); }
                    catch (Exception e) { System.out.println("(加载失败: " + e.getMessage() + ")"); }
                    continue;
                }
                if ("/init".equalsIgnoreCase(input)) {
                    System.out.println("正在分析项目...\n");
                    String prompt = "请全面分析这个项目的代码库，生成 agent4j.md 放在项目根目录。\n\n"
                            + "要求：\n"
                            + "1. 用 tree / glob 了解项目结构\n"
                            + "2. 阅读核心源文件\n"
                            + "3. agent4j.md 应包含：项目概述、目录结构树、技术栈表格、架构设计、全部工具列表、运行方式\n"
                            + "4. 用 write_file 写入 agent4j.md\n"
                            + "5. 完成后一句话总结";
                    try {
                        String reply = agent.chat(prompt);
                        System.out.println();
                        System.out.println(reply);
                    } catch (Exception e) {
                        System.out.println("(初始化失败: " + e.getMessage() + ")");
                    }
                    continue;
                }
                if (input.toLowerCase().startsWith("/rewind ")) {
                    try {
                        int n = Integer.parseInt(input.substring(8).trim());
                        System.out.println("回退到第 " + n + " 轮...");
                        String reply = agent.rewind(n);
                        if (reply != null) {
                            System.out.println();
                            System.out.println(reply);
                        } else {
                            System.out.println("(无效的轮次)");
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("用法: /rewind N");
                    }
                    continue;
                }

                try {
                    long t0 = System.currentTimeMillis();
                    String reply = agent.chat(input);
                    long elapsed = System.currentTimeMillis() - t0;
                    System.out.println();
                    if (reply == null || reply.isEmpty()) {
                        System.out.println("(模型返回空内容，可能 API 异常或模型选择有误)");
                    } else {
                        System.out.println(reply);
                    }
                    System.out.println();
                    String usage = "";
                    if (lastUsage[2] > 0) {
                        int cacheTotal = lastUsage[3] + lastUsage[4];
                        String cacheStr = cacheTotal > 0
                                ? " | 📦 cache: " + lastUsage[3] + " hit + " + lastUsage[4] + " miss"
                                  + " (" + (lastUsage[3] * 100 / cacheTotal) + "%)"
                                : "";
                        usage = " | 💰 in=" + lastUsage[0] + " out=" + lastUsage[1] + " total=" + lastUsage[2] + cacheStr;

                        // 会话累计
                        long[] sess = agent.getSessionUsage();
                        long sessCacheTotal = sess[2] + sess[3];
                        String sessCacheStr = sessCacheTotal > 0
                                ? " | 📦 cache: " + sess[2] + " hit + " + sess[3] + " miss"
                                  + " (" + (sess[2] * 100 / sessCacheTotal) + "%)"
                                : "";
                        usage += "\n       [会话累计 💰 in=" + sess[0] + " out=" + sess[1] + sessCacheStr + "]";
                    }
                    System.out.println("[" + (elapsed / 1000.0) + "s]" + usage);
                    lastUsage[0] = lastUsage[1] = lastUsage[2] = lastUsage[3] = lastUsage[4] = 0;
                    System.out.println();
                    agent.saveUsage(); // 每次对话后持久化 token 用量
                    agent.flushSession(); // 每轮对话结束后刷入会话数据到磁盘
                } catch (Exception e) {
                    System.err.println("错误: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
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
