package site.sorghum.agent4j.bin.session;

import site.sorghum.agent4j.bin.agent.ConversationContext;
import site.sorghum.agent4j.bin.agent.MessageHealer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 会话管理服务 —— 编排会话生命周期。
 * <p>
 * 职责：创建/切换/加载会话、注入历史到上下文、token 用量追踪持久化。
 * 从 Agent4jAgent 中抽出，遵循单一职责原则。
 * </p>
 *
 * @author Sorghum
 */
public class SessionService {

    private final ConversationContext ctx;
    private SessionStore store;
    private long sessionPromptTokens;
    private long sessionCompletionTokens;
    private long sessionCacheHitTokens;
    private long sessionCacheMissTokens;
    /** 是否已生成会话标题 */
    private boolean titleGenerated = false;

    /** 检查是否已生成会话标题 */
    public boolean isTitleGenerated() {
        return titleGenerated;
    }

    /** 设置标题已生成标志 */
    public void setTitleGenerated(boolean generated) {
        this.titleGenerated = generated;
    }

    public SessionService(ConversationContext ctx, SessionStore store) {
        this.ctx = ctx;
        this.store = store;
        ctx.setSessionStore(store);
    }

    /**
     * 创建支持工作区隔离的 SessionService
     *
     * @param ctx 会话上下文
     * @param sessionsDir 会话目录路径
     */
    public SessionService(ConversationContext ctx, Path sessionsDir) throws IOException {
        this.ctx = ctx;
        this.store = new JsonlSessionStore(sessionsDir);
        ctx.setSessionStore(store);
    }

    /** 加载指定会话名，或自动选用最近活跃会话 */
    public void loadOrCreate(String sessionName) throws IOException {
        if (sessionName != null && !sessionName.isEmpty()) {
            if (!store.switchTo(sessionName)) {
                System.err.println("[session] 切换到指定会话失败: " + sessionName + "，使用新会话");
            }
        } else {
            List<SessionStore.SessionInfo> sessions = store.list();
            if (!sessions.isEmpty()) {
                SessionStore.SessionInfo latest = sessions.get(0);
                if (store.switchTo(latest.name)) {
                    System.err.println("[session] 自动加载最近会话: " + latest.name
                            + " (" + latest.messageCount + " 条消息)");
                } else {
                    System.err.println("[session] 自动加载失败: " + latest.name + "，使用新会话");
                }
            }
        }
        List<Map<String, Object>> loaded = store.load();
        loaded = MessageHealer.heal(loaded, false);
        for (Map<String, Object> m : loaded) {
            ctx.injectHistory(m);
        }
        restoreUsage(store.currentName());
        
        // 检查是否已有标题
        try {
            String currentName = store.currentName();
            String existingTitle = store.getTitle(currentName);
            titleGenerated = (existingTitle != null && !existingTitle.isEmpty());
        } catch (Exception e) {
            titleGenerated = false;
        }
    }

    /** 新建会话：保存当前、关闭旧 store、创建新会话 */
    public void newSession() throws IOException {
        saveUsage();
        // 关闭旧的 store，释放定时器 + writer 资源
        if (store instanceof JsonlSessionStore) {
            ((JsonlSessionStore) store).shutdown();
        }
        store = new JsonlSessionStore();
        ctx.setSessionStore(store);
        ctx.clearHistory();  // 仅清空内存历史，不重写旧会话文件
        resetUsage();
        titleGenerated = false; // 新会话，标题未生成
    }

    /** 保存当前会话 token 用量 */
    public void saveUsage() {
        try {
            store.saveUsage(store.currentName(), sessionPromptTokens,
                    sessionCompletionTokens, sessionCacheHitTokens, sessionCacheMissTokens);
        } catch (IOException ignored) {}
    }

    /** 累计 token 用量 */
    public void addUsage(int prompt, int completion, int cacheHit, int cacheMiss) {
        this.sessionPromptTokens += prompt;
        this.sessionCompletionTokens += completion;
        this.sessionCacheHitTokens += cacheHit;
        this.sessionCacheMissTokens += cacheMiss;
    }

    /** 获取会话累计 token 用量 */
    public long[] getUsage() {
        return new long[]{sessionPromptTokens, sessionCompletionTokens,
                sessionCacheHitTokens, sessionCacheMissTokens};
    }

    /** 恢复 token 用量 */
    public void restoreUsage(String name) {
        long[] u = store.loadUsage(name);
        sessionPromptTokens = u[0];
        sessionCompletionTokens = u[1];
        sessionCacheHitTokens = u[2];
        sessionCacheMissTokens = u[3];
    }

    /** 重置 token 累计 */
    public void resetUsage() {
        sessionPromptTokens = sessionCompletionTokens = 0;
        sessionCacheHitTokens = sessionCacheMissTokens = 0;
    }

    /**
     * 刷入缓冲区数据到磁盘。
     * 委托给底层 SessionStore.flush()，确保消息已持久化。
     */
    public void flush() {
        try {
            store.flush();
        } catch (IOException ignored) {}
    }

    /** 获取底层 SessionStore */
    public SessionStore getStore() {
        return store;
    }

    /** 注入单条历史消息 */
    public void injectHistory(Map<String, Object> msg) {
        ctx.injectHistory(msg);
    }

    /**
     * 生成会话标题。
     * 根据用户第一条消息内容生成简短标题。
     *
     * @param userMessage 用户消息内容
     * @return 生成的标题
     */
    public String generateSessionTitle(String userMessage) {
        if (userMessage == null || userMessage.isEmpty()) {
            return "新会话";
        }
        // 移除换行符，截取前30个字符
        String title = userMessage.replaceAll("\\s+", " ").trim();
        if (title.length() > 30) {
            title = title.substring(0, 30) + "...";
        }
        return title;
    }

    /**
     * 更新当前会话的标题。
     *
     * @param title 会话标题
     */
    public void updateCurrentSessionTitle(String title) {
        try {
            store.updateTitle(store.currentName(), title);
        } catch (IOException e) {
            System.err.println("[session] 更新会话标题失败: " + e.getMessage());
        }
    }
}
