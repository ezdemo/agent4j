package site.sorghum.loopra.bin.session;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import site.sorghum.loopra.bin.agent.context.ConversationContext;
import site.sorghum.loopra.bin.agent.context.MessageHealer;
import site.sorghum.loopra.bin.agent.model.ChatMessage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话管理服务 —— 编排会话生命周期。
 * <p>
 * 职责：创建/切换/加载会话、注入历史到上下文、token 用量追踪持久化。
 * 从 LoopraAgent 中抽出，遵循单一职责原则。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
public class SessionService {

    private final ConversationContext ctx;
    /**
     * 按模型分别累计的 token 用量：model -> [prompt, completion, cacheHit, cacheMiss]
     */
    private final Map<String, long[]> modelUsage = new LinkedHashMap<>();
    /**
     *  获取底层 SessionStore
     */
    @Getter
    private SessionStore store;
    private long sessionPromptTokens;
    private long sessionCompletionTokens;
    private long sessionCacheHitTokens;
    private long sessionCacheMissTokens;
    /**
     * 最近一次 API 返回的 prompt_tokens（用于上下文使用率计算）
     */
    private long sessionLastPromptTokens;
    /**
     * 是否已生成会话标题
     */
    @Setter
    @Getter
    private boolean titleGenerated = false;

    /**
     * 创建支持工作区隔离的 SessionService
     *
     * @param ctx         会话上下文
     * @param sessionsDir 会话目录路径
     */
    public SessionService(ConversationContext ctx, Path sessionsDir) throws IOException {
        this.ctx = ctx;
        this.store = new JsonlSessionStore(sessionsDir);
        ctx.setSessionStore(store);
    }

    /**
     * 加载指定会话名。
     * <p>
     * 当传入 sessionName 时，切换到该会话并加载历史。
     * 当 sessionName 为空时，不自动加载任何历史会话 ——
     * 保持空白状态，由用户在前端主动选择会话。
     * </p>
     */
    public void loadOrCreate(String sessionName) throws IOException {
        if (sessionName != null && !sessionName.isEmpty()) {
            if (!store.bindTo(sessionName)) {
                log.warn("[session] 切换到指定会话失败: {}，使用新会话", sessionName);
            }
            // 仅在明确指定会话时才加载历史和恢复用量
            List<ChatMessage> loaded = store.load();
            var healResult = MessageHealer.heal(loaded);
            loaded = healResult.messages();
            for (ChatMessage m : loaded) {
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
        // sessionName 为空时：保持 store 当前状态（新建的空白会话），不加载历史
    }

    /**
     * 保存当前会话 token 用量
     */
    public void saveUsage() {
        String name = store.currentName();
        if (name == null) return; // 尚未选择会话，无需保存
        try {
            store.saveUsage(name, sessionPromptTokens,
                    sessionCompletionTokens, sessionCacheHitTokens, sessionCacheMissTokens,
                    sessionLastPromptTokens);
            // 保存按模型分别累计的用量
            store.saveModelUsage(name, modelUsage);
        } catch (IOException ignored) {
        }
    }

    /**
     * 累计 token 用量（兼容旧接口，不区分模型）
     */
    public void addUsage(int prompt, int completion, int cacheHit, int cacheMiss) {
        this.sessionPromptTokens += prompt;
        this.sessionCompletionTokens += completion;
        this.sessionCacheHitTokens += cacheHit;
        this.sessionCacheMissTokens += cacheMiss;
    }

    /**
     * 按模型累计 token 用量（同时更新总量）
     */
    public void addUsage(String model, int prompt, int completion, int cacheHit, int cacheMiss) {
        // 更新总量（向后兼容）
        addUsage(prompt, completion, cacheHit, cacheMiss);
        // 按模型分别累计
        String key = model != null ? model : "unknown";
        long[] mu = modelUsage.computeIfAbsent(key, k -> new long[4]);
        mu[0] += prompt;
        mu[1] += completion;
        mu[2] += cacheHit;
        mu[3] += cacheMiss;
        // 追加到每日用量日志
        store.appendDailyUsage(model, prompt, completion, cacheHit, cacheMiss);
    }

    /**
     * 获取按模型分别累计的 token 用量快照
     */
    public Map<String, long[]> getModelUsage() {
        Map<String, long[]> copy = new LinkedHashMap<>();
        for (Map.Entry<String, long[]> e : modelUsage.entrySet()) {
            copy.put(e.getKey(), e.getValue().clone());
        }
        return copy;
    }

    /**
     * 获取会话累计 token 用量
     */
    public long[] getUsage() {
        return new long[]{sessionPromptTokens, sessionCompletionTokens,
                sessionCacheHitTokens, sessionCacheMissTokens, sessionLastPromptTokens};
    }

    /**
     * 恢复 token 用量
     */
    public void restoreUsage(String name) {
        long[] u = store.loadUsage(name);
        sessionPromptTokens = u[0];
        sessionCompletionTokens = u[1];
        sessionCacheHitTokens = u[2];
        sessionCacheMissTokens = u[3];
        sessionLastPromptTokens = u.length > 4 ? u[4] : 0;
        // 恢复按模型用量
        modelUsage.clear();
        modelUsage.putAll(store.loadModelUsage(name));
    }

    /**
     * 更新 lastPromptTokens（上下文使用量）
     */
    public void updateLastPromptTokens(int lastPromptTokens) {
        this.sessionLastPromptTokens = lastPromptTokens;
    }

    /**
     * 重置 token 累计
     */
    public void resetUsage() {
        sessionPromptTokens = sessionCompletionTokens = 0;
        sessionCacheHitTokens = sessionCacheMissTokens = 0;
        sessionLastPromptTokens = 0;
        modelUsage.clear();
    }

    /**
     * 刷入缓冲区数据到磁盘。
     * 委托给底层 SessionStore.flush()，确保消息已持久化。
     */
    public void flush() {
        store.flush();
    }

    /**
     * 注入单条历史消息
     */
    public void injectHistory(ChatMessage msg) {
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
     * 确保当前会话已分配名称。
     * <p>
     * 新建会话时 {@code currentName} 为 null（延迟到首次 append 才分配），
     * 但标题生成等操作需要会话名才能写入 .meta 文件。
     * 此方法在 currentName 为 null 时主动分配一个新名称。
     * </p>
     */
    public void ensureSessionName() {
        if (store.currentName() == null) {
            String newName = store.newSessionName();
            store.bindTo(newName);
        }
    }

    /**
     * 更新当前会话的标题。
     *
     * @param title 会话标题
     */
    public void updateCurrentSessionTitle(String title) {
        String name = store.currentName();
        if (name == null) return; // 尚未选择会话
        try {
            store.updateTitle(name, title);
        } catch (IOException e) {
            log.error("[session] 更新会话标题失败: {}", e.getMessage());
        }
    }
}
