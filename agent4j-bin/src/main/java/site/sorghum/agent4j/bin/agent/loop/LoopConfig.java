package site.sorghum.agent4j.bin.agent.loop;

import site.sorghum.agent4j.bin.config.Agent4jConfig;

/**
 * AgentLoop 配置常量 —— 从 {@link AgentLoop} 中提取的配置读取逻辑。
 * <p>
 * 集中管理所有默认值和配置访问方法，避免 AgentLoop 过于庞大。
 * </p>
 *
 * @author Sorghum
 */
public class LoopConfig {

    /** 默认最大上下文字符数（200k 约 256k tokens 的保守估计，覆盖主流模型上下文窗口） */
    public static final int DEFAULT_MAX_CONTEXT_CHARS = 200_000;
    /** 折叠后保留尾部字符数（80k 确保折叠后仍有足够上下文供后续推理） */
    public static final int DEFAULT_KEEP_TAIL_CHARS = 80_000;
    /** 工具执行超时秒数（1080s=18min，覆盖长时间工具调用如大型构建/测试） */
    public static final int DEFAULT_TOOL_TIMEOUT_SEC = 1080;
    /** Storm 断路器自愈最大尝试次数 */
    public static final int DEFAULT_MAX_SELF_CORRECTION = 5;
    /** 流式响应等待超时秒数（防止 HTTP 流永不结束导致线程挂起） */
    public static final int DEFAULT_STREAM_LATCH_TIMEOUT_SEC = 300;

    private final Agent4jConfig config;

    public LoopConfig(Agent4jConfig config) {
        this.config = config;
    }

    public int maxTotalChars() {
        return config != null ? config.maxContextChars() : DEFAULT_MAX_CONTEXT_CHARS;
    }

    public int keepTailChars() {
        return config != null ? config.keepTailChars() : DEFAULT_KEEP_TAIL_CHARS;
    }

    public int toolTimeoutSec() {
        return config != null ? config.toolTimeoutSec() : DEFAULT_TOOL_TIMEOUT_SEC;
    }

    public int maxSelfCorrectionAttempts() {
        return config != null ? config.maxSelfCorrectionAttempts() : DEFAULT_MAX_SELF_CORRECTION;
    }

    public int streamLatchTimeoutSec() {
        return DEFAULT_STREAM_LATCH_TIMEOUT_SEC;
    }
}
