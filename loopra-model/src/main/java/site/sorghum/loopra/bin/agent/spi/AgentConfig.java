package site.sorghum.loopra.bin.agent.spi;

import java.util.List;
import java.util.Map;

/**
 * AgentLoop 内核所需的只读配置视图（SPI）。
 * <p>
 * 内核仅依赖本接口，不感知具体配置实现；完整配置（文件读写、热更新、渠道结构等）
 * 由上层模块（loopra-harness 的 LoopraConfig）实现本接口后注入。
 * 传入 {@code null} 时内核全部使用内置默认值。
 * </p>
 *
 * @author Sorghum
 */
public interface AgentConfig {

    /** 上下文最大字符数（超出触发折叠）。 */
    int maxContextChars();

    /** 折叠时保留的尾部字符数。 */
    int keepTailChars();

    /** 普通工具执行超时（秒）。 */
    int toolTimeoutSec();

    /** 子代理工具执行超时（秒）。 */
    int subAgentTimeoutSec();

    /** 最大自我纠正次数。 */
    int maxSelfCorrectionAttempts();

    /** 无工具调用时是否直接结束本轮对话。 */
    boolean terminateOnNoToolCall();

    /** 风暴断路器窗口大小。 */
    int stormWindowSize();

    /** 风暴断路器阈值。 */
    int stormThreshold();

    /** 工具调用校验模型名（空表示未启用校验模型）。 */
    String validationModel();

    /** 校验模型所在渠道；未配置返回 {@code null}。 */
    Channel validationModelChannel();

    /** HITL auto 模式的白名单工具列表。 */
    List<String> autoWhitelist();

    /**
     * 模型渠道视图（对应具体实现中的 ModelChannel）。
     */
    interface Channel {
        String id();

        String apiUrl();

        String apiKey();

        String apiProtocol();

        /** 渠道内按名称查找模型条目；不存在返回 {@code null}。 */
        Entry modelEntry(String modelName);
    }

    /**
     * 模型条目视图（对应具体实现中的 ModelEntry）。
     */
    interface Entry {
        String name();

        int contextTokens();

        boolean imageInput();

        Map<String, Double> price();
    }
}
