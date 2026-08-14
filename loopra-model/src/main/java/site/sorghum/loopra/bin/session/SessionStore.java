package site.sorghum.loopra.bin.session;

import site.sorghum.loopra.bin.agent.model.ChatMessage;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 会话持久化仓库接口 —— 定义会话消息的 CRUD 契约。
 * <p>
 * 实现负责具体的存储格式（JSONL、数据库等），
 * 调用方仅依赖此接口以保证可替换性和可测试性。
 * </p>
 *
 * @author Sorghum
 */
public interface SessionStore {

    /**
     * 获取当前会话名
     */
    String currentName();

    /**
     * 生成新会话名
     */
    String newSessionName();

    /**
     * 切换到指定会话
     */
    boolean bindTo(String name);

    /**
     * 追加一条消息
     */
    void append(ChatMessage message) throws IOException;

    /**
     * 加载当前会话全部消息
     */
    List<ChatMessage> load() throws IOException;

    /**
     * 加载指定会话的消息
     */
    List<ChatMessage> load(String name) throws IOException;

    /**
     * 重写整个会话文件
     */
    void rewrite(List<ChatMessage> messages) throws IOException;

    /**
     * 列出所有活跃会话（最新在前）
     */
    List<SessionInfo> list() throws IOException;

    /**
     * 删除会话
     */
    boolean delete(String name) throws IOException;

    /**
     * 清空所有会话，删除所有会话文件（.jsonl / .usage / .meta）。
     */
    void clearAll() ;

    /**
     * 刷入缓冲区数据到磁盘。
     * 调用后确保所有已追加的消息被持久化。
     */
    void flush();

    /**
     * 保存 token 用量
     */
    void saveUsage(String name, long prompt, long completion, long cacheHit, long cacheMiss) throws IOException;

    /**
     * 保存 token 用量（包含 lastPromptTokens）
     */
    void saveUsage(String name, long prompt, long completion,
                   long cacheHit, long cacheMiss, long lastPromptTokens)
            throws IOException;

    /**
     * 加载 token 用量
     */
    long[] loadUsage(String name);

    /**
     * 保存按模型分别累计的 token 用量
     */
    void saveModelUsage(String name, Map<String, long[]> modelUsage) throws IOException;

    /**
     * 加载按模型分别累计的 token 用量
     */
    Map<String, long[]> loadModelUsage(String name);

    /**
     * 更新会话标题
     */
    void updateTitle(String name, String title) throws IOException;

    /**
     * 获取会话标题，不存在则返回 null
     */
    String getTitle(String name);

    /**
     * 持久化会话的计划模式开关。
     * <p>默认空实现；支持会话元数据的实现（如 JSONL .meta 文件）应覆盖，
     * 保证 Agent 重建/会话切换后能恢复计划模式，避免静默放宽为可写。</p>
     */
    default void setPlanMode(String name, boolean enabled) {
        // 默认空实现，向后兼容
    }

    /**
     * 读取会话持久化的计划模式状态（默认 false）。
     */
    default boolean isPlanMode(String name) {
        return false;
    }

    /**
     * 持久化待用户审查的计划；传 null 表示清除。
     */
    default void setPendingPlan(String name, String plan) {
        // 默认空实现，向后兼容
    }

    /**
     * 读取待用户审查的计划，不存在则返回 null。
     */
    default String getPendingPlan(String name) {
        return null;
    }

    /**
     * 持久化会话的"工作树隔离模式"开关。
     * <p>开启后该会话的 AI 文件操作落在 ~/.loopra/worktree/ 下的 git worktree 中，
     * 会话历史/Goal/Checklist 等仍归属主工作区。默认空实现；支持会话元数据的实现
     * （如 JSONL .meta 文件）应覆盖，保证 Agent 重建后工具根不会静默漂移回主工作区。</p>
     */
    default void setWorktreeMode(String name, boolean enabled) {
        // 默认空实现，向后兼容
    }

    /**
     * 读取会话持久化的工作树隔离模式（默认 false）。
     */
    default boolean isWorktreeMode(String name) {
        return false;
    }

    /**
     * 持久化会话的工作树合并模式：manual / ai-auto / ai-auto-approve。
     */
    default void setMergeMode(String name, String mode) {
        // 默认空实现，向后兼容
    }

    /**
     * 读取会话的工作树合并模式，未设置时返回 {@code "manual"}。
     */
    default String getMergeMode(String name) {
        return "manual";
    }

    /**
     * 追加一条每日用量记录到全局日志文件 {@code ~/.loopra/usage_daily.jsonl}。
     * <p>
     * 记录格式为一行 JSON：
     * {@code {"ts":<epochMs>,"model":"...","prompt":N,"completion":N,"cacheHit":N,"cacheMiss":N}}
     * </p>
     *
     * @param model      模型名称
     * @param prompt     输入 token 数
     * @param completion 输出 token 数
     * @param cacheHit   缓存命中 token 数
     * @param cacheMiss  缓存未命中 token 数
     */
    default void appendDailyUsage(String model, int prompt, int completion,
                                  int cacheHit, int cacheMiss) {
        // 默认空实现，向后兼容
    }

    /**
     * 关闭存储，释放其持有的资源（如后台线程、定时器、文件句柄）。
     * <p>
     * 调用后不应再使用该 store 实例。默认空实现，
     * 无资源需要释放的存储（如纯同步实现）无需覆盖。
     * </p>
     */
    default void shutdown() {
        // 默认空实现，向后兼容
    }

    /**
     * 会话元信息。
     *
     * @param worktreeMode 是否开启工作树隔离模式
     */
    record SessionInfo(String name, long size, long messageCount, long mtime, String title, boolean worktreeMode) {
        /** 兼容旧调用方的五参构造：默认非工作树模式。 */
        public SessionInfo(String name, long size, long messageCount, long mtime, String title) {
            this(name, size, messageCount, mtime, title, false);
        }
    }
}
