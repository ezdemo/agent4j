package site.sorghum.agent4j.bin.agent;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import site.sorghum.agent4j.bin.session.SessionStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话上下文 —— 在内存中管理消息历史。
 * <p>
 * 消息按角色累积：
 * <ul>
 *   <li>{@code user} — 用户输入</li>
 *   <li>{@code assistant} — 模型回复（可能含 tool_calls、reasoning_content）</li>
 *   <li>{@code tool} — 工具执行结果</li>
 * </ul>
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
public class ConversationContext {

    private final List<ChatMessage> history = new ArrayList<>();
    private final PromptPrefix prefix;
    /**
     * 持久化存储（可选）
     * -- SETTER --
     *  绑定会话存储，所有后续消息将通过此存储持久化。
     *  传入 null 可解除绑定（停止持久化）。
     *
     */
    @Setter
    @Getter
    private SessionStore sessionStore = null;

    public ConversationContext(PromptPrefix prefix) {
        this.prefix = prefix;
    }

    // ---- 写入 ----

    /**
     * 添加用户消息（支持纯文本和多模态）。
     *
     * @param msg 用户消息对象（纯文本或文本+图片）
     */
    public void addUser(UserMessage msg) {
        ChatMessage chatMsg;
        if (msg != null && msg.hasImages()) {
            chatMsg = ChatMessage.userWithImages(msg.getText(), msg.getImages());
        } else {
            String text = msg != null ? msg.getText() : null;
            chatMsg = ChatMessage.user(text);
        }
        history.add(chatMsg);
        persist(chatMsg);
    }

    /**
     * 添加纯文本用户消息（便捷方法，内部调用 {@link #addUser(UserMessage)}）。
     */
    public void addUser(String content) {
        addUser(UserMessage.of(content));
    }

    /**
     * 添加系统消息到上下文（用于系统通知、目标恢复提醒等）。
     * 消息角色为 "system"，将被包含在历史中发送给模型。
     *
     * @param content 系统消息内容
     */
    public void addSystemMessage(String content) {
        ChatMessage msg = ChatMessage.system(content);
        history.add(msg);
        persist(msg);
    }

    public void addAssistant(String content, List<ToolCallEntry> toolCalls, String reasoningContent) {
        // 防御：assistant 消息必须至少包含 content、tool_calls 或 reasoning_content 之一
        boolean hasContent = content != null && !content.isEmpty();
        boolean hasToolCalls = toolCalls != null && !toolCalls.isEmpty();
        boolean hasReasoning = reasoningContent != null && !reasoningContent.isEmpty();
        if (!hasContent && !hasToolCalls && !hasReasoning) {
            content = "";
        } else if (!hasContent && !hasToolCalls && hasReasoning) {
            // 只有 reasoning_content 没有 content/tool_calls → 保持 content 为 null，
            // 让 toMap() 不输出 content 字段，避免 "content":"" 导致 API 400
            content = null;
        }
        ChatMessage msg = ChatMessage.assistant(content, toolCalls, reasoningContent);
        history.add(msg);
        persist(msg);
    }

    public void addToolResult(String toolCallId, String result) {
        ChatMessage msg = ChatMessage.tool(toolCallId, result);
        history.add(msg);
        persist(msg);
    }

    /**
     * 将消息追加写入 JSONL 文件。
     * 如果 sessionStore 未设置或写入失败，静默忽略。
     */
    private void persist(ChatMessage msg) {
        if (sessionStore != null) {
            try {
                sessionStore.append(msg);
            } catch (IOException e) {
                log.error("[session] jsonl 写入失败: {}", e.getMessage());
            }
        }
    }

    // ---- 读取 ----

    /**
     * 构建发给 API 的消息列表 = prefix（system msg） + history。
     * prefix 始终保持不变 → DeepSeek 前缀缓存命中。
     */
    public List<ChatMessage> buildMessages() {
        List<ChatMessage> msgs = prefix.toMessages();
        msgs.addAll(history);
        return msgs;
    }

    /**
     * 加载历史会话时注入消息到上下文，不触发持久化。
     * 用于 /load 命令从 JSONL 文件恢复会话。
     */
    public void injectHistory(ChatMessage msg) {
        history.add(msg);
    }

    public int size() {
        return history.size();
    }

    /**
     * 清除所有历史消息（新建会话时用）。
     * 同时触发持久化回写。
     */
    public void clear() {
        history.clear();
        rewriteStore();
    }

    /**
     * 仅清除内存中的历史消息，不触发持久化回写。
     * 用于创建新会话时清空上下文，但保留旧会话内容。
     */
    public void clearHistory() {
        history.clear();
    }

    /**
     * 撤回最后一条用户消息及其后的所有消息，返回被撤回的用户消息内容。
     * 用于 /retry 命令。同时同步持久化。
     */
    public String retryLastUser() {
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).isUser()) {
                String text = history.get(i).getContent();
                history.subList(i, history.size()).clear();
                // 同步持久化：回写文件以移除被撤回的消息
                rewriteStore();
                return text;
            }
        }
        return null;
    }

    /**
     * 回退到第 N 条用户消息，移除之后的所有消息。
     * 用于 /rewind N 命令。同时同步持久化。
     */
    public String rewindToUser(int userIndex) {
        int count = 0;
        for (int i = 0; i < history.size(); i++) {
            if (history.get(i).isUser()) {
                if (count == userIndex) {
                    String text = history.get(i).getContent();
                    history.subList(i, history.size()).clear();
                    // 同步持久化：回写文件以移除被回退的消息
                    rewriteStore();
                    return text;
                }
                count++;
            }
        }
        return null;
    }

    /**
     * 将当前历史持久化回写到 session store
     */
    private void rewriteStore() {
        if (sessionStore != null) {
            try {
                sessionStore.rewrite(history);
            } catch (IOException e) {
                log.error("[session] rewrite 失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 折叠历史：用折叠后的消息列表替换当前历史。
     * 由 AgentLoop 的预检调用，将旧消息替换为摘要。
     */
    public void compact(List<ChatMessage> foldedMessages) {
        history.clear();
        history.addAll(foldedMessages);
        // 持久化回写
        rewriteStore();
    }

    /**
     * 获取完整历史消息列表的副本（用于调试）。
     */
    public List<ChatMessage> getHistory() {
        return new ArrayList<>(history);
    }

    /**
     * 获取最后一条 assistant 消息的 content（用于用户中断时返回已生成内容）。
     *
     * @return 最后一条 assistant 消息的 content，如果没有则返回 null
     */
    public String getLastAssistantContent() {
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage msg = history.get(i);
            if (msg.isAssistant()) {
                String content = msg.getContent();
                if (content != null && !content.isEmpty()) {
                    return content;
                }
            }
        }
        return null;
    }
}
