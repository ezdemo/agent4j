package site.sorghum.agent4j.bin.agent.context;

import lombok.extern.slf4j.Slf4j;
import org.noear.snack4.ONode;
import site.sorghum.agent4j.bin.agent.model.ChatMessage;
import site.sorghum.agent4j.bin.agent.model.ToolCallEntry;
import site.sorghum.agent4j.bin.model.ModelClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 上下文折叠 —— 将旧消息语义摘要，替代机械截断。
 * <p>
 * 参考 Agent4j TS 的 ContextManager.fold()：
 * 保留尾部（~80KB），头部用一次 API 调用压缩为一句总结。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
public class ContextFolding {

    private static final int HEAD_CHARS_LIMIT = 60_000;

    /**
     * 对消息列表执行折叠。如果消息总字符未超阈值则不操作。
     *
     * @param messages  当前全部消息（含 system prompt）
     * @param maxChars  触发折叠的阈值
     * @param keepChars 保留的尾部字符预算
     * @param client    API 客户端
     * @return 折叠后的消息列表，未触发折叠时返回原列表
     */
    public static List<ChatMessage> fold(
            List<ChatMessage> messages,
            int maxChars, int keepChars,
            ModelClient client) throws IOException {
        // 快速检查阈值，未超时直接返回原列表避免转换开销
        int total = estimateChars(messages);
        if (total <= maxChars) return messages;
        // 转换为 Map 进行内部处理
        List<Map<String, Object>> mapMessages = toMapList(messages);
        List<Map<String, Object>> result = foldInternal(mapMessages, maxChars, keepChars, client);
        return fromMapList(result);
    }

    private static List<Map<String, Object>> foldInternal(
            List<Map<String, Object>> messages,
            int maxChars, int keepChars,
            ModelClient client) throws IOException {

        int total = estimateCharsMap(messages);
        if (total <= maxChars) return messages;

        // 找到折叠边界：从尾部往前累积，留够 keepChars
        // 确保边界不切在 tool_calls/tool 对中间
        int cum = 0;
        int boundary = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            int sz = estimateCharsMap(messages.get(i));
            if (cum + sz > keepChars) break;
            cum += sz;
            boundary = i;
        }

        // 向后调整边界，避免切在 tool_calls/tool 对中间
        boundary = adjustBoundary(messages, boundary);

        if (boundary <= 0 || boundary >= messages.size()) return messages;

        List<Map<String, Object>> head = new ArrayList<>(messages.subList(0, boundary));
        List<Map<String, Object>> tail = messages.subList(boundary, messages.size());

        // 再次确保 tail 没有孤立的 tool 消息
        tail = ensureTailClean(tail);

        int dropped = head.size();
        if (dropped == 0) return messages;

        String summary = summarize(head, client);
        if (summary == null || summary.trim().isEmpty()) {
            log.warn("[fold] 摘要失败，跳过折叠");
            return messages;
        }

        Map<String, Object> summaryMsg = new LinkedHashMap<>();
        summaryMsg.put("role", "user");
        summaryMsg.put("content", "[历史上下文折叠——以下 " + dropped + " 条较早消息已被摘要]\n" + summary.trim());

        List<Map<String, Object>> result = new ArrayList<>();
        result.add(summaryMsg);
        result.addAll(tail);

