package site.sorghum.agent4j.web.service;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Init;

import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.bin.agent.Agent4jAgent;
import site.sorghum.agent4j.bin.agent.AgentLoopListener;
import site.sorghum.agent4j.bin.agent.AgentOutput;
import site.sorghum.agent4j.bin.command.ChatCommandRegistry;
import site.sorghum.agent4j.bin.config.Agent4jConfig;
import site.sorghum.agent4j.bin.session.SessionStore;
import site.sorghum.agent4j.bin.tool.ToolDef;

import org.noear.snack4.ONode;
import org.noear.solon.Solon;

import java.io.IOException;
import java.util.*;

/**
 * Agent 单例服务 —— 管理 Agent4jAgent 的生命周期和并发访问。
 * <p>
 * Web 模式下全局只有一个 Agent 实例。所有聊天请求通过串行锁保证线程安全。
 * 同时提供 SSE 事件桥接：将 AgentOutput 事件实时转发到 SseEmitter。
 * </p>
 *
 * <p>
 * <strong>关于命令操作：</strong>retry/rewind/compact/plan/hitl/agree/deny 等命令
 * 已由 {@link site.sorghum.agent4j.bin.command.ChatCommandRegistry} 在
 * {@link Agent4jAgent#chat(String)} 中统一处理。前端直接发送命令字符串
 * （如 {@code "/retry"}、{@code "/compact"}）到聊天接口即可，
<skilltt> * 无需额外 REST API。</p>
 *
 * @author Sorghum
 */
@Component
public class AgentService {

    @Inject
    ChatCommandRegistry commandRegistry;
    private volatile Agent4jAgent agent;
    private final java.util.concurrent.locks.ReentrantLock chatLock = new java.util.concurrent.locks.ReentrantLock();

    /** 当前 SSE 输出（每次请求创建一个新的） */
    private volatile SseEmitter currentSseEmitter;

