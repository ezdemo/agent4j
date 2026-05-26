package site.sorghum.agent4j.bin.agent;

import site.sorghum.agent4j.bin.model.ModelClient;
import site.sorghum.agent4j.bin.model.HttpModelClient;
import site.sorghum.agent4j.bin.config.Agent4jConfig;
import site.sorghum.agent4j.bin.tool.ToolDef;
import site.sorghum.agent4j.bin.tool.ToolRegistry;
import site.sorghum.agent4j.bin.tool.ToolDispatcher;
import site.sorghum.agent4j.bin.session.SessionService;
import site.sorghum.agent4j.bin.session.SessionStore;
import site.sorghum.agent4j.bin.session.JsonlSessionStore;
import site.sorghum.agent4j.tool.AgentTool;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolResult;
import site.sorghum.agent4j.tool.ToolParameter;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Agent4j 工厂——组装 ModelClient + ToolRegistry → AgentLoop。
 *
 * @author Sorghum
 */
public class Agent4jAgent {

    private AgentLoop loop;
    private ConversationContext ctx;
    private SessionService sessionService;
    private Path workspace;
    private String apiUrl;
    private String apiKey;

    private Agent4jAgent(Builder b) {
        this.workspace = b.workspace;
        this.apiUrl = b.apiUrl;
        this.apiKey = b.apiKey;

        ModelClient client = new HttpModelClient(b.apiUrl, b.apiKey, b.model);
        ToolRegistry registry = new ToolRegistry();

        // 通过 getBeansOfType 同步获取所有 AgentTool 子类 Bean
        for (AgentTool tool : org.noear.solon.Solon.context().getBeansOfType(AgentTool.class)) {
            registry.register(new ToolDef(
                    tool.getName(),
                    tool.getDescription(),
                    toParamDefs(tool.getParameters()),
                    args -> formatResult(tool.execute(
                            new ToolContext(args, workspace, apiUrl, apiKey, registry))),
                    tool.isReadOnly(),
                    tool.isStormExempt()));
        }

        // 加载项目文档（agent4j.md / CLAUDE.md），追加到 system prompt
        String systemPrompt = b.systemPrompt;
        String projectMd = loadProjectMd(b.workspace);
        if (!projectMd.isEmpty()) {
            systemPrompt = projectMd + "\n\n---\n\n" + systemPrompt;
        }

        // 构建缓存优先前缀：system prompt + 工具定义（注册后冻结，跨 turn 稳定）
        PromptPrefix prefix = new PromptPrefix(systemPrompt, registry.toOpenAiTools());
        this.ctx = new ConversationContext(prefix);

        // 会话持久化 — 委托 SessionService
        try {
            SessionStore store = new JsonlSessionStore();
            this.sessionService = new SessionService(ctx, store);
            sessionService.loadOrCreate(System.getenv("AGENT4J_SESSION"));
        } catch (IOException e) {
            System.err.println("[session] 初始化失败: " + e.getMessage());
        }

        this.loop = new AgentLoop(client, registry, ctx, 0);
    }

    /**
     * 将 AgentTool 的参数类型映射为 JSON Schema 类型。
     */
    private static String toJsonType(String type) {
        if (type == null) return "string";
        switch (type.toLowerCase()) {
            case "int": case "integer": case "long": return "integer";
            case "bool": case "boolean": return "boolean";
            case "number": case "float": case "double": return "number";
            case "array": case "list": return "array";
            case "object": case "map": return "object";
            default: return "string";
        }
    }

    /**
     * 将 AgentTool 的参数定义列表转换为 ToolDef.ParamDef 列表。
     */
    private static List<ToolDef.ParamDef> toParamDefs(List<ToolParameter> params) {
        List<ToolDef.ParamDef> out = new ArrayList<>();
        for (ToolParameter p : params) {
            if (p.isRequired()) {
                out.add(ToolDef.ParamDef.required(p.getName(), toJsonType(p.getType()), p.getDescription()));
            } else {
                out.add(ToolDef.ParamDef.of(p.getName(), toJsonType(p.getType()), p.getDescription()));
            }
        }
        return out;
    }

    /**
     * 将 ToolResult 格式化为工具调用的返回字符串。
     * 失败结果添加 [FAIL:errorCode] 前缀。
     */
    private static String formatResult(ToolResult r) {
        if (r.isSuccess()) return r.getText();
        return "[FAIL:" + r.getErrorCode() + "] " + r.getText();
    }

    // ========== 公共 API ==========

    public String chat(String message) throws IOException {
        return loop.run(message);
    }

    public void newSession() {
        try { sessionService.newSession(); } catch (IOException ignored) {}
    }

    /** 累计 token 用量 */
    public void addUsage(int prompt, int completion, int cacheHit, int cacheMiss) {
        sessionService.addUsage(prompt, completion, cacheHit, cacheMiss);
    }

    /** 获取会话累计 token 用量 */
    public long[] getSessionUsage() {
        return sessionService.getUsage();
    }

    /** /retry 撤回最后一条消息并重试 */
    public String retryLast() throws IOException {
        String msg = ctx.retryLastUser();
        return msg != null ? chat(msg) : null;
    }

