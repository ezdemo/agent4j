package site.sorghum.agent4j.bin.model;

import org.noear.snack4.ONode;
import site.sorghum.agent4j.bin.agent.ChatMessage;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 模型客户端接口 —— 封装 LLM API 调用的抽象契约。
 * <p>
 * 实现类负责具体的 HTTP 通信、流式解析逻辑，
 * 调用方仅依赖此接口以保证可替换性和可测试性。
 * </p>
 *
 * @author Sorghum
 */
public interface ModelClient {

    /**
     * 非流式调用 —— 发送消息列表和工具定义，返回 API 消息节点。
     * 用于后台操作（上下文折叠摘要等）。
     */
    ONode chat(List<ChatMessage> messages,
               List<Map<String, Object>> tools) throws IOException;

    /**
     * 流式调用 —— 通过回调逐 token 推送推理和内容。
     */
    void chatStream(List<ChatMessage> messages,
                    List<Map<String, Object>> tools,
                    StreamCallback callback);

    /**
     * 获取当前模型名称。
     */
    String getModel();

    /**
     * 设置模型名称（运行时切换）。
     */
    void setModel(String model);

    /**
     * 是否为推理模型（DeepSeek V4 / Reasoner 系列）。
     */
    boolean isThinkingMode();

    /**
     * 中断当前正在进行的流式调用（如果存在）。
     * 默认空实现——不支持中断的客户端可忽略。
     */
    default void abortStream() {
    }

    /**
     * 模型最大上下文窗口 token 数。
     * 用于折叠阈值计算：当 prompt_tokens 达到此值的 80% 时触发自动折叠。
     * 默认 128K，子类可按模型名返回更准确的值。
     */
    default int getMaxContextTokens() {
        return 128_000;
    }

    /**
     * 流式回调接口 —— 用于实时消费 SSE 事件。
     */
    interface StreamCallback {
        /**
         * 收到 reasoning token
         */
        default void onReasoningDelta(String token) {
        }

        /**
         * 收到内容 token
         */
        default void onContentDelta(String token) {
        }

        /**
         * 收到完整 tool_calls（流结束时的最终数组）
         */
        default void onToolCalls(ONode toolCalls) {
        }

        /**
         * token 用量回调。
         *
         * @param promptTokens     输入 token 数
         * @param completionTokens 输出 token 数
         * @param totalTokens      总 token 数
         * @param cacheHitTokens   缓存命中 token 数
         * @param cacheMissTokens  缓存未命中 token 数
         */
        default void onUsage(int promptTokens, int completionTokens, int totalTokens,
                             int cacheHitTokens, int cacheMissTokens) {
        }

        /**
         * 流结束
         */
        default void onDone() {
        }

        /**
         * 错误
         */
        default void onError(String error) {
        }
    }
}
