package site.sorghum.agent4j.bin.session;

import site.sorghum.agent4j.bin.agent.ChatMessage;

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
    boolean switchTo(String name);

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
     * 刷入缓冲区数据到磁盘。
     * 调用后确保所有已追加的消息被持久化。
     */
    void flush() throws IOException;

    /**
     * 保存 token 用量
     */
    void saveUsage(String name, long prompt, long completion, long cacheHit, long cacheMiss) throws IOException;

    /**
     * 保存 token 用量（包含 lastPromptTokens）
     */
    void saveUsage(String name, long prompt, long completion, long cacheHit, long cacheMiss, long lastPromptTokens) throws IOException;

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
    String getTitle(String name) throws IOException;

    /**
     * 会话元信息。
     */
    record SessionInfo(String name, long size, long messageCount, long mtime, String title) {
    }
}
