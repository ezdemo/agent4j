package site.sorghum.agent4j.bin.agent;

/**
 * 控制台输出实现 —— {@link AgentOutput} 的默认实现。
 * <p>
 * 所有输出内容通过 {@link System#out} / {@link System#err} 打印到控制台，
 * 保持与重构前一致的行为。将原 {@link AgentLoop} 和 {@link site.sorghum.agent4j.bin.Agent4jApp Agent4jApp}
 * 中散落的 System.out/err 调用集中于此。
 * </p>
 *
 * <h3>行为说明</h3>
 * <ul>
 *   <li>{@link #onContentDelta} — 输出到 System.out（实时刷新）</li>
 *   <li>{@link #onReasoningDelta} — 输出到 System.err（与内容区分）</li>
 *   <li>{@link #onToolCall} — 带 🔧 前缀输出到 System.out</li>
 *   <li>{@link #onToolResult} — 带 📦 前缀输出到 System.out（过长时截断）</li>
 *   <li>{@link #onLog} — 按级别输出到 System.out 或 System.err</li>
 * </ul>
 *
 * @author Sorghum
 */
public class ConsoleAgentOutput implements AgentOutput {

    /** 工具结果展示最大字符数（超过则截断） */
    private static final int MAX_DISPLAY_CHARS = 200;

    @Override
    public void onContentDelta(String token) {
        System.out.print(token);
        System.out.flush();
    }

    @Override
    public void onContentComplete() {
        System.out.println();
        System.out.flush();
    }

    @Override
    public void onReasoningDelta(String token) {
        System.err.print(token);
    }

    @Override
    public void onReasoningComplete() {
        // 默认不做额外输出，由 onContentComplete 或 onReasoning 处理
    }

    @Override
    public void onReasoning(String reasoning) {
        System.out.println("\n── 思考 ──");
        System.out.println(reasoning);
        System.out.println("──────────\n");
    }

    @Override
    public void onToolCall(String name, String args) {
        System.out.println("🔧 " + name + "(" + args + ")");
    }

    @Override
    public void onToolResult(String name, String result) {
        String display = result;
        if (display != null && display.length() > MAX_DISPLAY_CHARS) {
            display = display.substring(0, MAX_DISPLAY_CHARS)
                    + "\n[… 结果过长，截断显示 " + result.length() + " 字符]";
        }
        System.out.println("📦 " + name + " → " + (display != null ? display : "(无输出)"));
    }

    @Override
    public void onUsage(int promptTokens, int completionTokens, int totalTokens,
                        int cacheHit, int cacheMiss) {
        // ConsoleAgentOutput 不在流式过程中打印 usage，
        // 由 Agent4jApp 在每轮对话结束后统一展示
    }

    @Override
    public void onError(String error) {
        System.err.println("[error] " + error);
    }

    @Override
    public void onLog(LogLevel level, String message) {
        if (message == null) return;
        switch (level) {
            case ERROR:
                System.err.println("[error] " + message);
                break;
            case WARN:
                System.err.println("[warn] " + message);
                break;
            case DEBUG:
                System.err.println("[DEBUG] " + message);
                break;
            case INFO:
            default:
                System.err.println(message);
                break;
        }
    }

    @Override
    public void onMessage(String message) {
        System.out.println(message);
    }
}
