package site.sorghum.agent4j.bin.model;

import org.noear.snack4.ONode;
import site.sorghum.agent4j.bin.agent.model.ChatMessage;

import java.io.IOException;
import java.util.List;

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
               ONode tools) throws IOException;

    /**
     * 流式调用 —— 通过回调逐 token 推送推理和内容。
     */
    void chatStream(List<ChatMessage> messages,
                    ONode tools,
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
     * 设置推理强度（运行时切换）。
     * 取值: low / medium / high / max
     * 默认空实现——不支持运行时切换的客户端可忽略。
     */
    default void setReasoningEffort(String reasoningEffort) {
    }

    /**
     * 中断当前正在进行的流式调用（如果存在）。
     * 默认空实现——不支持中断的客户端可忽略。
     */
    default void abortStream() {
    }

    /**
     * 清除上一轮遗留的流式中断状态。
     * <p>
     * 每个新的用户回合开始前调用。默认空实现，未维护中断状态的客户端无需处理。
     * </p>
     */
    default void resetStreamAbort() {
    }

    /**
     * 创建一个可独立流式调用和中断的客户端实例。
     * 不支持复制的实现可返回自身，但并发调用方应优先覆盖此方法。
     */
    default ModelClient fork() {
        return this;
    }

    /**
     * 模型最大上下文窗口 token 数。
     * 用于折叠阈值计算：当 prompt_tokens 达到此值的 80% 时触发自动折叠。
     * 默认 128K，子类可按模型名返回更准确的值。
     */
    default int getMaxContextTokens() {
        return 256_000;
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