    /** /rewind N 回退到第 N 轮 */
    public String rewind(int n) throws IOException {
        String msg = ctx.rewindToUser(n);
        return msg != null ? chat(msg) : null;
    }

    public void setListener(AgentLoopListener listener) {
        loop.setListener(listener);
    }

    /**
     * 设置输出接口。
     * <p>
     * 所有 Agent 的输出（流式内容、思考、工具调用、日志等）都会通过此接口发送。
     * 默认使用 {@link ConsoleAgentOutput} 打印到控制台。
     * 可传入自定义实现（如 WebSocket SSE、日志文件等）。
     * </p>
     *
     * @param output 输出接口实现，传入 null 则使用 NOOP（关闭输出）
     */
    public void setOutput(AgentOutput output) {
        loop.setOutput(output);
    }

    /** 获取当前输出接口 */
    public AgentOutput getOutput() {
        return loop.getOutput();
    }

    /** 获取 SessionStore（用于列表/切换） */
    public SessionStore getSessionStore() {
        return sessionService.getStore();
    }

    /** 设置 SessionStore */
    public void setSessionStore(SessionStore store) {
        ctx.setSessionStore(store);
        // 重建 SessionService 以保持一致性
        this.sessionService = new SessionService(ctx, store);
    }

    /** 注入历史消息（加载会话时） */
    public void injectHistory(Map<String, Object> msg) {
        sessionService.injectHistory(msg);
    }

    /** 进入/退出 Plan Mode（提示词始终包含规则，仅切换 dispatch 门控） */
    public void setPlanMode(boolean on) {
        loop.setPlanMode(on);
    }

    public boolean isPlanMode() {
        return loop.isPlanMode();
    }

    /** 保存会话用量（退出前调用） */
    public void saveUsage() {
        sessionService.saveUsage();
    }

    /**
     * 刷入会话数据到磁盘。
     * 每轮对话结束后调用，确保消息已持久化。
     */
    public void flushSession() {
        sessionService.flush();
    }

    public void compact() throws IOException {
        sessionService.saveUsage();
        loop.compactNow();
    }

    public int historySize() {
        return ctx.size();
    }

    public static Builder builder() {
        return new Builder();
    }

    // ---- 项目文档加载 ----

    /**
     * 加载工作区根目录下的 agent4j.md 和 CLAUDE.md，
     * 将项目文档作为系统提示的补充上下文。
     * 文件不存在时返回空字符串。
     */
    private static String loadProjectMd(Path workspace) {
        StringBuilder sb = new StringBuilder();
        for (String name : new String[]{"agent4j.md", "CLAUDE.md"}) {
            Path file = workspace.resolve(name);
            if (java.nio.file.Files.exists(file)) {
                try {
                    String content = new String(java.nio.file.Files.readAllBytes(file),
                            java.nio.charset.StandardCharsets.UTF_8);
                    if (sb.length() > 0) sb.append("\n\n");
                    sb.append("[来自 ").append(name).append(" 的项目上下文]\n");
                    sb.append(content.trim());
                } catch (IOException ignored) {}
            }
        }
        return sb.toString();
    }

    public static class Builder {
        String apiUrl;
        String apiKey;
        String model = "deepseek-v4-flash";
        String systemPrompt = "你是一个代码助手，可以搜索、阅读、编辑文件，执行终端命令。\n"
                + "对于问候或简单对话，直接用文本回复。\n"
                + "编辑文件时使用 edit_file（SEARCH/REPLACE，search 必须唯一）。\n"
                + "多文件批量编辑使用 multi_edit。\n"
                + "不确定文件位置时用 glob/grep 搜索，需要构建/测试时用 run_command。\n\n"
                + "# Plan mode (/plan)\n\n"
                + "当用户输入 /plan 进入计划模式后，写入工具（edit_file / multi_edit / write_file / run_command 等）"
                + "会被 dispatch 拒绝并返回 'unavailable in plan mode'。\n"
                + "计划模式下只读工具（read_file / glob / grep / tree / get_file_info / web_search）正常使用。\n"
                + "你需要先探索代码库，然后用 submit_plan 提交计划。\n"
                + "用户审批或输入 /execute 退出计划模式后，所有工具恢复正常。";
        Path workspace = Paths.get(".").toAbsolutePath();
        int maxSteps = 20;

        public Builder apiUrl(String v) { this.apiUrl = v; return this; }
        public Builder apiKey(String v) { this.apiKey = v; return this; }
        public Builder model(String v) { this.model = v; return this; }
        public Builder systemPrompt(String v) { this.systemPrompt = v; return this; }
        public Builder workspace(Path v) { this.workspace = v; return this; }
        public Builder maxSteps(int v) { this.maxSteps = v; return this; }

        public Builder config(Agent4jConfig c) {
            if (c.chatApiUrl() != null) this.apiUrl = c.chatApiUrl();
            if (c.apiKey() != null) this.apiKey = c.apiKey();
            this.model = c.model();
            this.workspace = c.workspaceDir();
            return this;
        }

        public Agent4jAgent build() {
            Objects.requireNonNull(apiUrl, "apiUrl is required");
            Objects.requireNonNull(apiKey, "apiKey is required");
            return new Agent4jAgent(this);
        }
    }
}