    /** 初始化 Agent（Solon 启动后自动调用） */
    @Init
    public void init() {
        try {
            Agent4jConfig config = Agent4jConfig.load();
            String apiUrl = envOr("OPENAI_BASE_URL", config.chatApiUrl());
            String apiKey = envOr("OPENAI_API_KEY", config.apiKey());
            String model = envOr("MODEL", config.model());

            if (apiKey == null || apiKey.isEmpty()) {
                System.err.println("[web] 未配置 apiKey，Agent 未初始化");
                return;
            }


            agent = Agent4jAgent.builder()
                    .config(config)
                    .apiUrl(apiUrl)
                    .apiKey(apiKey)
                    .model(model)
                    .workspace(config.workspaceDir())
                    .commandRegistry(commandRegistry)
                    .build();

            // 注册 token 用量追踪
            agent.setListener(new AgentLoopListener() {
                @Override
                public void onUsage(int promptTokens, int completionTokens, int totalTokens,
                                     int cacheHit, int cacheMiss) {
                    agent.addUsage(promptTokens, completionTokens, cacheHit, cacheMiss);
                }
            });

            // 默认使用 NOOP 输出（API 调用时由 SseEmitter 接管）
            agent.setOutput(AgentOutput.NOOP);

            System.out.println("[web] Agent 初始化完成 — 模型: " + model);
        } catch (Exception e) {
            System.err.println("[web] Agent 初始化失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** 获取 Agent 实例（可能为 null，如果初始化失败） */
    public Agent4jAgent getAgent() {
        return agent;
    }

    /** Agent 是否已初始化 */
    public boolean isReady() {
        return agent != null;
    }

    // ==================== 聊天 ====================

    /**
     * 同步聊天 —— 串行执行，返回完整回复。
     * <p>
     * 命令字符串（如 "/retry"、"/compact"）会由 Agent4jAgent.chat()
     * 自动路由到 {@link site.sorghum.agent4j.bin.command.ChatCommandRegistry} 处理。
     * </p>
     */
    public String chat(String message) throws IOException, InterruptedException {
        chatLock.lock();
        try {
            agent.setOutput(AgentOutput.NOOP);
            return agent.chat(message);
        } finally {
            agent.flushSession();
            agent.saveUsage();
            chatLock.unlock();
        }
    }

    /**
     * 流式聊天 —— 通过 AgentOutput 桥接到 SseEmitter。
     * <p>
     * 命令字符串同样在此通道处理，命令的输出通过 SSE 事件返回。
     * </p>
     */
    public void chatStream(String message, SseEmitter emitter) throws IOException, InterruptedException {
        chatLock.lock();
        try {
            this.currentSseEmitter = emitter;

            // 设置 AgentOutput：将所有事件桥接到 SSE
            agent.setOutput(new AgentOutput() {
                @Override
                public void onContentDelta(String token) {
                    emitter.sendContent(token);
                }

                @Override
                public void onContentComplete() {}

                @Override
                public void onReasoningDelta(String token) {
                    emitter.sendReasoning(token);
                }

                @Override
                public void onReasoningComplete() {}

                @Override
                public void onReasoning(String reasoning) {
                    if (reasoning != null && !reasoning.isEmpty()) {
                        emitter.sendReasoning(reasoning);
                    }
                }

                @Override
                public void onToolCall(String name, String args) {
                    emitter.sendToolCall(name, args);
                }

                @Override
                public void onToolResult(String name, String result) {
                    emitter.sendToolResult(name, result);
                }

                @Override
                public void onUsage(int promptTokens, int completionTokens, int totalTokens,
                                     int cacheHit, int cacheMiss) {
                    emitter.sendUsage(promptTokens, completionTokens, totalTokens, cacheHit, cacheMiss);
                    agent.addUsage(promptTokens, completionTokens, cacheHit, cacheMiss);
                }

                @Override
                public void onError(String error) {
                    emitter.sendError(error);
                }

                @Override
                public void onLog(LogLevel level, String message) {}

                @Override
                public void onMessage(String message) {}
            });

            String reply = agent.chat(message);

            // 发送最终回复
            if (reply != null && !reply.isEmpty()) {
                ONode replyNode = ONode.ofJson("{}").asObject();
                replyNode.set("content", reply);
                emitter.send("reply", replyNode.toJson());
            }
        } catch (Exception e) {
            emitter.sendError(e.getMessage());
        } finally {
            agent.setOutput(AgentOutput.NOOP);
            agent.flushSession();
            agent.saveUsage();
            this.currentSseEmitter = null;
            emitter.complete();
            chatLock.unlock();
        }
    }

    // ==================== 会话管理 ====================

    public void newSession() {
        requireAgent();
        agent.newSession();
    }

    public List<Map<String, Object>> listSessions() throws IOException {
        requireAgent();
        SessionStore store = agent.getSessionStore();
        List<SessionStore.SessionInfo> sessions = store.list();
        List<Map<String, Object>> result = new ArrayList<>();
        for (SessionStore.SessionInfo s : sessions) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", s.name);
            m.put("messageCount", s.messageCount);
            m.put("size", s.size);
            m.put("mtime", s.mtime);
            result.add(m);
        }
        return result;
    }

    public boolean switchSession(String name) {
        requireAgent();
        return agent.getSessionStore().switchTo(name);
    }

    public boolean deleteSession(String name) throws IOException {
        requireAgent();
        return agent.getSessionStore().delete(name);
    }

    public Map<String, Object> getCurrentSession() {
        requireAgent();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", agent.getSessionStore().currentName());
        info.put("historySize", agent.historySize());
        return info;
    }

    // ==================== Agent 状态 ====================

    public Map<String, Object> getStatus() {
        requireAgent();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("ready", true);
        status.put("planMode", agent.isPlanMode());
        status.put("hitlMode", agent.isHitlMode());
        status.put("hasPendingHitl", agent.hasPendingHITL());
        status.put("historySize", agent.historySize());
        status.put("currentSession", agent.getSessionStore().currentName());

        long[] usage = agent.getSessionUsage();
        Map<String, Object> usageMap = new LinkedHashMap<>();
        usageMap.put("promptTokens", usage[0]);
        usageMap.put("completionTokens", usage[1]);
        usageMap.put("cacheHit", usage[2]);
        usageMap.put("cacheMiss", usage[3]);
        status.put("usage", usageMap);

        return status;
    }

    public List<Map<String, Object>> getHistory() {
        requireAgent();
        try {
            return agent.getSessionStore().load();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // ==================== 工具 ====================

    public List<Map<String, Object>> listTools() {
        requireAgent();
        site.sorghum.agent4j.bin.tool.ToolRegistry registry = agent.getToolRegistry();
        if (registry == null) return new ArrayList<>();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, ToolDef> entry : registry.all().entrySet()) {
            ToolDef def = entry.getValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", def.name);
            m.put("description", def.description);
            m.put("readOnly", def.readOnly);
            m.put("stormExempt", def.stormExempt);
            m.put("parameters", def.toParametersSchema());
            result.add(m);
        }
        return result;
    }

    public Map<String, Object> getTool(String name) {
        requireAgent();
        site.sorghum.agent4j.bin.tool.ToolRegistry registry = agent.getToolRegistry();
        if (registry == null) return null;
        ToolDef def = registry.get(name);
        if (def == null) return null;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", def.name);
        m.put("description", def.description);
        m.put("readOnly", def.readOnly);
        m.put("stormExempt", def.stormExempt);
        m.put("parameters", def.toParametersSchema());
        return m;
    }

    public String executeTool(String name, Map<String, Object> arguments) {
        requireAgent();
        site.sorghum.agent4j.bin.tool.ToolRegistry registry = agent.getToolRegistry();
        if (registry == null) throw new IllegalStateException("ToolRegistry 未初始化");
        ToolDef def = registry.get(name);
        if (def == null) throw new IllegalArgumentException("未知工具: " + name);
        return def.fn.call(arguments != null ? arguments : new LinkedHashMap<String, Object>());
    }

    // ==================== 用量 ====================

    public Map<String, Object> getUsage() {
        requireAgent();
        long[] u = agent.getSessionUsage();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("promptTokens", u[0]);
        m.put("completionTokens", u[1]);
        m.put("cacheHit", u[2]);
        m.put("cacheMiss", u[3]);
        return m;
    }

    // ==================== 辅助 ====================

    private void requireAgent() {
        if (agent == null) {
            throw new IllegalStateException("Agent 未初始化，请检查 ~/.agent4j/config.json 配置");
        }
    }

    private static String envOr(String key, String fallback) {
        String v = System.getenv(key);
        return (v != null && !v.isEmpty()) ? v : fallback;
    }
}