        int after = estimateCharsMap(result);
        log.info("[fold] {} 条消息 → {} 字符摘要（{} → ~{} 字符）", dropped, summary.length(), total, after);
        return result;
    }

    /**
     * 调整折叠边界，确保不切在 tool_calls 与 tool 消息之间。
     * 如果 boundary 指向 tool 消息，向前退到对应的 assistant（含 tool_calls）。
     */
    private static int adjustBoundary(List<Map<String, Object>> messages, int boundary) {
        if (boundary >= messages.size()) return messages.size() - 1;
        for (int i = boundary; i < messages.size(); i++) {
            Map<String, Object> m = messages.get(i);
            if ("tool".equals(m.get("role"))) {
                // boundary 切在 tool 开头 → 向前回退到最近的 assistant
                int back = i;
                while (back > 0) {
                    back--;
                    Map<String, Object> prev = messages.get(back);
                    if ("assistant".equals(prev.get("role")) && prev.containsKey("tool_calls")) {
                        return back; // 从 assistant 开始保留完整对
                    }
                }
            }
        }
        return boundary;
    }

    /**
     * 确保 tail 没有孤立的 tool_calls/tool 对。
     * 如果 assistant 的 tool_calls 数 > 后续 tool 消息数，则从 assistant 中去掉 tool_calls。
     * 工具结果（tool）没有配对时直接丢弃。
     */
    private static List<Map<String, Object>> ensureTailClean(List<Map<String, Object>> tail) {
        List<Map<String, Object>> clean = new ArrayList<>();
        // 当前未配对的 tool_calls 数量
        int pendingToolCount = 0;
        int lastAssistantWithTcIdx = -1; // 跟踪最后一个带 tool_calls 的 assistant 在 clean 中的位置
        for (Map<String, Object> m : tail) {
            String role = (String) m.get("role");
            if ("assistant".equals(role) && m.containsKey("tool_calls")) {
                List<?> tcs = (List<?>) m.get("tool_calls");
                int tcCount = tcs != null ? tcs.size() : 0;
                pendingToolCount += tcCount; // 累加而非覆盖
                lastAssistantWithTcIdx = clean.size(); // 记录即将写入的位置
                clean.add(m);
            } else if ("tool".equals(role) && pendingToolCount > 0) {
                clean.add(m);
                pendingToolCount--;
            } else if ("tool".equals(role)) {
                continue; // 孤立 tool，丢弃
            } else {
                clean.add(m);
            }
        }
        // 如果 tail 末尾还有未配对的 tool_calls，剥离 tool_calls 并删除孤儿 tool 结果
        if (pendingToolCount > 0) {
            // 剥离最后一个 assistant 的 tool_calls
            Map<String, Object> m = clean.get(lastAssistantWithTcIdx);
            Map<String, Object> stripped = new LinkedHashMap<>();
            stripped.put("role", "assistant");
            if (m.containsKey("content")) stripped.put("content", m.get("content"));
            if (m.containsKey("reasoning_content")) stripped.put("reasoning_content", m.get("reasoning_content"));
            clean.set(lastAssistantWithTcIdx, stripped);
            // 同步删除该 assistant 之后所有孤儿 tool 消息
            for (int i = clean.size() - 1; i > lastAssistantWithTcIdx; i--) {
                if ("tool".equals(clean.get(i).get("role"))) {
                    clean.remove(i);
                }
            }
        }
        return clean;
    }

    /**
     * 对 head 消息执行语义摘要，通过一次 API 调用将旧消息压缩为一段中文描述。
     * 截断后清理 tool_calls/tool 对，避免摘要器看到工具调用细节。
     *
     * @param head   要摘要的消息子集
     * @param client API 客户端
     * @return 摘要文本，失败时返回 null
     */
    private static String summarize(List<Map<String, Object>> head, ModelClient client) throws IOException {
        String sp = """
                你是一个编码助手的对话历史摘要器。\
                输出一段简洁的中文段落，包含以下内容：
                - 用户的整体目标
                - 已完成的决策和结论
                - 已检查或修改的文件
                - 仍相关的工具结果
                - 未完成的待办
                不要逐轮记录，不要 markdown 标题，纯中文段落。""";

        // 先截断到字符限制
        List<Map<String, Object>> trimmed = truncateForSummary(head);
        // 截断后再清理 tool_calls/tool 对（顺序重要：先截断后清理）
        trimmed = sanitizeMessagesForSummary(trimmed);

        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ChatMessage.ofSystem(sp));
        for (Map<String, Object> m : trimmed) {
            msgs.add(ChatMessage.fromMap(m));
        }
        msgs.add(ChatMessage.ofUser("请用一段中文总结上面的对话。这段摘要将替代原始对话以释放上下文。"));

        ONode resp = client.chat(msgs, null);
        String content = resp.get("content").getString();
        if (content != null && !content.isEmpty()) return content;

        String reasoning = resp.get("reasoning_content").getString();
        if (reasoning != null && !reasoning.isEmpty()) return reasoning;

        return null;
    }

    /**
     * 清理消息列表用于摘要 API：
     * - 保留 tool_calls 结构（维持 API 所要求的配对约束）
     * - 移除孤立的 tool role 消息（无对应 tool_calls 的）
     */
    private static List<Map<String, Object>> sanitizeMessagesForSummary(List<Map<String, Object>> msgs) {
        List<Map<String, Object>> result = new ArrayList<>();
        int pendingToolCount = 0;
        for (Map<String, Object> m : msgs) {
            String role = (String) m.get("role");
            if ("assistant".equals(role) && m.containsKey("tool_calls")) {
                List<?> tcs = (List<?>) m.get("tool_calls");
                pendingToolCount += tcs != null ? tcs.size() : 0;
                result.add(m);
            } else if ("tool".equals(role) && pendingToolCount > 0) {
                result.add(m);
                pendingToolCount--;
            } else if ("tool".equals(role)) {
                continue; // 孤立 tool，丢弃
            } else {
                result.add(m);
            }
        }
        return result;
    }

    /**
     * 截断消息列表到 HEAD_CHARS_LIMIT 字符限制，避免摘要 API 的 token 消耗过大。
     */
    private static List<Map<String, Object>> truncateForSummary(List<Map<String, Object>> head) {
        List<Map<String, Object>> result = new ArrayList<>();
        int cum = 0;
        for (Map<String, Object> m : head) {
            int sz = estimateCharsMap(m);
            if (cum + sz > HEAD_CHARS_LIMIT) {
                // 截断当前消息的 content 部分
                Map<String, Object> truncated = new LinkedHashMap<>(m);
                String content = (String) m.get("content");
                if (content != null) {
                    int remaining = HEAD_CHARS_LIMIT - cum;
                    if (remaining > 10) {
                        truncated.put("content", content.substring(0, remaining) + "...[截断]");
                        result.add(truncated);
                    }
                }
                break;
            }
            result.add(m);
            cum += sz;
        }
        return result;
    }

    // ==================== 字符估算工具 ====================

    /**
     * 估算消息列表的总字符数（用于触发折叠的快速判断）。
     */
    public static int estimateChars(List<ChatMessage> messages) {
        int total = 0;
        for (ChatMessage m : messages) {
            total += estimateMsgChars(m);
        }
        return total;
    }

    private static int estimateMsgChars(ChatMessage msg) {
        int c = 0;
        if (msg.getContent() != null) c += msg.getContent().length();
        if (msg.getReasoningContent() != null) c += msg.getReasoningContent().length();
        if (msg.hasToolCalls()) {
            for (ToolCallEntry tc : msg.getToolCalls()) {
                c += tc.name().length();
                if (tc.arguments() != null) c += tc.arguments().toString().length();
            }
        }
        if (msg.getToolCallId() != null) c += msg.getToolCallId().length();
        return c;
    }

    static int estimateCharsMap(List<Map<String, Object>> messages) {
        int total = 0;
        for (Map<String, Object> m : messages) {
            total += estimateCharsMap(m);
        }
        return total;
    }

    private static int estimateCharsMap(Map<String, Object> m) {
        int c = 0;
        Object content = m.get("content");
        if (content instanceof String s) c += s.length();
        if (content instanceof List) c += 200; // 多模态粗略估算
        Object reasoning = m.get("reasoning_content");
        if (reasoning instanceof String s) c += s.length();
        Object tcs = m.get("tool_calls");
        if (tcs instanceof List) c += ((List<?>) tcs).size() * 50;
        Object tcId = m.get("tool_call_id");
        if (tcId instanceof String s) c += s.length();
        return c;
    }

    // ==================== 格式转换工具 ====================

    private static List<Map<String, Object>> toMapList(List<ChatMessage> messages) {
        List<Map<String, Object>> list = new ArrayList<>(messages.size());
        for (ChatMessage m : messages) {
            list.add(m.toMap());
        }
        return list;
    }

    private static List<ChatMessage> fromMapList(List<Map<String, Object>> mapList) {
        List<ChatMessage> list = new ArrayList<>(mapList.size());
        for (Map<String, Object> m : mapList) {
            list.add(ChatMessage.fromMap(m));
        }
        return list;
    }

    /**
     * 保留最后 N 条消息，折叠之前的消息。
     * 用于 /compact 命令的手动折叠。
     *
     * @param messages  当前全部消息
     * @param keepCount 保留的尾部消息条数
     * @param client    API 客户端
     * @return 折叠后的消息列表
     */
    public static List<ChatMessage> foldKeepLast(List<ChatMessage> messages, int keepCount, ModelClient client) throws IOException {
        if (messages.size() <= keepCount) return messages;
        List<Map<String, Object>> mapMessages = toMapList(messages);
        List<Map<String, Object>> head = new ArrayList<>(mapMessages.subList(0, mapMessages.size() - keepCount));
        List<Map<String, Object>> tail = mapMessages.subList(mapMessages.size() - keepCount, mapMessages.size());

        String summary = summarize(head, client);
        if (summary == null || summary.trim().isEmpty()) {
            log.warn("[foldKeepLast] 摘要失败，跳过折叠");
            return messages;
        }

        Map<String, Object> summaryMsg = new LinkedHashMap<>();
        summaryMsg.put("role", "user");
        summaryMsg.put("content", "[历史上下文折叠——以下 " + head.size() + " 条较早消息已被摘要]\n" + summary.trim());

        List<Map<String, Object>> result = new ArrayList<>();
        result.add(summaryMsg);
        result.addAll(tail);

        return fromMapList(result);
    }
}
